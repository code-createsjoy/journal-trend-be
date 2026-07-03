package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.KeywordSyncState;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.PaperReference;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.Journal;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.KeywordSyncStatus;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalAuthorProfile;
import com.norman.swp391.integration.model.ExternalKeywordInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.mapper.SyncLogMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.repository.KeywordSyncStateRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.repository.PaperReferenceRepository;
import com.norman.swp391.repository.SyncLogRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.service.ApiSourceService;
import com.norman.swp391.service.JournalService;
import com.norman.swp391.service.NotificationService;
import com.norman.swp391.service.PaperReviewService;
import com.norman.swp391.service.PaperSyncService;
import com.norman.swp391.service.KeywordTrendService;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
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
    private final PaperRepository paperRepository;
    private final KeywordRepository keywordRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final SyncLogRepository syncLogRepository;
    private final KeywordSyncStateRepository keywordSyncStateRepository;
    private final UserRepository userRepository;
    private final ApiSourceService apiSourceService;
    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final NotificationService notificationService;
    private final KeywordTrendService keywordTrendService;
    private final PaperReviewService paperReviewService;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;

    // [Fix #2] Graceful shutdown support
    private volatile boolean shutdownRequested = false;
    private volatile Thread syncThread = null;

    @PreDestroy
    public void onShutdown() {
        shutdownRequested = true;
        Thread currentSyncThread = syncThread;
        if (currentSyncThread != null) {
            currentSyncThread.interrupt();
            log.info("[SYNC] Shutdown requested, interrupting sync thread");
        }
    }

    // [Fix #1] Pessimistic lock — ngăn 2 request đồng thời tạo 2 sync RUNNING
    @Override
    @Transactional
    public SyncLogResponse startSync(Long adminId) {
        expireStaleRunningSyncs();

        // Pessimistic lock — block concurrent startSync() calls
        List<SyncLog> running = syncLogRepository.findByStatusForUpdate(
                SyncStatus.RUNNING, PageRequest.of(0, 1));
        if (!running.isEmpty()) {
            return SyncLogMapper.toResponse(running.get(0));
        }

        User admin = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        SyncLog sync = syncLogRepository.save(SyncLog.builder()
                .startedAt(LocalDateTime.now())
                .status(SyncStatus.RUNNING)
                .papersFetched(0)
                .triggeredByAdmin(admin)
                .build());

        SyncLogResponse response = SyncLogMapper.toResponse(sync);
        Long syncId = sync.getId();
        Thread vt = Thread.startVirtualThread(() -> executeSync(syncId));
        syncThread = vt; // [Fix #2] Track virtual thread for graceful shutdown
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

    private void executeSync(Long syncLogId) {
        SyncLog sync = syncLogRepository.findById(syncLogId).orElse(null);
        if (sync == null) {
            return;
        }

        // [Fix #6] Gộp biến — chỉ dùng totalPapersInserted cho cả limit check
        Set<Long> newPaperIds = new HashSet<>();
        boolean openAlexEnabled = false;

        // Metrics Tracking
        int totalApiCalls = 0;
        int totalPagesFetched = 0;
        int totalPapersFetched = 0;
        int totalPapersInserted = 0;
        // [Fix #4] Early stop tracking — giờ được sử dụng thực sự
        boolean globalEarlyStopTriggered = false;

        try {
            openAlexEnabled = apiSourceService.isEnabled("OpenAlex");
            if (!openAlexEnabled) {
                throw new IllegalStateException("OpenAlex source is disabled in admin config");
            }

            List<String> queries = appProperties.getSync().getSearchQueries();
            if (queries == null || queries.isEmpty()) {
                queries = List.of("computer science");
            }
            int maxPages = Math.max(1, appProperties.getSync().getMaxPages());
            int maxPapers = Math.max(1, appProperties.getSync().getMaxPapersPerRun());

            // Historical collection: always use the fixed from-publication-date from config
            String fromDate = appProperties.getSync().getFromPublicationDate();
            log.info("[SYNC] Historical collection mode. Fixed from_publication_date={}", fromDate);

            int ingestBatchSize = Math.max(1, appProperties.getSync().getIngestBatchSize());
            List<ExternalPaperMetadata> ingestBuffer = new ArrayList<>(ingestBatchSize);

            List<String> enabledSources = new ArrayList<>();
            if (openAlexEnabled) {
                enabledSources.add("OpenAlex");
            }

            // [Fix #8] DOI Pre-Filter — load known identifiers ONCE at sync start, normalize to lowercase for consistent matching
            Set<String> knownDois = paperRepository.findAllDois().stream()
                    .filter(StringUtils::hasText)
                    .map(d -> d.toLowerCase().trim())
                    .collect(Collectors.toCollection(HashSet::new));
            Set<String> knownSourceIds = paperRepository.findAllSourceIdentifiers().stream()
                    .filter(StringUtils::hasText)
                    .map(s -> s.toLowerCase().trim())
                    .collect(Collectors.toCollection(HashSet::new));
            log.info("[SYNC] Loaded {} known DOIs + {} known source IDs for pre-filtering",
                    knownDois.size(), knownSourceIds.size());

            // [Fix #4] Early stopping config
            boolean earlyStoppingEnabled = appProperties.getSync().isEarlyStoppingEnabled();
            int earlyStopThreshold = Math.max(1, appProperties.getSync().getEarlyStopConsecutiveEmptyPages());

            outer:
            for (String query : queries) {
                if (!StringUtils.hasText(query)) {
                    continue;
                }
                String trimmedQuery = query.trim();

                for (String source : enabledSources) {
                    String sourceTypeKey = source.toUpperCase().replace(" ", "");

                    // --- Resume Support: Load or create KeywordSyncState ---
                    KeywordSyncState crawlerState = keywordSyncStateRepository
                            .findByKeywordAndSourceType(trimmedQuery, sourceTypeKey)
                            .orElse(null);

                    if (crawlerState != null && crawlerState.getStatus() == KeywordSyncStatus.COMPLETED) {
                        log.info("[SYNC] Keyword={} Source={} COMPLETED. Skipping.", trimmedQuery, sourceTypeKey);
                        continue;
                    }

                    if (crawlerState == null) {
                        crawlerState = KeywordSyncState.builder()
                                .keyword(trimmedQuery)
                                .sourceType(sourceTypeKey)
                                .lastPage(0)
                                .status(KeywordSyncStatus.IN_PROGRESS)
                                .build();
                        crawlerState = keywordSyncStateRepository.save(crawlerState);
                    }

                    int startPage = crawlerState.getLastPage() + 1;
                    log.info("[SYNC] Keyword={} Source={} Resume Page={}", trimmedQuery, sourceTypeKey, startPage);

                    int queryApiCalls = 0;
                    int queryPagesFetched = 0;
                    int queryPapersFetched = 0;
                    int queryPapersInsertedBefore = totalPapersInserted;
                    // [Fix #4] Consecutive empty pages counter cho early stopping
                    int consecutiveEmptyPages = 0;

                    for (int page = startPage; page <= maxPages; page++) {
                        // [Fix #2] Check graceful shutdown
                        if (shutdownRequested) {
                            log.info("[SYNC] Shutdown requested, stopping gracefully at page {}", page);
                            sync.setErrorMessage("Application shutdown — will resume next run");
                            break outer;
                        }

                        // [Fix #6] Dùng totalPapersInserted thay vì totalFetched
                        if (totalPapersInserted >= maxPapers) {
                            log.info("[SYNC] Keyword={} reached max-papers-per-run limit ({}). Will resume from page {} next run.",
                                    trimmedQuery, maxPapers, page);
                            break outer;
                        }
                        
                        totalApiCalls++;
                        totalPagesFetched++;
                        queryApiCalls++;
                        queryPagesFetched++;

                        List<ExternalPaperMetadata> batch;
                        try {
                            if ("OpenAlex".equals(source)) {
                                batch = openAlexClient.fetchWorks(trimmedQuery, page, fromDate);
                            } else {
                                batch = List.of();
                            }
                        } catch (com.norman.swp391.exception.OpenAlexQuotaExhaustedException ex) {
                            log.error("[SYNC] Keyword={} OpenAlex quota exhausted at page {}. Will resume next run.",
                                    trimmedQuery, page);
                            throw ex;
                        } catch (Exception ex) {
                            log.warn("[SYNC] Keyword={} {} fetch failed page {}: {}", trimmedQuery, source, page, ex.getMessage());
                            continue;
                        }

                        // --- Completion Detection ---
                        if (batch.isEmpty()) {
                            crawlerState.setStatus(KeywordSyncStatus.COMPLETED);
                            crawlerState.setLastSyncTime(LocalDateTime.now());
                            keywordSyncStateRepository.save(crawlerState);
                            log.info("[SYNC] Keyword={} Source={} COMPLETED (empty results at page {})",
                                    trimmedQuery, sourceTypeKey, page);
                            break;
                        }

                        totalPapersFetched += batch.size();
                        queryPapersFetched += batch.size();

                        // Determine the last publication date in this batch
                        LocalDate batchLastPubDate = null;
                        for (ExternalPaperMetadata m : batch) {
                            if (m.publicationDate() != null) {
                                if (batchLastPubDate == null || m.publicationDate().isAfter(batchLastPubDate)) {
                                    batchLastPubDate = m.publicationDate();
                                }
                            }
                        }

                        // [Fix #8] Pre-filter: skip papers already in DB using in-memory sets
                        int newInPage = 0;
                        for (ExternalPaperMetadata metadata : batch) {
                            if (totalPapersInserted >= maxPapers) {
                                break outer;
                            }

                            // [Fix #8] DOI pre-filter — skip already known papers
                            boolean alreadyKnown = false;
                            if (StringUtils.hasText(metadata.doi())
                                    && knownDois.contains(metadata.doi().toLowerCase().trim())) {
                                alreadyKnown = true;
                            }
                            if (!alreadyKnown && StringUtils.hasText(metadata.sourceIdentifier())
                                    && knownSourceIds.contains(metadata.sourceIdentifier().toLowerCase().trim())) {
                                alreadyKnown = true;
                            }
                            if (alreadyKnown) {
                                continue; // Skip — don't add to ingestBuffer
                            }

                            newInPage++;
                            ingestBuffer.add(metadata);
                            if (ingestBuffer.size() >= ingestBatchSize) {
                                int inserted = flushIngest(ingestBuffer, newPaperIds, maxPapers - totalPapersInserted,
                                        knownDois, knownSourceIds);
                                totalPapersInserted += inserted;
                                ingestBuffer.clear();
                            }
                        }

                        // [Fix #4] Early stopping — detect consecutive pages with 0 new papers
                        // Lưu ý: KHÔNG mark COMPLETED vì keyword có thể có papers mới trong tương lai.
                        // Chỉ break ra khỏi loop page hiện tại → lần sync sau sẽ crawl lại từ lastPage.
                        if (newInPage == 0) {
                            consecutiveEmptyPages++;
                            if (earlyStoppingEnabled && consecutiveEmptyPages >= earlyStopThreshold) {
                                globalEarlyStopTriggered = true;
                                log.info("[SYNC] Keyword={} Source={} EARLY STOP — {} consecutive pages with 0 new papers at page {}. Will retry next sync.",
                                        trimmedQuery, sourceTypeKey, consecutiveEmptyPages, page);
                                break; // Chỉ break page loop, KHÔNG mark COMPLETED
                            }
                        } else {
                            consecutiveEmptyPages = 0;
                        }

                        // --- Persist progress immediately after each successfully processed page ---
                        crawlerState.setLastPage(page);
                        if (batchLastPubDate != null) {
                            crawlerState.setLastPublicationDate(batchLastPubDate);
                        }
                        crawlerState.setLastSyncTime(LocalDateTime.now());
                        keywordSyncStateRepository.save(crawlerState);

                        log.info("[SYNC] Keyword={} Page={} PapersFetched={} NewInPage={} PapersInserted={} PapersSkipped={} LastPublicationDate={}",
                                trimmedQuery, page, batch.size(), newInPage,
                                totalPapersInserted - queryPapersInsertedBefore,
                                queryPapersFetched - (totalPapersInserted - queryPapersInsertedBefore),
                                batchLastPubDate);
                    }

                    // Log Query-specific Effectiveness Metrics
                    int queryPapersInserted = totalPapersInserted - queryPapersInsertedBefore;
                    int queryPapersSkipped = queryPapersFetched - queryPapersInserted;
                    log.info("[SYNC] Keyword={} Source={} Summary: PagesFetched={} ApiCalls={} PapersFetched={} PapersInserted={} PapersSkipped={}",
                            trimmedQuery, sourceTypeKey, queryPagesFetched, queryApiCalls,
                            queryPapersFetched, queryPapersInserted, queryPapersSkipped);
                }
            }

            if (!ingestBuffer.isEmpty()) {
                int inserted = flushIngest(ingestBuffer, newPaperIds, maxPapers - totalPapersInserted,
                        knownDois, knownSourceIds);
                totalPapersInserted += inserted;
            }

            // [Fix #7] Post-sync tasks — each isolated with try-catch
            runPostSyncTask("expireStalePendingReviews",
                    () -> paperReviewService.expireStalePendingReviews());
            runPostSyncTask("recalculateAll",
                    () -> keywordTrendService.recalculateAll());
            runPostSyncTask("backfillHistoricalMonths", () -> {
                int backfillMonths = appProperties.getSync().getTrendBackfillMonths();
                if (backfillMonths > 0) {
                    keywordTrendService.backfillHistoricalMonths(backfillMonths);
                }
            });
            runPostSyncTask("notifyTrending",
                    () -> notificationService.notifyTrendingForFollowedKeywords(keywordTrendService.findTrendingKeywords(null, null)));
            runPostSyncTask("notifyNewPapers",
                    () -> notificationService.notifyNewPapersForSubscriptions(newPaperIds));
            runPostSyncTask("enrichAuthorStats", () -> enrichAuthorStats(50)); // chỉ xử lý authors mới từ sync này
            runPostSyncTask("updateJournalImpactFactors",
                    () -> journalService.calculateAndUpdateJournalImpactFactors());
            runPostSyncTask("evictDashboardCache", () -> {
                var cache = cacheManager.getCache("dashboardSummary");
                if (cache != null) cache.clear();
            });

            // [Fix #2] Check if shutdown was requested during sync — mark appropriately
            if (shutdownRequested) {
                sync.setStatus(SyncStatus.FAILED);
                sync.setErrorMessage("Application shutdown — will resume next run");
            } else {
                sync.setStatus(SyncStatus.SUCCESS);
            }
            if (openAlexEnabled) {
                apiSourceService.recordSyncResult("OpenAlex", true);
            }
            log.info("[SYNC] Run #{} completed: {} papers inserted total", syncLogId, totalPapersInserted);
        } catch (Exception ex) {
            log.error("[SYNC] Run #{} failed", syncLogId, ex);
            if (ex instanceof com.norman.swp391.exception.OpenAlexQuotaExhaustedException) {
                sync.setStatus(SyncStatus.FAILED_QUOTA_EXHAUSTED);
            } else {
                sync.setStatus(SyncStatus.FAILED);
            }
            sync.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            if (openAlexEnabled) {
                apiSourceService.recordSyncResult("OpenAlex", false);
            }
        } finally {
            syncThread = null; // [Fix #2] Clear thread reference
            sync.setFinishedAt(LocalDateTime.now());
            sync.setApiCalls(totalApiCalls);
            sync.setPagesFetched(totalPagesFetched);
            sync.setPapersFetched(totalPapersFetched);
            sync.setPapersInserted(totalPapersInserted);
            sync.setPapersSkipped(Math.max(0, totalPapersFetched - totalPapersInserted));
            sync.setEarlyStopTriggered(globalEarlyStopTriggered);
            syncLogRepository.save(sync);
        }
    }

    /**
     * Enrich citationCount và hIndex cho các authors chưa có stats từ OpenAlex.
     * Dùng batch fetch để giảm số API calls. Trả về số authors đã được enrich.
     */
    @Override
    public int enrichAuthorStats(int limit) {
        List<Author> toEnrich = authorRepository.findUnenrichedAuthors(
                PageRequest.of(0, limit));
        if (toEnrich.isEmpty()) {
            return 0;
        }
        log.info("[SYNC] Enriching stats for {} authors from OpenAlex", toEnrich.size());
        List<String> ids = toEnrich.stream()
                .map(Author::getSourceIdentifier)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return 0;
        try {
            Map<String, ExternalAuthorProfile> profileMap = openAlexClient.fetchAuthorsByIds(ids).stream()
                    .filter(p -> StringUtils.hasText(p.openAlexId()))
                    .collect(Collectors.toMap(
                            p -> p.openAlexId().toLowerCase().trim(),
                            p -> p,
                            (a, b) -> a));
            List<Author> toSave = new ArrayList<>();
            for (Author author : toEnrich) {
                if (!StringUtils.hasText(author.getSourceIdentifier())) continue;
                ExternalAuthorProfile profile = profileMap.get(author.getSourceIdentifier().toLowerCase().trim());
                if (profile != null) {
                    if (profile.citedByCount() != null) author.setCitationCount(profile.citedByCount());
                    if (profile.hIndex() != null) author.setHIndex(profile.hIndex());
                    toSave.add(author);
                }
            }
            if (!toSave.isEmpty()) {
                authorRepository.saveAll(toSave);
                log.info("[SYNC] Enriched {} authors with citationCount + hIndex", toSave.size());
            }
            return toSave.size();
        } catch (Exception ex) {
            log.warn("[SYNC] Failed to batch-enrich author stats: {}", ex.getMessage());
            return 0;
        }
    }

    /**
     * [Fix #7] Helper chạy post-sync task với error isolation.
     */
    private void runPostSyncTask(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            log.error("[SYNC] Post-sync task '{}' failed: {}", taskName, ex.getMessage(), ex);
        }
    }

    /**
     * Flush ingest buffer vào DB. Cập nhật knownDois/knownSourceIds sau khi insert.
     */
    private int flushIngest(List<ExternalPaperMetadata> batch, Set<Long> newPaperIds, int maxRemaining,
                            Set<String> knownDois, Set<String> knownSourceIds) {
        if (batch.isEmpty() || maxRemaining <= 0) {
            return 0;
        }
        List<ExternalPaperMetadata> slice = batch.size() > maxRemaining ? batch.subList(0, maxRemaining) : batch;

        // Pre-fetch author stats BEFORE opening the DB transaction to avoid holding connection during HTTP calls
        // Keep original case for API calls; deduplicate by lowercase key
        Map<String, String> candidateAuthorIds = new HashMap<>(); // lowercase -> original
        for (ExternalPaperMetadata metadata : slice) {
            if (metadata.authorDetails() != null) {
                for (ExternalAuthorInfo info : metadata.authorDetails()) {
                    if ("OPENALEX".equalsIgnoreCase(info.sourceType()) && StringUtils.hasText(info.sourceIdentifier())) {
                        String orig = info.sourceIdentifier().trim();
                        candidateAuthorIds.putIfAbsent(orig.toLowerCase(), orig);
                    }
                }
            }
        }
        Map<String, ExternalAuthorProfile> prefetchedProfiles = new HashMap<>();
        if (!candidateAuthorIds.isEmpty()) {
            try {
                openAlexClient.fetchAuthorsByIds(new ArrayList<>(candidateAuthorIds.values())).stream()
                        .filter(p -> StringUtils.hasText(p.openAlexId()))
                        .forEach(p -> prefetchedProfiles.put(p.openAlexId().toLowerCase().trim(), p));
            } catch (Exception ex) {
                log.warn("[SYNC] Pre-fetch author stats failed, hIndex will be null: {}", ex.getMessage());
            }
        }

        // [Fix #5] Nới lỏng validation — chỉ yêu cầu title, publicationDate, doi
        List<ExternalPaperMetadata> validMetas = new ArrayList<>();
        for (ExternalPaperMetadata metadata : slice) {
            if (StringUtils.hasText(metadata.title())
                    && metadata.publicationDate() != null
                    && StringUtils.hasText(metadata.doi())) {
                validMetas.add(metadata);
            }
        }

        if (validMetas.isEmpty()) {
            return 0;
        }

        Integer saved = transactionTemplate.execute(status -> {
            // 1. Bulk fetch existing papers
            List<String> dois = validMetas.stream().map(ExternalPaperMetadata::doi).filter(StringUtils::hasText).toList();
            List<String> ids = validMetas.stream().map(ExternalPaperMetadata::sourceIdentifier).filter(StringUtils::hasText).toList();
            List<Paper> existingPapersList = paperRepository.findByDoiInOrSourceIdentifierIn(
                    dois.isEmpty() ? List.of("") : dois,
                    ids.isEmpty() ? List.of("") : ids
            );

            Map<String, Paper> paperByDoi = new HashMap<>();
            Map<String, Paper> paperBySource = new HashMap<>();
            for (Paper p : existingPapersList) {
                if (StringUtils.hasText(p.getDoi())) {
                    paperByDoi.put(p.getDoi().toLowerCase().trim(), p);
                }
                if (StringUtils.hasText(p.getSourceType()) && StringUtils.hasText(p.getSourceIdentifier())) {
                    String key = p.getSourceType().toUpperCase() + ":" + p.getSourceIdentifier().toLowerCase().trim();
                    paperBySource.put(key, p);
                }
            }

            // Keyword filter config — computed once for the whole batch
            int maxKwPerPaper = appProperties.getSync().getMaxKeywordsPerPaper();
            List<String> allowedDomainsCfg = appProperties.getSync().getAllowedKeywordDomains();
            Set<String> allowedDomainsLower = new HashSet<>();
            if (allowedDomainsCfg != null) {
                for (String d : allowedDomainsCfg) {
                    if (StringUtils.hasText(d)) allowedDomainsLower.add(d.trim().toLowerCase());
                }
            }

            // 2. Bulk fetch existing keywords
            Set<String> allKeywordTerms = new HashSet<>();
            for (ExternalPaperMetadata metadata : validMetas) {
                if (metadata.keywords() != null) {
                    for (ExternalKeywordInfo kw : metadata.keywords()) {
                        if (!StringUtils.hasText(kw.term())) continue;
                        if (!allowedDomainsLower.isEmpty()) {
                            String d = StringUtils.hasText(kw.domain()) ? kw.domain().trim().toLowerCase() : "general";
                            if (!allowedDomainsLower.contains(d)) continue;
                        }
                        allKeywordTerms.add(kw.term().trim().toLowerCase());
                    }
                }
            }
            List<Keyword> existingKeywordsList = keywordRepository.findByTermInIgnoreCase(
                    allKeywordTerms.isEmpty() ? List.of("") : allKeywordTerms
            );
            Map<String, Keyword> keywordMap = new HashMap<>();
            for (Keyword k : existingKeywordsList) {
                keywordMap.put(k.getTerm().toLowerCase().trim(), k);
            }

            // Save new keywords or update existing ones in batch
            List<Keyword> keywordsToSave = new ArrayList<>();
            for (ExternalPaperMetadata metadata : validMetas) {
                if (metadata.keywords() == null) continue;
                for (ExternalKeywordInfo info : metadata.keywords()) {
                    if (!StringUtils.hasText(info.term())) continue;
                    if (!allowedDomainsLower.isEmpty()) {
                        String d = StringUtils.hasText(info.domain()) ? info.domain().trim().toLowerCase() : "general";
                        if (!allowedDomainsLower.contains(d)) continue;
                    }
                    String term = info.term().trim();
                    String domain = StringUtils.hasText(info.domain()) ? info.domain().trim() : "General";
                    Keyword kw = keywordMap.get(term.toLowerCase());
                    if (kw == null) {
                        kw = Keyword.builder()
                                .term(term)
                                .domain(domain)
                                .createdAt(LocalDateTime.now())
                                .build();
                        keywordMap.put(term.toLowerCase(), kw);
                        keywordsToSave.add(kw);
                    } else if ("General".equalsIgnoreCase(kw.getDomain()) && !"General".equalsIgnoreCase(domain)) {
                        kw.setDomain(domain);
                        keywordsToSave.add(kw);
                    }
                }
            }
            if (!keywordsToSave.isEmpty()) {
                keywordRepository.saveAll(keywordsToSave);
            }

            // 3. [Fix #3] Bulk fetch existing authors — by sourceId AND by name (eliminates N+1)
            Set<String> allAuthorIds = new HashSet<>();
            Set<String> allAuthorNames = new HashSet<>();
            for (ExternalPaperMetadata metadata : validMetas) {
                List<ExternalAuthorInfo> authorInfos = new ArrayList<>();
                if (metadata.authorDetails() != null) {
                    authorInfos.addAll(metadata.authorDetails());
                } else if (metadata.authors() != null) {
                    for (String name : metadata.authors()) {
                        if (StringUtils.hasText(name)) {
                            authorInfos.add(new ExternalAuthorInfo(name.trim(), "LOCAL", null, ""));
                        }
                    }
                }
                for (ExternalAuthorInfo auth : authorInfos) {
                    if (StringUtils.hasText(auth.sourceIdentifier()) && "OPENALEX".equalsIgnoreCase(auth.sourceType())) {
                        allAuthorIds.add(auth.sourceIdentifier().trim().toLowerCase());
                    }
                    if (StringUtils.hasText(auth.name())) {
                        allAuthorNames.add(auth.name().trim().toLowerCase());
                    }
                }
            }
            List<Author> existingAuthorsList = authorRepository.findBySourceTypeAndSourceIdentifierIn(
                    "OPENALEX",
                    allAuthorIds.isEmpty() ? List.of("") : allAuthorIds
            );
            Map<String, Author> authorBySourceId = new HashMap<>();
            for (Author a : existingAuthorsList) {
                if (StringUtils.hasText(a.getSourceIdentifier())) {
                    authorBySourceId.put(a.getSourceIdentifier().toLowerCase().trim(), a);
                }
            }

            // [Fix #3] Bulk fetch by name — replaces N+1 findFirstByNameAndAffiliation calls
            List<Author> existingByName = authorRepository.findByNameInIgnoreCase(
                    allAuthorNames.isEmpty() ? List.of("") : allAuthorNames
            );
            Map<String, List<Author>> authorsByNameLower = new HashMap<>();
            for (Author a : existingByName) {
                authorsByNameLower.computeIfAbsent(a.getName().toLowerCase().trim(), k -> new ArrayList<>()).add(a);
            }

            Map<String, Author> authorByNameAffiliation = new HashMap<>();
            List<Author> authorsToSave = new ArrayList<>();

            for (ExternalPaperMetadata metadata : validMetas) {
                List<ExternalAuthorInfo> authorInfos = new ArrayList<>();
                if (metadata.authorDetails() != null && !metadata.authorDetails().isEmpty()) {
                    authorInfos.addAll(metadata.authorDetails());
                } else if (metadata.authors() != null) {
                    for (String name : metadata.authors()) {
                        if (StringUtils.hasText(name)) {
                            authorInfos.add(new ExternalAuthorInfo(name.trim(), "LOCAL", null, ""));
                        }
                    }
                }

                for (ExternalAuthorInfo info : authorInfos) {
                    if (!StringUtils.hasText(info.name())) continue;
                    String trimmed = info.name().trim();
                    String affiliation = info.affiliation() != null ? info.affiliation().trim() : "";
                    String nameAffKey = trimmed.toLowerCase() + "||" + affiliation.toLowerCase();

                    Author author = null;
                    if (StringUtils.hasText(info.sourceIdentifier()) && "OPENALEX".equalsIgnoreCase(info.sourceType())) {
                        author = authorBySourceId.get(info.sourceIdentifier().toLowerCase().trim());
                    }
                    if (author == null) {
                        // [Fix #3] In-memory lookup instead of DB query
                        author = authorByNameAffiliation.get(nameAffKey);
                        if (author == null) {
                            List<Author> candidates = authorsByNameLower.get(trimmed.toLowerCase());
                            if (candidates != null) {
                                for (Author c : candidates) {
                                    String cAff = c.getAffiliation() != null ? c.getAffiliation().trim() : "";
                                    if (cAff.equalsIgnoreCase(affiliation)) {
                                        author = c;
                                        break;
                                    }
                                }
                            }
                            if (author != null) {
                                authorByNameAffiliation.put(nameAffKey, author);
                            }
                        }
                    }

                    if (author == null) {
                        author = Author.builder()
                                .name(truncateText(trimmed, 255))
                                .affiliation(truncateText(affiliation, 500))
                                .citationCount(0)
                                .sourceType(info.sourceType())
                                .sourceIdentifier(info.sourceIdentifier())
                                .build();
                        if (StringUtils.hasText(info.sourceIdentifier()) && "OPENALEX".equalsIgnoreCase(info.sourceType())) {
                            authorBySourceId.put(info.sourceIdentifier().toLowerCase().trim(), author);
                        }
                        authorByNameAffiliation.put(nameAffKey, author);
                        // Also add to bulk name lookup cache so future lookups find it
                        authorsByNameLower.computeIfAbsent(trimmed.toLowerCase(), k -> new ArrayList<>()).add(author);
                        authorsToSave.add(author);
                    } else {
                        boolean dirty = false;
                        if (StringUtils.hasText(info.sourceIdentifier()) && !Objects.equals(info.sourceIdentifier(), author.getSourceIdentifier())) {
                            author.setSourceType(info.sourceType());
                            author.setSourceIdentifier(info.sourceIdentifier());
                            dirty = true;
                        }
                        if (StringUtils.hasText(affiliation) && !affiliation.equals(author.getAffiliation())) {
                            author.setAffiliation(affiliation);
                            dirty = true;
                        }
                        if (dirty) {
                            authorsToSave.add(author);
                        }
                    }
                }
            }
            if (!authorsToSave.isEmpty()) {
                // Apply pre-fetched author stats (fetched outside transaction to avoid holding DB connection during HTTP)
                for (Author a : authorsToSave) {
                    if (!"OPENALEX".equalsIgnoreCase(a.getSourceType())
                            || !StringUtils.hasText(a.getSourceIdentifier())
                            || a.getHIndex() != null) continue;
                    ExternalAuthorProfile profile = prefetchedProfiles.get(a.getSourceIdentifier().toLowerCase().trim());
                    if (profile != null) {
                        if (profile.citedByCount() != null) a.setCitationCount(profile.citedByCount());
                        if (profile.hIndex() != null) a.setHIndex(profile.hIndex());
                    }
                }
                authorRepository.saveAll(authorsToSave);
            }

            // 4. Batch process journals using local cache to avoid redundant database searches
            Map<String, Journal> journalMap = new HashMap<>();
            List<Paper> papersToSave = new ArrayList<>();
            Map<ExternalPaperMetadata, Paper> metaToPaperMap = new HashMap<>();
            Set<Paper> newPapersSet = new HashSet<>();

            for (ExternalPaperMetadata metadata : validMetas) {
                // Skip papers whose keywords are all filtered out by domain whitelist
                if (!allowedDomainsLower.isEmpty()) {
                    boolean hasValidKeyword = false;
                    if (metadata.keywords() != null) {
                        for (ExternalKeywordInfo kw : metadata.keywords()) {
                            if (!StringUtils.hasText(kw.term())) continue;
                            String d = StringUtils.hasText(kw.domain()) ? kw.domain().trim().toLowerCase() : "general";
                            if (allowedDomainsLower.contains(d)) {
                                hasValidKeyword = true;
                                break;
                            }
                        }
                    }
                    if (!hasValidKeyword) continue;
                }

                Paper paper = null;
                if (StringUtils.hasText(metadata.sourceIdentifier()) && StringUtils.hasText(metadata.sourceType())) {
                    String key = metadata.sourceType().toUpperCase() + ":" + metadata.sourceIdentifier().toLowerCase().trim();
                    paper = paperBySource.get(key);
                }
                if (paper == null && StringUtils.hasText(metadata.doi())) {
                    paper = paperByDoi.get(metadata.doi().toLowerCase().trim());
                }

                boolean isNew = (paper == null);
                if (isNew) {
                    paper = Paper.builder()
                            .createdAt(LocalDateTime.now())
                            .status(PaperStatus.ACTIVE)
                            .reviewStatus(PaperReviewStatus.NONE)
                            .citationCount(0)
                            .openAccess(false)
                            .build();
                    newPapersSet.add(paper);
                    // Register in lookup maps immediately to prevent duplicate DOI within same batch
                    if (StringUtils.hasText(metadata.doi())) {
                        paperByDoi.put(metadata.doi().toLowerCase().trim(), paper);
                    }
                    if (StringUtils.hasText(metadata.sourceIdentifier()) && StringUtils.hasText(metadata.sourceType())) {
                        String regKey = metadata.sourceType().toUpperCase() + ":" + metadata.sourceIdentifier().toLowerCase().trim();
                        paperBySource.put(regKey, paper);
                    }
                }

                if (isNew) {
                    paper.setTitle(truncateText(metadata.title(), 1000));
                    paper.setAbstractText(metadata.abstractText());
                    paper.setDoi(truncateText(metadata.doi(), 255));
                    if (metadata.publicationDate() != null) {
                        paper.setPublicationDate(metadata.publicationDate());
                    }
                    if (metadata.citationCount() != null) {
                        paper.setCitationCount(metadata.citationCount());
                    }
                    paper.setPdfUrl(truncateText(metadata.pdfUrl(), 500));
                    paper.setSourceUrl(truncateText(metadata.landingPageUrl(), 500));
                    paper.setOpenAccess(metadata.openAccess() != null ? metadata.openAccess() : paper.isOpenAccess());
                    if (StringUtils.hasText(metadata.sourceIdentifier())) {
                        paper.setSourceType(truncateText(metadata.sourceType(), 50));
                        paper.setSourceIdentifier(truncateText(metadata.sourceIdentifier(), 100));
                    }
                    paper.setPrimarySource(metadata.sourceType() != null ? truncateText(metadata.sourceType(), 50) : "OPENALEX");
                    paper.setStatus(PaperStatus.ACTIVE);
                    paper.setReviewStatus(PaperReviewStatus.NONE);
                    if (paper.getCreatedAt() == null) {
                        paper.setCreatedAt(LocalDateTime.now());
                    }
                } else {
                    paper.setPrimarySource(metadata.sourceType() != null ? truncateText(metadata.sourceType(), 50) : "OPENALEX");
                    paper.setStatus(PaperStatus.ACTIVE);
                    paperReviewService.applyIncomingMetadata(paper, metadata, metadata.sourceType() != null ? metadata.sourceType() : "OPENALEX");
                }

                if (StringUtils.hasText(metadata.journal())) {
                    String jName = metadata.journal().trim();
                    Journal journalEntity = journalMap.computeIfAbsent(jName, name -> {
                        String domain = metadata.keywords() != null && !metadata.keywords().isEmpty()
                                ? metadata.keywords().get(0).domain()
                                : "General";
                        try {
                            return journalService.findOrCreate(name, null, domain);
                        } catch (Exception ex) {
                            log.debug("Journal link skipped: {}", ex.getMessage());
                            return null;
                        }
                    });
                    if (journalEntity != null && journalEntity.getId() != null) {
                        paper.setJournalRef(journalRepository.getReferenceById(journalEntity.getId()));
                        paper.setJournal(truncateText(journalEntity.getName(), 500));
                    } else {
                        paper.setJournal(truncateText(metadata.journal(), 500));
                    }
                }

                papersToSave.add(paper);
                metaToPaperMap.put(metadata, paper);
            }

            // Bulk save all papers to DB
            List<Paper> savedPapers = paperRepository.saveAll(papersToSave);

            // 5. Bulk fetch paper relationships (keywords & authors)
            List<Long> savedPaperIds = savedPapers.stream().map(Paper::getId).filter(Objects::nonNull).toList();
            List<PaperKeyword> existingPaperKeywords = paperKeywordRepository.findByPaperIdInWithKeyword(savedPaperIds);
            Map<Long, Set<Long>> paperToKeywordsMap = new HashMap<>();
            for (PaperKeyword pk : existingPaperKeywords) {
                paperToKeywordsMap.computeIfAbsent(pk.getPaper().getId(), k -> new HashSet<>())
                        .add(pk.getKeyword().getKeywordId());
            }

            List<PaperAuthor> existingPaperAuthors = paperAuthorRepository.findByPaperIdInWithAuthor(savedPaperIds);
            Map<Long, Set<Long>> paperToAuthorsMap = new HashMap<>();
            for (PaperAuthor pa : existingPaperAuthors) {
                paperToAuthorsMap.computeIfAbsent(pa.getPaper().getId(), k -> new HashSet<>())
                        .add(pa.getAuthor().getId());
            }

            List<PaperKeyword> paperKeywordsToSave = new ArrayList<>();
            List<PaperAuthor> paperAuthorsToSave = new ArrayList<>();

            for (ExternalPaperMetadata metadata : validMetas) {
                Paper paper = metaToPaperMap.get(metadata);
                if (paper == null || paper.getId() == null) continue;

                // Link keywords in batch — apply domain filter + per-paper cap
                if (metadata.keywords() != null) {
                    Set<Long> existingKeywordIds = paperToKeywordsMap.computeIfAbsent(paper.getId(), k -> new HashSet<>());
                    int kwCount = 0;
                    for (ExternalKeywordInfo info : metadata.keywords()) {
                        if (maxKwPerPaper > 0 && kwCount >= maxKwPerPaper) break;
                        if (!StringUtils.hasText(info.term())) continue;
                        if (!allowedDomainsLower.isEmpty()) {
                            String d = StringUtils.hasText(info.domain()) ? info.domain().trim().toLowerCase() : "general";
                            if (!allowedDomainsLower.contains(d)) continue;
                        }
                        Keyword kw = keywordMap.get(info.term().trim().toLowerCase());
                        if (kw != null && kw.getKeywordId() != null) {
                            if (!existingKeywordIds.contains(kw.getKeywordId())) {
                                paperKeywordsToSave.add(PaperKeyword.builder().paper(paper).keyword(kw).build());
                                existingKeywordIds.add(kw.getKeywordId());
                                kwCount++;
                            }
                        }
                    }
                }

                // Link authors in batch
                List<ExternalAuthorInfo> authorInfos = new ArrayList<>();
                if (metadata.authorDetails() != null && !metadata.authorDetails().isEmpty()) {
                    authorInfos.addAll(metadata.authorDetails());
                } else if (metadata.authors() != null) {
                    for (String name : metadata.authors()) {
                        if (StringUtils.hasText(name)) {
                            authorInfos.add(new ExternalAuthorInfo(name.trim(), "LOCAL", null, ""));
                        }
                    }
                }

                Set<Long> existingAuthorIds = paperToAuthorsMap.computeIfAbsent(paper.getId(), k -> new HashSet<>());
                for (ExternalAuthorInfo info : authorInfos) {
                    if (!StringUtils.hasText(info.name())) continue;
                    String trimmed = info.name().trim();
                    String affiliation = info.affiliation() != null ? info.affiliation().trim() : "";
                    String nameAffKey = trimmed.toLowerCase() + "||" + affiliation.toLowerCase();

                    Author author = null;
                    if (StringUtils.hasText(info.sourceIdentifier()) && "OPENALEX".equalsIgnoreCase(info.sourceType())) {
                        author = authorBySourceId.get(info.sourceIdentifier().toLowerCase().trim());
                    }
                    if (author == null) {
                        author = authorByNameAffiliation.get(nameAffKey);
                    }

                    if (author != null && author.getId() != null) {
                        if (!existingAuthorIds.contains(author.getId())) {
                            paperAuthorsToSave.add(PaperAuthor.builder().paper(paper).author(author).authorPosition(info.authorPosition()).build());
                            existingAuthorIds.add(author.getId());
                        }
                    }
                }
            }

            if (!paperKeywordsToSave.isEmpty()) {
                paperKeywordRepository.saveAll(paperKeywordsToSave);
            }
            if (!paperAuthorsToSave.isEmpty()) {
                paperAuthorRepository.saveAll(paperAuthorsToSave);
            }

            // 6. Bulk insert PaperReference rows (referenced_works from OpenAlex)
            List<PaperReference> refsToSave = new ArrayList<>();
            LocalDateTime refFetchedAt = LocalDateTime.now();
            for (Map.Entry<ExternalPaperMetadata, Paper> entry : metaToPaperMap.entrySet()) {
                ExternalPaperMetadata meta = entry.getKey();
                Paper p = entry.getValue();
                if (p.getId() == null || meta.referencedWorkIds() == null || meta.referencedWorkIds().isEmpty()) {
                    continue;
                }
                // Chỉ insert cho papers mới (tránh duplicate khi update)
                if (!newPapersSet.contains(p)) {
                    continue;
                }
                // Deduplicate references to prevent unique constraint violation
                List<String> uniqueRefs = meta.referencedWorkIds().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();

                for (String refId : uniqueRefs) {
                    refsToSave.add(PaperReference.builder()
                            .paperId(p.getId())
                            .referencedOpenAlexId(refId)
                            .fetchedAt(refFetchedAt)
                            .build());
                }
            }
            if (!refsToSave.isEmpty()) {
                try {
                    TransactionTemplate requiresNew = new TransactionTemplate(transactionTemplate.getTransactionManager());
                    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    requiresNew.executeWithoutResult(innerStatus -> {
                        paperReferenceRepository.saveAll(refsToSave);
                    });
                    log.debug("[SYNC] Saved {} paper references", refsToSave.size());
                } catch (Exception ex) {
                    log.warn("[SYNC] Error saving bulk paper references in REQUIRES_NEW: {}", ex.getMessage());
                }
            }

            // Return how many NEW papers were added to newPaperIds
            int count = 0;
            for (Paper p : savedPapers) {
                if (newPapersSet.contains(p)) {
                    newPaperIds.add(p.getId());
                    count++;
                    // [Fix #8] Update known sets with newly inserted papers
                    if (StringUtils.hasText(p.getDoi())) {
                        knownDois.add(p.getDoi().toLowerCase().trim());
                    }
                    if (StringUtils.hasText(p.getSourceIdentifier())) {
                        knownSourceIds.add(p.getSourceIdentifier().toLowerCase().trim());
                    }
                }
            }
            return count;
        });

        return saved != null ? saved : 0;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
