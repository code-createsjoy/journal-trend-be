package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperTopic;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.integration.semanticscholar.SemanticScholarClient;
import com.norman.swp391.mapper.SyncLogMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperTopicRepository;
import com.norman.swp391.repository.SyncLogRepository;
import com.norman.swp391.repository.TopicRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.service.ApiSourceService;
import com.norman.swp391.service.JournalService;
import com.norman.swp391.service.NotificationService;
import com.norman.swp391.service.PaperReviewService;
import com.norman.swp391.service.PaperSyncService;
import com.norman.swp391.service.TopicTrendService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Đồng bộ metadata bài báo từ OpenAlex (và làm giàu tùy chọn).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperSyncServiceImpl implements PaperSyncService {

    private final AppProperties appProperties;
    private final OpenAlexClient openAlexClient;
    private final SemanticScholarClient semanticScholarClient;
    private final PaperRepository paperRepository;
    private final TopicRepository topicRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final SyncLogRepository syncLogRepository;
    private final UserRepository userRepository;
    private final ApiSourceService apiSourceService;
    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final NotificationService notificationService;
    private final TopicTrendService topicTrendService;
    private final PaperReviewService paperReviewService;
    private final TransactionTemplate transactionTemplate;

    @Override
/**
 * Xử lý nghiệp vụ: startSync.
 */
    public SyncLogResponse startSync(Long adminId) {
        expireStaleRunningSyncs();
        SyncLog running = findRunningWithAdmin();
        if (running != null) {
            return SyncLogMapper.toResponse(running);
        }

        User admin = null;
        if (adminId != null) {
            admin = userRepository.findById(adminId).orElse(null);
        }

        SyncLog sync = syncLogRepository.save(SyncLog.builder()
                .startedAt(LocalDateTime.now())
                .status(SyncStatus.RUNNING)
                .papersFetched(0)
                .triggeredByAdmin(admin)
                .build());

        SyncLogResponse response = SyncLogMapper.toResponse(sync);
        Long syncId = sync.getId();
        Thread.startVirtualThread(() -> executeSync(syncId));
        return response;
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: resetStaleRunningSyncs.
 */
    public void resetStaleRunningSyncs() {
        for (SyncLog sync : syncLogRepository.findByStatusWithAdmin(SyncStatus.RUNNING, PageRequest.of(0, 50))) {
            sync.setStatus(SyncStatus.FAILED);
            sync.setFinishedAt(LocalDateTime.now());
            sync.setErrorMessage("Reset by admin — start a new sync");
            syncLogRepository.save(sync);
            log.warn("Admin reset: marked sync #{} as FAILED", sync.getId());
        }
    }

    @Override
    @Transactional
/**
 * Lấy dữ liệu: getLatestSyncStatus.
 */
    public SyncLogResponse getLatestSyncStatus() {
        expireStaleRunningSyncs();
        var recent = syncLogRepository.findRecentWithAdmin(PageRequest.of(0, 1));
        if (recent.isEmpty()) {
            return null;
        }
        return SyncLogMapper.toResponse(recent.get(0));
    }

/**
 * Xử lý nghiệp vụ: expireStaleRunningSyncs.
 */
    private void expireStaleRunningSyncs() {
        int staleMinutes = Math.max(1, appProperties.getSync().getStaleSyncMinutes());
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleMinutes);
        for (SyncLog sync : syncLogRepository.findByStatusWithAdmin(SyncStatus.RUNNING, PageRequest.of(0, 20))) {
            if (sync.getStartedAt() != null && sync.getStartedAt().isBefore(cutoff)) {
                sync.setStatus(SyncStatus.FAILED);
                sync.setFinishedAt(LocalDateTime.now());
                sync.setErrorMessage("Timed out after " + staleMinutes + " minutes — start a new sync");
                syncLogRepository.save(sync);
            }
        }
    }

/**
 * Tìm kiếm: findRunningWithAdmin.
 */
    private SyncLog findRunningWithAdmin() {
        var running = syncLogRepository.findByStatusWithAdmin(SyncStatus.RUNNING, PageRequest.of(0, 1));
        return running.isEmpty() ? null : running.get(0);
    }

/**
 * Xử lý nghiệp vụ: executeSync.
 */
    private void executeSync(Long syncLogId) {
        SyncLog sync = syncLogRepository.findById(syncLogId).orElse(null);
        if (sync == null) {
            return;
        }

        int totalFetched = 0;
        Set<Long> newPaperIds = new HashSet<>();

        try {
            if (!apiSourceService.isEnabled("OpenAlex")) {
                throw new IllegalStateException("OpenAlex source is disabled in admin config");
            }

            List<String> queries = appProperties.getSync().getSearchQueries();
            if (queries == null || queries.isEmpty()) {
                queries = List.of("computer science");
            }
            int maxPages = Math.max(1, appProperties.getSync().getMaxPages());
            int maxPapers = Math.max(1, appProperties.getSync().getMaxPapersPerRun());
            String fromDate = appProperties.getSync().getFromPublicationDate();
            boolean externalOnIngest = appProperties.getSync().isExternalEnrichOnIngest();
            int ingestBatchSize = Math.max(1, appProperties.getSync().getIngestBatchSize());
            List<ExternalPaperMetadata> ingestBuffer = new ArrayList<>(ingestBatchSize);

            outer:
            for (String query : queries) {
                if (!StringUtils.hasText(query)) {
                    continue;
                }
                for (int page = 1; page <= maxPages; page++) {
                    if (totalFetched >= maxPapers) {
                        break outer;
                    }
                    List<ExternalPaperMetadata> batch;
                    try {
                        batch = openAlexClient.fetchWorks(query.trim(), page, fromDate);
                    } catch (Exception ex) {
                        log.warn("OpenAlex fetch failed for query '{}' page {}: {}", query, page, ex.getMessage());
                        continue;
                    }
                    if (batch.isEmpty()) {
                        break;
                    }
                    for (ExternalPaperMetadata metadata : batch) {
                        if (totalFetched >= maxPapers) {
                            break outer;
                        }
                        ExternalPaperMetadata processed = metadata;
                        if (externalOnIngest && StringUtils.hasText(metadata.doi())) {
                            processed = semanticScholarClient
                                    .enrichByDoi(metadata.doi())
                                    .map(enrich -> mergeMetadata(metadata, enrich))
                                    .orElse(metadata);
                        }
                        ingestBuffer.add(processed);
                        if (ingestBuffer.size() >= ingestBatchSize) {
                            totalFetched += flushIngest(ingestBuffer, newPaperIds, maxPapers - totalFetched);
                            ingestBuffer.clear();
                        }
                    }
                }
            }

            if (!ingestBuffer.isEmpty()) {
                totalFetched += flushIngest(ingestBuffer, newPaperIds, maxPapers - totalFetched);
            }

            paperReviewService.expireStalePendingReviews();
            topicTrendService.recalculateAll();
            int backfillMonths = appProperties.getSync().getTrendBackfillMonths();
            if (backfillMonths > 0) {
                topicTrendService.backfillHistoricalMonths(backfillMonths);
            }
            notificationService.notifyTrendingForFollowedTopics(topicTrendService.findTrendingTopics());
            notificationService.notifyNewPapersForSubscriptions(newPaperIds);

            sync.setStatus(SyncStatus.SUCCESS);
            sync.setPapersFetched(totalFetched);
            apiSourceService.recordSyncResult("OpenAlex", true);
            log.info("Sync #{} completed: {} papers", syncLogId, totalFetched);
        } catch (Exception ex) {
            log.error("Sync #{} failed", syncLogId, ex);
            sync.setStatus(SyncStatus.FAILED);
            sync.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            apiSourceService.recordSyncResult("OpenAlex", false);
        } finally {
            sync.setFinishedAt(LocalDateTime.now());
            if (sync.getPapersFetched() == 0 && sync.getStatus() == SyncStatus.SUCCESS) {
                sync.setPapersFetched(totalFetched);
            } else if (sync.getStatus() == SyncStatus.RUNNING) {
                sync.setPapersFetched(totalFetched);
            }
            syncLogRepository.save(sync);
        }
    }

/**
 * Xử lý nghiệp vụ: flushIngest.
 */
    private int flushIngest(List<ExternalPaperMetadata> batch, Set<Long> newPaperIds, int maxRemaining) {
        if (batch.isEmpty() || maxRemaining <= 0) {
            return 0;
        }
        List<ExternalPaperMetadata> slice = batch.size() > maxRemaining ? batch.subList(0, maxRemaining) : batch;
        Integer saved = transactionTemplate.execute(status -> {
            int count = 0;
            for (ExternalPaperMetadata meta : slice) {
                Long paperId = savePaperWithRelations(meta);
                if (paperId != null) {
                    newPaperIds.add(paperId);
                    count++;
                }
            }
            return count;
        });
        return saved != null ? saved : 0;
    }

/**
 * Xử lý nghiệp vụ: mergeMetadata.
 */
    private ExternalPaperMetadata mergeMetadata(ExternalPaperMetadata base, ExternalPaperMetadata enrich) {
        return new ExternalPaperMetadata(
                coalesce(enrich.title(), base.title()),
                coalesce(enrich.abstractText(), base.abstractText()),
                coalesce(enrich.doi(), base.doi()),
                enrich.publicationDate() != null ? enrich.publicationDate() : base.publicationDate(),
                enrich.citationCount() != null ? enrich.citationCount() : base.citationCount(),
                mergeTopics(base.topics(), enrich.topics()),
                mergeAuthors(base.authors(), enrich.authors()),
                coalesce(enrich.pdfUrl(), base.pdfUrl()),
                coalesce(enrich.landingPageUrl(), base.landingPageUrl()),
                enrich.openAccess() != null ? enrich.openAccess() : base.openAccess(),
                coalesce(enrich.journal(), base.journal()),
                coalesce(enrich.openAlexId(), base.openAlexId()),
                mergeAuthorDetails(base.authorDetails(), enrich.authorDetails()));
    }

/**
 * Xử lý nghiệp vụ: mergeAuthorDetails.
 */
    private List<ExternalAuthorInfo> mergeAuthorDetails(List<ExternalAuthorInfo> a, List<ExternalAuthorInfo> b) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExternalAuthorInfo> merged = new ArrayList<>();
        for (ExternalAuthorInfo info : a) {
            String key = StringUtils.hasText(info.openAlexId()) ? info.openAlexId() : info.name();
            if (seen.add(key)) {
                merged.add(info);
            }
        }
        for (ExternalAuthorInfo info : b) {
            String key = StringUtils.hasText(info.openAlexId()) ? info.openAlexId() : info.name();
            if (seen.add(key)) {
                merged.add(info);
            }
        }
        return merged;
    }

/**
 * Xử lý nghiệp vụ: mergeTopics.
 */
    private List<String> mergeTopics(List<String> a, List<String> b) {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(a);
        set.addAll(b);
        return new ArrayList<>(set);
    }

/**
 * Xử lý nghiệp vụ: mergeAuthors.
 */
    private List<String> mergeAuthors(List<String> a, List<String> b) {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(a);
        set.addAll(b);
        return new ArrayList<>(set);
    }

/**
 * Xử lý nghiệp vụ: coalesce.
 */
    private String coalesce(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

/**
 * Tạo hoặc lưu: savePaperWithRelations.
 */
    private Long savePaperWithRelations(ExternalPaperMetadata metadata) {
        if (!StringUtils.hasText(metadata.title())) {
            return null;
        }
        if (metadata.publicationDate() == null) {
            return null;
        }
        if (!StringUtils.hasText(metadata.doi()) && !StringUtils.hasText(metadata.openAlexId())) {
            return null;
        }
        Optional<Paper> existing = findExistingPaper(metadata);
        boolean isNew = existing.isEmpty();
        Paper paper = existing.orElseGet(this::newPaper);

        if (isNew) {
            paper.setTitle(metadata.title());
            paper.setAbstractText(metadata.abstractText());
            paper.setDoi(metadata.doi());
            if (metadata.publicationDate() != null) {
                paper.setPublicationDate(metadata.publicationDate());
            }
            if (metadata.citationCount() != null) {
                paper.setCitationCount(metadata.citationCount());
            }
            paper.setPdfUrl(metadata.pdfUrl());
            paper.setSourceUrl(metadata.landingPageUrl());
            paper.setOpenAccess(metadata.openAccess() != null ? metadata.openAccess() : paper.isOpenAccess());
            if (StringUtils.hasText(metadata.openAlexId())) {
                paper.setOpenAlexId(metadata.openAlexId());
            }
            paper.setPrimarySource("OPENALEX");
            paper.setStatus(PaperStatus.ACTIVE);
            paper.setReviewStatus(PaperReviewStatus.NONE);
            if (paper.getCreatedAt() == null) {
                paper.setCreatedAt(LocalDateTime.now());
            }
            paper = paperRepository.save(paper);
        } else {
            paper.setPrimarySource("OPENALEX");
            paper.setStatus(PaperStatus.ACTIVE);
            paperReviewService.applyIncomingMetadata(paper, metadata, "OPENALEX");
            paper = paperRepository.findById(paper.getId()).orElse(paper);
        }

        // Link topics/authors before journal (REQUIRES_NEW journal tx can clear parent session).
        linkTopics(paper, metadata.topics());
        linkAuthors(paper, metadata);

        if (StringUtils.hasText(metadata.journal())) {
            paper = paperRepository.findById(paper.getId()).orElse(paper);
            String domain = metadata.topics() != null && !metadata.topics().isEmpty()
                    ? metadata.topics().get(0)
                    : "General";
            try {
                var journalEntity = journalService.findOrCreate(metadata.journal(), null, domain);
                if (journalEntity != null && journalEntity.getId() != null) {
                    paper.setJournalRef(journalRepository.getReferenceById(journalEntity.getId()));
                    paper.setJournal(journalEntity.getName());
                    paperRepository.save(paper);
                }
            } catch (Exception ex) {
                log.debug("Journal link skipped: {}", ex.getMessage());
                paper.setJournal(metadata.journal());
                paperRepository.save(paper);
            }
        }
        return isNew ? paper.getId() : null;
    }

/**
 * Tìm kiếm: findExistingPaper.
 */
    private Optional<Paper> findExistingPaper(ExternalPaperMetadata metadata) {
        if (StringUtils.hasText(metadata.openAlexId())) {
            Optional<Paper> byOx = paperRepository.findByOpenAlexIdIgnoreCase(metadata.openAlexId());
            if (byOx.isPresent()) {
                return byOx;
            }
        }
        if (StringUtils.hasText(metadata.doi())) {
            return paperRepository.findByDoiIgnoreCase(metadata.doi());
        }
        return Optional.empty();
    }

/**
 * Xử lý nghiệp vụ: newPaper.
 */
    private Paper newPaper() {
        return Paper.builder()
                .createdAt(LocalDateTime.now())
                .status(PaperStatus.ACTIVE)
                .reviewStatus(PaperReviewStatus.NONE)
                .citationCount(0)
                .openAccess(false)
                .build();
    }

/**
 * Xử lý nghiệp vụ: linkTopics.
 */
    private void linkTopics(Paper paper, List<String> topicNames) {
        if (topicNames == null) {
            return;
        }
        for (String name : topicNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            Topic topic = topicRepository
                    .findByNameIgnoreCase(trimmed)
                    .orElseGet(() -> topicRepository.save(Topic.builder()
                            .name(trimmed)
                            .description("Imported from OpenAlex sync")
                            .build()));

            boolean exists = paperTopicRepository.findByPaperId(paper.getId()).stream()
                    .anyMatch(pt -> pt.getTopic().getId().equals(topic.getId()));
            if (!exists) {
                paperTopicRepository.save(PaperTopic.builder().paper(paper).topic(topic).build());
            }
        }
    }

/**
 * Xử lý nghiệp vụ: linkAuthors.
 */
    private void linkAuthors(Paper paper, ExternalPaperMetadata metadata) {
        if (metadata.authorDetails() != null && !metadata.authorDetails().isEmpty()) {
            for (ExternalAuthorInfo info : metadata.authorDetails()) {
                linkOneAuthor(paper, info);
            }
            return;
        }
        if (metadata.authors() == null) {
            return;
        }
        for (String name : metadata.authors()) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            linkOneAuthor(paper, new ExternalAuthorInfo(name.trim(), null, ""));
        }
    }

/**
 * Xử lý nghiệp vụ: linkOneAuthor.
 */
    private void linkOneAuthor(Paper paper, ExternalAuthorInfo info) {
        if (!StringUtils.hasText(info.name())) {
            return;
        }
        String trimmed = info.name().trim();
        String affiliation = info.affiliation() != null ? info.affiliation().trim() : "";

        Author author = null;
        if (StringUtils.hasText(info.openAlexId())) {
            author = authorRepository.findByOpenAlexIdIgnoreCase(info.openAlexId()).orElse(null);
        }
        if (author == null) {
            author = authorRepository
                    .findFirstByNameIgnoreCaseAndAffiliationIgnoreCaseOrderByIdAsc(trimmed, affiliation)
                    .orElse(null);
        }
        if (author == null) {
            author = authorRepository.save(Author.builder()
                    .name(trimmed)
                    .affiliation(affiliation)
                    .citationCount(0)
                    .build());
        } else {
            if (StringUtils.hasText(info.openAlexId())) {
                author.setOpenAlexId(info.openAlexId());
            }
            if (StringUtils.hasText(affiliation)) {
                author.setAffiliation(affiliation);
            }
            author = authorRepository.save(author);
        }

        final Author linkedAuthor = author;
        boolean exists = paperAuthorRepository.findByPaperId(paper.getId()).stream()
                .anyMatch(pa -> pa.getAuthor().getId().equals(linkedAuthor.getId()));
        if (!exists) {
            paperAuthorRepository.save(PaperAuthor.builder().paper(paper).author(linkedAuthor).build());
        }
    }
}
