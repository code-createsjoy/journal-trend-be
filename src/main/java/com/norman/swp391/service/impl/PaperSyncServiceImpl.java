package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalKeywordInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.integration.semanticscholar.SemanticScholarClient;
import com.norman.swp391.mapper.SyncLogMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.repository.SyncLogRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.service.ApiSourceService;
import com.norman.swp391.service.JournalService;
import com.norman.swp391.service.NotificationService;
import com.norman.swp391.service.PaperReviewService;
import com.norman.swp391.service.PaperSyncService;
import com.norman.swp391.service.KeywordTrendService;
import java.time.LocalDate;
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
    private final KeywordRepository keywordRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final SyncLogRepository syncLogRepository;
    private final UserRepository userRepository;
    private final ApiSourceService apiSourceService;
    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final NotificationService notificationService;
    private final KeywordTrendService keywordTrendService;
    private final PaperReviewService paperReviewService;
    private final TransactionTemplate transactionTemplate;

    @Override
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
    public SyncLogResponse getLatestSyncStatus() {
        expireStaleRunningSyncs();
        var recent = syncLogRepository.findRecentWithAdmin(PageRequest.of(0, 1));
        if (recent.isEmpty()) {
            return null;
        }
        return SyncLogMapper.toResponse(recent.get(0));
    }

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

    private SyncLog findRunningWithAdmin() {
        var running = syncLogRepository.findByStatusWithAdmin(SyncStatus.RUNNING, PageRequest.of(0, 1));
        return running.isEmpty() ? null : running.get(0);
    }

    private void executeSync(Long syncLogId) {
        SyncLog sync = syncLogRepository.findById(syncLogId).orElse(null);
        if (sync == null) {
            return;
        }

        int totalFetched = 0;
        Set<Long> newPaperIds = new HashSet<>();
        boolean openAlexEnabled = false;
        boolean semanticScholarEnabled = false;

        try {
            openAlexEnabled = apiSourceService.isEnabled("OpenAlex");
            semanticScholarEnabled = apiSourceService.isEnabled("SemanticScholar");
            if (!openAlexEnabled && !semanticScholarEnabled) {
                throw new IllegalStateException("Both OpenAlex and SemanticScholar sources are disabled in admin config");
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

            List<String> enabledSources = new ArrayList<>();
            if (openAlexEnabled) {
                enabledSources.add("OpenAlex");
            }
            if (semanticScholarEnabled) {
                enabledSources.add("SemanticScholar");
            }

            boolean semanticScholarRateLimited = false;

            outer:
            for (String query : queries) {
                if (!StringUtils.hasText(query)) {
                    continue;
                }
                for (String source : enabledSources) {
                    if ("SemanticScholar".equals(source) && semanticScholarRateLimited) {
                        continue;
                    }
                    for (int page = 1; page <= maxPages; page++) {
                        if (totalFetched >= maxPapers) {
                            break outer;
                        }
                        List<ExternalPaperMetadata> batch;
                        try {
                            if ("OpenAlex".equals(source)) {
                                batch = openAlexClient.fetchWorks(query.trim(), page, fromDate);
                            } else {
                                batch = semanticScholarClient.fetchWorks(query.trim(), page);
                            }
                        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
                            log.error("Semantic Scholar rate limit reached. Disabling Semantic Scholar for the remainder of this sync run.");
                            semanticScholarRateLimited = true;
                            break; // break the page loop for SemanticScholar
                        } catch (Exception ex) {
                            log.warn("{} fetch failed for query '{}' page {}: {}", source, query, page, ex.getMessage());
                            continue;
                        }
                        if (batch.isEmpty()) {
                            break;
                        }
                        for (ExternalPaperMetadata metadata : batch) {
                            if (totalFetched >= maxPapers) {
                                break outer;
                            }

                            // For Semantic Scholar, filter by publication date manually
                            if ("SemanticScholar".equals(source) && StringUtils.hasText(fromDate)) {
                                try {
                                    LocalDate limitDate = LocalDate.parse(fromDate);
                                    if (metadata.publicationDate() != null && metadata.publicationDate().isBefore(limitDate)) {
                                        continue;
                                    }
                                } catch (Exception ex) {
                                    // ignore date parse errors
                                }
                            }

                            ExternalPaperMetadata processed = metadata;
                            if ("OpenAlex".equals(source) && externalOnIngest && StringUtils.hasText(metadata.doi())) {
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
            }

            if (!ingestBuffer.isEmpty()) {
                totalFetched += flushIngest(ingestBuffer, newPaperIds, maxPapers - totalFetched);
            }

            paperReviewService.expireStalePendingReviews();
            keywordTrendService.recalculateAll();
            int backfillMonths = appProperties.getSync().getTrendBackfillMonths();
            if (backfillMonths > 0) {
                keywordTrendService.backfillHistoricalMonths(backfillMonths);
            }
            notificationService.notifyTrendingForFollowedKeywords(keywordTrendService.findTrendingKeywords());
            notificationService.notifyNewPapersForSubscriptions(newPaperIds);

            sync.setStatus(SyncStatus.SUCCESS);
            sync.setPapersFetched(totalFetched);
            if (openAlexEnabled) {
                apiSourceService.recordSyncResult("OpenAlex", true);
            }
            if (semanticScholarEnabled) {
                apiSourceService.recordSyncResult("SemanticScholar", true);
            }
            log.info("Sync #{} completed: {} papers", syncLogId, totalFetched);
        } catch (Exception ex) {
            log.error("Sync #{} failed", syncLogId, ex);
            sync.setStatus(SyncStatus.FAILED);
            sync.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            if (openAlexEnabled) {
                apiSourceService.recordSyncResult("OpenAlex", false);
            }
            if (semanticScholarEnabled) {
                apiSourceService.recordSyncResult("SemanticScholar", false);
            }
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

    private ExternalPaperMetadata mergeMetadata(ExternalPaperMetadata base, ExternalPaperMetadata enrich) {
        return new ExternalPaperMetadata(
                coalesce(enrich.title(), base.title()),
                coalesce(enrich.abstractText(), base.abstractText()),
                coalesce(enrich.doi(), base.doi()),
                enrich.publicationDate() != null ? enrich.publicationDate() : base.publicationDate(),
                enrich.citationCount() != null ? enrich.citationCount() : base.citationCount(),
                mergeKeywords(base.keywords(), enrich.keywords()),
                mergeAuthors(base.authors(), enrich.authors()),
                coalesce(enrich.pdfUrl(), base.pdfUrl()),
                coalesce(enrich.landingPageUrl(), base.landingPageUrl()),
                enrich.openAccess() != null ? enrich.openAccess() : base.openAccess(),
                coalesce(enrich.journal(), base.journal()),
                coalesce(enrich.sourceType(), base.sourceType()),
                coalesce(enrich.sourceIdentifier(), base.sourceIdentifier()),
                mergeAuthorDetails(base.authorDetails(), enrich.authorDetails()));
    }

    private List<ExternalAuthorInfo> mergeAuthorDetails(List<ExternalAuthorInfo> a, List<ExternalAuthorInfo> b) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExternalAuthorInfo> merged = new ArrayList<>();
        for (ExternalAuthorInfo info : a) {
            String key = StringUtils.hasText(info.sourceIdentifier()) ? info.sourceIdentifier() : info.name();
            if (seen.add(key)) {
                merged.add(info);
            }
        }
        for (ExternalAuthorInfo info : b) {
            String key = StringUtils.hasText(info.sourceIdentifier()) ? info.sourceIdentifier() : info.name();
            if (seen.add(key)) {
                merged.add(info);
            }
        }
        return merged;
    }

    private List<ExternalKeywordInfo> mergeKeywords(List<ExternalKeywordInfo> a, List<ExternalKeywordInfo> b) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExternalKeywordInfo> merged = new ArrayList<>();
        for (ExternalKeywordInfo info : a) {
            if (seen.add(info.term().toLowerCase().trim())) {
                merged.add(info);
            }
        }
        for (ExternalKeywordInfo info : b) {
            if (seen.add(info.term().toLowerCase().trim())) {
                merged.add(info);
            }
        }
        return merged;
    }

    private List<String> mergeAuthors(List<String> a, List<String> b) {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(a);
        set.addAll(b);
        return new ArrayList<>(set);
    }

    private String coalesce(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private Long savePaperWithRelations(ExternalPaperMetadata metadata) {
        if (!StringUtils.hasText(metadata.title())) {
            return null;
        }
        if (metadata.publicationDate() == null) {
            return null;
        }
        if (!StringUtils.hasText(metadata.doi()) && !StringUtils.hasText(metadata.sourceIdentifier())) {
            return null;
        }
        if (!StringUtils.hasText(metadata.journal())) {
            return null;
        }
        if (metadata.keywords() == null || metadata.keywords().isEmpty()) {
            return null;
        }
        if (metadata.authors() == null || metadata.authors().isEmpty()) {
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
            if (StringUtils.hasText(metadata.sourceIdentifier())) {
                paper.setSourceType(metadata.sourceType());
                paper.setSourceIdentifier(metadata.sourceIdentifier());
            }
            paper.setPrimarySource(metadata.sourceType() != null ? metadata.sourceType() : "OPENALEX");
            paper.setStatus(PaperStatus.ACTIVE);
            paper.setReviewStatus(PaperReviewStatus.NONE);
            if (paper.getCreatedAt() == null) {
                paper.setCreatedAt(LocalDateTime.now());
            }
            paper = paperRepository.save(paper);
        } else {
            paper.setPrimarySource(metadata.sourceType() != null ? metadata.sourceType() : "OPENALEX");
            paper.setStatus(PaperStatus.ACTIVE);
            
            paperReviewService.applyIncomingMetadata(paper, metadata, metadata.sourceType() != null ? metadata.sourceType() : "OPENALEX");
            paper = paperRepository.findById(paper.getId()).orElse(paper);
        }

        linkKeywords(paper, metadata.keywords());
        linkAuthors(paper, metadata);

        if (StringUtils.hasText(metadata.journal())) {
            paper = paperRepository.findById(paper.getId()).orElse(paper);
            String domain = metadata.keywords() != null && !metadata.keywords().isEmpty()
                    ? metadata.keywords().get(0).domain()
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

    private Optional<Paper> findExistingPaper(ExternalPaperMetadata metadata) {
        if (StringUtils.hasText(metadata.sourceIdentifier()) && StringUtils.hasText(metadata.sourceType())) {
            Optional<Paper> bySource = paperRepository.findBySourceTypeAndSourceIdentifierIgnoreCase(
                    metadata.sourceType(), metadata.sourceIdentifier());
            if (bySource.isPresent()) {
                return bySource;
            }
        }
        if (StringUtils.hasText(metadata.doi())) {
            return paperRepository.findByDoiIgnoreCase(metadata.doi());
        }
        return Optional.empty();
    }

    private Paper newPaper() {
        return Paper.builder()
                .createdAt(LocalDateTime.now())
                .status(PaperStatus.ACTIVE)
                .reviewStatus(PaperReviewStatus.NONE)
                .citationCount(0)
                .openAccess(false)
                .build();
    }

    private void linkKeywords(Paper paper, List<ExternalKeywordInfo> keywords) {
        if (keywords == null) {
            return;
        }
        for (ExternalKeywordInfo info : keywords) {
            if (!StringUtils.hasText(info.term())) {
                continue;
            }
            String term = info.term().trim();
            String domain = StringUtils.hasText(info.domain()) ? info.domain().trim() : "General";
            Keyword keyword = keywordRepository.findByTermIgnoreCase(term).orElse(null);
            if (keyword == null) {
                keyword = keywordRepository.save(Keyword.builder()
                        .term(term)
                        .domain(domain)
                        .createdAt(LocalDateTime.now())
                        .build());
            } else if ("General".equalsIgnoreCase(keyword.getDomain()) && !"General".equalsIgnoreCase(domain)) {
                keyword.setDomain(domain);
                keyword = keywordRepository.save(keyword);
            }

            final Keyword finalKeyword = keyword;
            boolean exists = paperKeywordRepository.findByPaperId(paper.getId()).stream()
                    .anyMatch(pk -> pk.getKeyword().getKeywordId().equals(finalKeyword.getKeywordId()));
            if (!exists) {
                paperKeywordRepository.save(PaperKeyword.builder().paper(paper).keyword(finalKeyword).build());
            }
        }
    }

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
            linkOneAuthor(paper, new ExternalAuthorInfo(name.trim(), "LOCAL", null, ""));
        }
    }

    private void linkOneAuthor(Paper paper, ExternalAuthorInfo info) {
        if (!StringUtils.hasText(info.name())) {
            return;
        }
        String trimmed = info.name().trim();
        String affiliation = info.affiliation() != null ? info.affiliation().trim() : "";

        Author author = null;
        if (StringUtils.hasText(info.sourceIdentifier()) && StringUtils.hasText(info.sourceType())) {
            author = authorRepository.findBySourceTypeAndSourceIdentifierIgnoreCase(info.sourceType(), info.sourceIdentifier()).orElse(null);
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
                    .sourceType(info.sourceType())
                    .sourceIdentifier(info.sourceIdentifier())
                    .build());
        } else {
            if (StringUtils.hasText(info.sourceIdentifier())) {
                author.setSourceType(info.sourceType());
                author.setSourceIdentifier(info.sourceIdentifier());
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
