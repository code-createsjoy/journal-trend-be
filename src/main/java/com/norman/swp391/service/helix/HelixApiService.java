package com.norman.swp391.service.helix;

import com.norman.swp391.dto.helix.HelixDtos.*;
import com.norman.swp391.dto.request.auth.LoginRequest;
import com.norman.swp391.dto.request.auth.RegisterRequest;
import com.norman.swp391.dto.request.collection.AddPaperToCollectionRequest;
import com.norman.swp391.dto.request.collection.CollectionRequest;
import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.dto.response.auth.AuthResponse;
import com.norman.swp391.dto.response.auth.UserResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.collection.CollectionResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.*;
import com.norman.swp391.entity.enums.*;
import com.norman.swp391.mapper.PaperMapper;
import com.norman.swp391.repository.*;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Facade API cho frontend Helix (JSON contract riêng).
 */
@Service
@RequiredArgsConstructor
public class HelixApiService {

    private static final List<String> HEATMAP_DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private final AuthService authService;
    private final PaperService paperService;
    private final KeywordService keywordService;
    private final KeywordTrendService keywordTrendService;
    private final DashboardHighlightService dashboardHighlightService;
    private final CollectionService collectionService;
    private final NotificationService notificationService;
    private final AuthorService authorService;
    private final AdminService adminService;
    private final PaperSyncService paperSyncService;
    private final AuthorRepository authorRepository;
    private final PaperRepository paperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final PaperCollectionRepository paperCollectionRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final SyncLogRepository syncLogRepository;
    private final PaperReviewService paperReviewService;
    private final KeywordRepository keywordRepository;

    public HelixAuthSession login(HelixLoginRequest request) {
        return toHelixSession(authService.login(new LoginRequest(request.email(), request.password())));
    }

    public HelixAuthSession register(HelixRegisterRequest request) {
        UserResponse user = authService.register(RegisterRequest.builder()
                .fullName(request.name())
                .email(request.email())
                .password(request.password())
                .role(UserRole.RESEARCHER)
                .build());
        return new HelixAuthSession(toHelixUser(user), null, null);
    }

    public HelixUser currentUser() {
        return toHelixUser(authService.getCurrentUser());
    }

    public List<HelixPaper> listPapers(String category, String excludeId, Integer limit, String q, Long keywordId) {
        int pageSize = limit != null && limit > 0 ? Math.min(limit, 100) : 100;
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by("citationCount").descending());
        String query = (q == null || q.isBlank()) ? null : q;
        Page<Paper> page;
        if (keywordId != null) {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, query, null, keywordId, null, null, null, null, null, null, pageable);
        } else if (query == null) {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, null, null, null, null, null, null, null, null, null, pageable);
        } else {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, query, null, null, null, null, null, null, null, null, pageable);
        }
        List<Paper> paperEntities = page.getContent();
        List<Long> paperIds = paperEntities.stream().map(Paper::getId).toList();
        Map<Long, List<HelixAuthorRef>> authorRefsByPaper = loadAuthorRefsByPaper(paperIds);
        Map<Long, PaperTopicMeta> topicMetaByPaper = loadPaperTopicMeta(paperIds);
        List<HelixPaper> papers = PaperMapper.toResponseList(paperEntities).stream()
                .map(p -> toHelixPaperSummary(
                        p,
                        authorRefsByPaper.getOrDefault(p.getId(), List.of()),
                        topicMetaByPaper.get(p.getId())))
                .collect(Collectors.toCollection(ArrayList::new));

        if (category != null && !category.isBlank()) {
            papers = papers.stream()
                    .filter(p -> p.category().equalsIgnoreCase(category))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (excludeId != null) {
            papers = papers.stream()
                    .filter(p -> !p.id().equals(excludeId))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (limit != null && limit > 0 && papers.size() > limit) {
            return papers.subList(0, limit);
        }
        return papers;
    }

    public HelixPaper getPaper(String id) {
        return toHelixPaper(paperService.getById(Long.parseLong(id)));
    }

    @Transactional(readOnly = true)
    public HelixAuthorProfile getAuthorProfile(String id) {
        long authorId = Long.parseLong(id);
        AuthorResponse author = authorService.getById(authorId);
        int paperCount = (int) paperAuthorRepository.countByAuthorId(authorId);
        int citations = author.getCitationCount();
        String name = author.getName();
        String affiliation = author.getAffiliation() != null ? author.getAffiliation() : "";
        return new HelixAuthorProfile(
                String.valueOf(author.getId()),
                name,
                affiliation,
                Math.max(paperCount, 1),
                citations,
                estimateHIndex(citations),
                author.getSourceIdentifier(),
                author.getSourceType() != null ? author.getSourceType() : "Local DB");
    }

    @Transactional(readOnly = true)
    public List<HelixAuthor> listFeaturedAuthors(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return authorService.getFeatured(PageRequest.of(0, size)).getContent().stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        (int) paperAuthorRepository.countByAuthorId(a.getId()),
                        a.getCitationCount(),
                        estimateHIndex(a.getCitationCount())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<HelixAuthor> listAuthors(int page, int size, String q, String topicId) {
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Author> authorPage;

        if (topicId != null && !topicId.isBlank()) {
            long hash;
            try {
                hash = Long.parseLong(topicId);
            } catch (NumberFormatException e) {
                hash = 0;
            }
            final long targetHash = hash;
            String domain = keywordTrendService.findTrendingTopics().stream()
                    .filter(t -> t.getTopicId() == targetHash)
                    .map(TrendingTopicResponse::getTopicName)
                    .findFirst()
                    .orElse(null);

            if (domain != null) {
                LocalDate now = LocalDate.now();
                authorPage = authorRepository.findTrendingAuthorsByDomain(domain, q, now.getYear(), now.getMonthValue(),
                        pageable);
            } else {
                authorPage = authorRepository.findAllAuthors(q, pageable);
            }
        } else {
            pageable = PageRequest.of(page, pageSize, Sort.by("citationCount").descending());
            authorPage = authorRepository.findAllAuthors(q, pageable);
        }

        List<HelixAuthor> helixAuthors = authorPage.getContent().stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        (int) paperAuthorRepository.countByAuthorId(a.getId()),
                        a.getCitationCount(),
                        estimateHIndex(a.getCitationCount())))
                .toList();

        return PageResponse.from(authorPage, helixAuthors);
    }

    public List<HelixPaper> listAuthorPapers(String authorId, Integer limit) {
        int size = limit != null && limit > 0 ? Math.min(limit, 100) : 50;
        var page = authorService.getPapersByAuthor(Long.parseLong(authorId), PageRequest.of(0, size));
        List<Long> paperIds = page.getContent().stream().map(PaperResponse::getId).toList();
        Map<Long, List<HelixAuthorRef>> refs = loadAuthorRefsByPaper(paperIds);
        Map<Long, PaperTopicMeta> topicMetaByPaper = loadPaperTopicMeta(paperIds);
        return page.getContent().stream()
                .map(p -> toHelixPaperSummary(
                        p,
                        refs.getOrDefault(p.getId(), List.of()),
                        topicMetaByPaper.get(p.getId())))
                .toList();
    }

    public List<HelixTopicTrend> listTrendingTopics(int limit) {
        return keywordTrendService.findTrendingTopics().stream()
                .limit(limit)
                .map(topic -> new HelixTopicTrend(
                        String.valueOf(topic.getTopicId()),
                        topic.getTopicName(),
                        topic.getPaperCount(),
                        topic.getTrendScore() != null ? topic.getTrendScore().doubleValue() : 0.0,
                        topic.getRank()))
                .toList();
    }

    public HelixTopicDetail getTopicDetail(String id) {
        long hash = Long.parseLong(id);
        Optional<Keyword> kwOpt = keywordRepository.findById(hash);
        if (kwOpt.isPresent()) {
            Keyword kw = kwOpt.get();
            return new HelixTopicDetail(
                    String.valueOf(kw.getKeywordId()),
                    kw.getTerm(),
                    "Trending keyword in " + (kw.getDomain() != null ? kw.getDomain() : "Research"),
                    kw.getPaperCount(),
                    kw.getTrendScore() != null ? kw.getTrendScore().doubleValue() : 0.0);
        }
        TrendingTopicResponse matched = keywordTrendService.findTrendingTopics().stream()
                .filter(t -> t.getTopicId() == hash)
                .findFirst()
                .orElse(null);
        if (matched != null) {
            return new HelixTopicDetail(
                    String.valueOf(matched.getTopicId()),
                    matched.getTopicName(),
                    matched.getDescription(),
                    matched.getPaperCount(),
                    matched.getTrendScore() != null ? matched.getTrendScore().doubleValue() : 0.0);
        }
        return new HelixTopicDetail(id, "General", "General research field", 0, 0.0);
    }

    @Transactional(readOnly = true)
    public List<HelixPaper> listPapersByTopic(String topicId, Integer limit) {
        int pageSize = limit != null && limit > 0 ? Math.min(limit, 100) : 50;
        long hash = Long.parseLong(topicId);
        Optional<Keyword> kwOpt = keywordRepository.findById(hash);
        List<Paper> paperEntities;
        if (kwOpt.isPresent()) {
            paperEntities = paperRepository.search(
                    PaperStatus.ACTIVE,
                    PaperReviewStatus.NONE,
                    null,
                    null,
                    hash,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    PageRequest.of(0, pageSize)
            ).getContent();
        } else {
            String domain = keywordTrendService.findTrendingTopics().stream()
                    .filter(t -> t.getTopicId() == hash)
                    .map(TrendingTopicResponse::getTopicName)
                    .findFirst()
                    .orElse(null);
            if (domain == null) {
                return List.of();
            }
            paperEntities = paperRepository.findByKeywordDomain(domain);
            if (paperEntities.size() > pageSize) {
                paperEntities = paperEntities.subList(0, pageSize);
            }
        }
        List<Long> paperIds = paperEntities.stream().map(Paper::getId).toList();
        Map<Long, List<HelixAuthorRef>> authorRefsByPaper = loadAuthorRefsByPaper(paperIds);
        Map<Long, PaperTopicMeta> topicMetaByPaper = loadPaperTopicMeta(paperIds);

        return PaperMapper.toResponseList(paperEntities).stream()
                .map(p -> toHelixPaperSummary(
                        p,
                        authorRefsByPaper.getOrDefault(p.getId(), List.of()),
                        topicMetaByPaper.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public HelixAnalyticsSnapshot analyticsSnapshot() {
        var stats = adminService.getSystemStats();
        List<TrendingKeywordResponse> monthlyTop = keywordTrendService.findTopByTrendScore(10);
        List<TrendingTopicResponse> topicTrends = keywordTrendService.findTrendingTopics();
        var authorsPage = authorService.getFeatured(PageRequest.of(0, 10));

        double avgMonthlyTrend = monthlyTop.stream()
                .map(TrendingKeywordResponse::getTrendScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        long totalCitations = paperRepository.sumCitationCountByStatus(PaperStatus.ACTIVE);
        SyncLog lastSync = syncLogRepository.findFirstByOrderByStartedAtDesc();
        double syncHealth = lastSync != null && lastSync.getStatus() == SyncStatus.SUCCESS ? 98.0 : 72.0;

        HelixDashboardKpis kpis = new HelixDashboardKpis(
                round(avgMonthlyTrend),
                2.4,
                topicTrends.size(),
                formatVolume(totalCitations),
                syncHealth,
                (int) stats.getTotalPapers(),
                (int) stats.getTotalAuthors());

        List<HelixPublicationVelocityPoint> velocity = buildPublicationVelocity();
        List<HelixCategorySlice> slices = buildCategorySlices(topicTrends);
        List<HelixRadarFieldPoint> radar = buildRadarFields(topicTrends);
        List<HelixHeatmapCell> heatmap = buildHeatmap((int) stats.getTotalPapers());

        List<HelixKeyword> keywords = monthlyTop.stream()
                .limit(8)
                .map(k -> new HelixKeyword(
                        String.valueOf(k.getKeywordId()),
                        k.getTerm(),
                        k.getPaperCount(),
                        k.getTrendScore() != null ? k.getTrendScore().doubleValue() : 0,
                        3,
                        k.getDomain() != null ? k.getDomain() : "Research"))
                .toList();

        List<HelixAuthor> helixAuthors = authorsPage.getContent().stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        (int) paperAuthorRepository.countByAuthorId(a.getId()),
                        a.getCitationCount(),
                        estimateHIndex(a.getCitationCount())))
                .toList();

        List<HelixTopicTrend> helixTopics = topicTrends.stream()
                .map(t -> new HelixTopicTrend(
                        String.valueOf(t.getTopicId()),
                        t.getTopicName(),
                        t.getPaperCount(),
                        t.getTrendScore() != null ? t.getTrendScore().doubleValue() : 0,
                        t.getRank()))
                .toList();

        return new HelixAnalyticsSnapshot(
                kpis,
                velocity,
                slices,
                radar,
                heatmap,
                keywords,
                helixAuthors,
                helixTopics,
                dashboardHighlightService.buildHighlights());
    }

    public List<HelixNotification> listNotifications() {
        var page = notificationService.listForCurrentUser(PageRequest.of(0, 20));
        List<HelixNotification> items = new ArrayList<>();
        for (NotificationResponse n : page.getContent()) {
            boolean unread = n.getReadStatus() == NotificationReadStatus.UNREAD;
            items.add(new HelixNotification(
                    String.valueOf(n.getId()),
                    "system",
                    "Notification",
                    n.getMessage(),
                    n.getCreatedAt().toString(),
                    unread));
        }
        if (items.isEmpty()) {
            items.addAll(buildFallbackNotifications());
        }
        return items;
    }

    public void markNotificationRead(Long notificationId) {
        notificationService.markAsRead(notificationId);
    }

    public void markAllNotificationsRead() {
        notificationService.markAllAsRead();
    }

    public List<HelixCollection> listCollections() {
        return collectionService.listForCurrentUser().stream()
                .map(c -> toHelixCollection(c.getId()))
                .toList();
    }

    public HelixCollection getCollection(String id) {
        return toHelixCollection(Long.parseLong(id));
    }

    @Transactional
    public HelixCollection createCollection(String name) {
        var created = collectionService.create(CollectionRequest.builder().name(name).build());
        return toHelixCollection(created.getId());
    }

    @Transactional
    public HelixCollection updateCollection(String id, String name) {
        collectionService.update(Long.parseLong(id), CollectionRequest.builder().name(name).build());
        return toHelixCollection(Long.parseLong(id));
    }

    @Transactional
    public void deleteCollection(String id) {
        collectionService.delete(Long.parseLong(id));
    }

    @Transactional
    public List<HelixCollection> savePaperToCollections(HelixSavePaperRequest request) {
        Long paperId = Long.parseLong(request.paperId());
        Long userId = SecurityUtils.getCurrentUserId();
        List<PaperCollection> collections = paperCollectionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        for (PaperCollection col : collections) {
            boolean shouldHave = request.collectionIds().contains(String.valueOf(col.getId()));
            boolean has = collectionPaperRepository.existsByCollectionIdAndPaperId(col.getId(), paperId);
            if (shouldHave && !has) {
                collectionService.addPaper(
                        col.getId(), AddPaperToCollectionRequest.builder().paperId(paperId).build());
            } else if (!shouldHave && has) {
                collectionService.removePaper(col.getId(), paperId);
            }
        }
        return listCollections();
    }

    @Transactional
    public HelixCollection removePaperFromCollection(HelixRemovePaperRequest request) {
        collectionService.removePaper(Long.parseLong(request.collectionId()), Long.parseLong(request.paperId()));
        return toHelixCollection(Long.parseLong(request.collectionId()));
    }

    @Transactional(readOnly = true)
    public HelixAdminOverview adminOverview() {
        List<HelixAuditLog> logs = syncLogRepository.findRecentWithAdmin(PageRequest.of(0, 10)).stream()
                .map(s -> new HelixAuditLog(
                        String.valueOf(s.getId()),
                        s.getTriggeredByAdmin() != null ? s.getTriggeredByAdmin().getEmail() : "system",
                        "SYNC",
                        "OpenAlex metadata",
                        s.getStartedAt().toString(),
                        s.getStatus().name()))
                .toList();
        List<HelixPendingReviewPaper> pending = paperRepository
                .findByReviewStatus(
                        PaperReviewStatus.PENDING_REVIEW,
                        PageRequest.of(0, 10, Sort.by("reviewFlaggedAt").descending()))
                .getContent()
                .stream()
                .map(this::toPendingReview)
                .toList();

        // Topic anomalies are removed, so return an empty list
        List<HelixTopicAnomaly> anomalies = List.of();

        return new HelixAdminOverview(
                logs, pending, anomalies, paperReviewService.countByReviewStatus(PaperReviewStatus.PENDING_REVIEW));
    }

    public HelixSyncResult triggerAdminSync() {
        SyncLogResponse log = adminService.triggerSync();
        return new HelixSyncResult(log.getPapersInserted(), log.getStatus().name(), toSyncMessage(log));
    }

    public HelixSyncResult latestSyncStatus() {
        SyncLogResponse log = paperSyncService.getLatestSyncStatus();
        if (log == null) {
            return new HelixSyncResult(0, "NONE", "No sync runs yet");
        }
        return new HelixSyncResult(log.getPapersInserted(), log.getStatus().name(), toSyncMessage(log));
    }

    public HelixSyncResult resetStaleSync() {
        paperSyncService.resetStaleRunningSyncs();
        return latestSyncStatus();
    }

    private HelixCollection toHelixCollection(Long collectionId) {
        CollectionResponse c = collectionService.getById(collectionId);
        List<String> paperIds = collectionService.listPapers(collectionId).stream()
                .map(p -> String.valueOf(p.getId()))
                .toList();
        return new HelixCollection(
                String.valueOf(c.getId()),
                c.getName(),
                paperIds,
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
    }

    private HelixPendingReviewPaper toPendingReview(Paper paper) {
        int year = paper.getPublicationDate() != null ? paper.getPublicationDate().getYear()
                : LocalDate.now().getYear();
        Map<Long, List<HelixAuthorRef>> refs = loadAuthorRefsByPaper(List.of(paper.getId()));
        List<String> authors = refs.getOrDefault(paper.getId(), List.of()).stream()
                .map(HelixAuthorRef::name)
                .toList();
        List<HelixTopicRef> keywords = paperKeywordRepository.findByPaperId(paper.getId()).stream()
                .map(pk -> new HelixTopicRef(String.valueOf(pk.getKeyword().getKeywordId()), pk.getKeyword().getTerm()))
                .toList();
        return new HelixPendingReviewPaper(
                String.valueOf(paper.getId()),
                resolvePaperTitle(paper),
                authors,
                resolvePaperJournal(paper),
                year,
                paper.getCitationCount(),
                0,
                keywords,
                "General",
                estimateImpactFactor(paper.getCitationCount(), year),
                paper.getDoi(),
                paper.getAbstractText(),
                mapSource(paper.getPrimarySource()),
                paper.getReviewStatus() != null ? paper.getReviewStatus().name() : "NONE");
    }

    private String toSyncMessage(SyncLogResponse log) {
        if (log.getStatus() == SyncStatus.RUNNING) {
            return "Syncing metadata from OpenAlex…";
        }
        if (log.getStatus() == SyncStatus.SUCCESS) {
            return "Complete · " + log.getPapersInserted() + " papers inserted";
        }
        String err = log.getErrorMessage();
        return "Failed · " + (err != null ? err : "see audit log");
    }

    private HelixAuthSession toHelixSession(AuthResponse auth) {
        return new HelixAuthSession(toHelixUser(auth.getUser()), auth.getAccessToken(), auth.getRefreshToken());
    }

    private HelixUser toHelixUser(UserResponse user) {
        String role = user.getRole() == UserRole.SUPER_ADMIN ? "SUPER_ADMIN" : user.getRole().name();
        return new HelixUser(user.getFullName(), user.getEmail(), role);
    }

    private Map<Long, List<HelixAuthorRef>> loadAuthorRefsByPaper(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<HelixAuthorRef>> map = new HashMap<>();
        for (PaperAuthor pa : paperAuthorRepository.findByPaperIdInWithAuthor(paperIds)) {
            map.computeIfAbsent(pa.getPaper().getId(), k -> new ArrayList<>())
                    .add(new HelixAuthorRef(
                            String.valueOf(pa.getAuthor().getId()), pa.getAuthor().getName()));
        }
        return map;
    }

    private HelixPaper toHelixPaper(PaperDetailResponse detail) {
        List<String> authors = detail.getAuthors() != null
                ? detail.getAuthors().stream().map(AuthorResponse::getName).toList()
                : List.of();
        List<HelixTopicRef> keywords = detail.getKeywords() != null
                ? detail.getKeywords().stream()
                        .map(k -> new HelixTopicRef(String.valueOf(k.getKeywordId()), k.getTerm()))
                        .toList()
                : List.of();
        int year = detail.getPublicationDate() != null
                ? detail.getPublicationDate().getYear()
                : LocalDate.now().getYear();
        String category = keywords.isEmpty() ? "General" : keywords.get(0).name();
        List<HelixAuthorRef> refs = detail.getAuthors() != null
                ? detail.getAuthors().stream()
                        .map(a -> new HelixAuthorRef(String.valueOf(a.getId()), a.getName()))
                        .toList()
                : List.of();
        List<Long> keywordIds = detail.getKeywords() != null
                ? detail.getKeywords().stream().map(KeywordResponse::getKeywordId).toList()
                : List.of();
        double trendScore = maxMonthlyKeywordTrendScore(keywordIds);
        return new HelixPaper(
                String.valueOf(detail.getId()),
                detail.getTitle(),
                authors,
                detail.getJournal() != null ? detail.getJournal() : "",
                detail.getJournalId() != null ? String.valueOf(detail.getJournalId()) : "",
                year,
                detail.getCitationCount(),
                trendScore,
                keywords,
                category,
                estimateImpactFactor(detail.getCitationCount(), year),
                detail.getDoi(),
                detail.getAbstractText(),
                mapSource(detail.getPrimarySource()),
                refs);
    }

    private HelixPaper toHelixPaperSummary(PaperResponse p, List<HelixAuthorRef> authorRefs, PaperTopicMeta topicMeta) {
        int year = p.getPublicationDate() != null ? p.getPublicationDate().getYear() : LocalDate.now().getYear();
        List<String> authors = authorRefs.stream().map(HelixAuthorRef::name).toList();
        PaperTopicMeta meta = topicMeta != null ? topicMeta : PaperTopicMeta.empty();
        return new HelixPaper(
                String.valueOf(p.getId()),
                p.getTitle(),
                authors,
                p.getJournal() != null ? p.getJournal() : "",
                p.getJournalId() != null ? String.valueOf(p.getJournalId()) : "",
                year,
                p.getCitationCount(),
                meta.trendScore(),
                meta.keywords(),
                meta.category(),
                estimateImpactFactor(p.getCitationCount(), year),
                p.getDoi(),
                p.getAbstractText(),
                mapSource(p.getPrimarySource()),
                authorRefs);
    }

    private record PaperTopicMeta(List<HelixTopicRef> keywords, String category, double trendScore) {
        static PaperTopicMeta empty() {
            return new PaperTopicMeta(List.of(), "General", 0);
        }
    }

    private Map<Long, PaperTopicMeta> loadPaperTopicMeta(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return Map.of();
        }
        List<PaperKeyword> allLinks = paperKeywordRepository.findByPaperIdInWithKeyword(paperIds);
        LocalDate now = LocalDate.now();
        Map<Long, Double> trendByKeywordId = loadMonthlyKeywordTrendScores(
                allLinks.stream().map(pk -> pk.getKeyword().getKeywordId()).collect(Collectors.toSet()),
                now.getYear(),
                now.getMonthValue());

        Map<Long, List<PaperKeyword>> keywordsByPaper = new HashMap<>();
        for (PaperKeyword pk : allLinks) {
            keywordsByPaper.computeIfAbsent(pk.getPaper().getId(), k -> new ArrayList<>()).add(pk);
        }

        Map<Long, PaperTopicMeta> result = new HashMap<>();
        for (Long paperId : paperIds) {
            List<PaperKeyword> links = keywordsByPaper.getOrDefault(paperId, List.of());
            List<HelixTopicRef> keywords = links.stream()
                    .map(pk -> new HelixTopicRef(String.valueOf(pk.getKeyword().getKeywordId()),
                            pk.getKeyword().getTerm()))
                    .distinct()
                    .toList();
            double trendScore = links.stream()
                    .mapToDouble(pk -> trendByKeywordId.getOrDefault(pk.getKeyword().getKeywordId(), 0.0))
                    .max()
                    .orElse(0);
            String category = keywords.isEmpty() ? "General" : keywords.get(0).name();
            result.put(paperId, new PaperTopicMeta(keywords, category, round(trendScore)));
        }
        return result;
    }

    private Map<Long, Double> loadMonthlyKeywordTrendScores(Set<Long> keywordIds, int year, int month) {
        if (keywordIds == null || keywordIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> scores = new HashMap<>();
        List<PublicationTrend> trends = publicationTrendRepository.findByYearAndMonth(year, month);
        for (PublicationTrend trend : trends) {
            Long kwId = trend.getKeyword().getKeywordId();
            if (keywordIds.contains(kwId) && trend.getDeltaPercent() != null) {
                scores.put(kwId, trend.getDeltaPercent().doubleValue());
            }
        }
        return scores;
    }

    private double maxMonthlyKeywordTrendScore(List<Long> keywordIds) {
        if (keywordIds == null || keywordIds.isEmpty()) {
            return 0;
        }
        LocalDate now = LocalDate.now();
        Map<Long, Double> scores = loadMonthlyKeywordTrendScores(new HashSet<>(keywordIds), now.getYear(),
                now.getMonthValue());
        return round(
                keywordIds.stream().mapToDouble(id -> scores.getOrDefault(id, 0.0)).max().orElse(0));
    }

    private List<HelixPublicationVelocityPoint> buildPublicationVelocity() {
        List<HelixPublicationVelocityPoint> points = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String label = cursor.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + cursor.getYear();
            int papers = (int) paperRepository.countByStatus(PaperStatus.ACTIVE) / 12;
            points.add(new HelixPublicationVelocityPoint(label, Math.max(1, papers + i), papers * 3));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    private List<HelixCategorySlice> buildCategorySlices(List<TrendingTopicResponse> trending) {
        String[] fills = { "#6366f1", "#22c55e", "#f59e0b", "#ef4444", "#8b5cf6" };
        List<HelixCategorySlice> slices = new ArrayList<>();
        int i = 0;
        for (TrendingTopicResponse t : trending.stream().limit(5).toList()) {
            slices.add(new HelixCategorySlice(
                    t.getTopicName(), t.getPaperCount(), fills[i++ % fills.length]));
        }
        if (slices.isEmpty()) {
            slices.add(new HelixCategorySlice("General", 1, fills[0]));
        }
        return slices;
    }

    private List<HelixRadarFieldPoint> buildRadarFields(List<TrendingTopicResponse> trending) {
        return trending.stream()
                .limit(6)
                .map(t -> new HelixRadarFieldPoint(
                        t.getTopicName(),
                        t.getTrendScore() != null ? Math.min(100, t.getTrendScore().intValue()) : 50,
                        45))
                .toList();
    }

    private List<HelixHeatmapCell> buildHeatmap(int seed) {
        List<HelixHeatmapCell> cells = new ArrayList<>();
        for (int w = 1; w <= 12; w++) {
            for (String day : HEATMAP_DAYS) {
                int value = 20 + Math.abs((seed + w + day.hashCode()) % 80);
                cells.add(new HelixHeatmapCell("W" + w, day, value));
            }
        }
        return cells;
    }

    private List<HelixNotification> buildFallbackNotifications() {
        if (paperRepository.countByStatus(PaperStatus.ACTIVE) == 0) {
            return List.of(new HelixNotification(
                    "n-welcome",
                    "system",
                    "No paper data yet",
                    "Go to Admin → Run Manual Sync to load metadata from OpenAlex.",
                    java.time.Instant.now().toString(),
                    true));
        }
        return listPapers(null, null, 3, null, null).stream()
                .map(p -> new HelixNotification(
                        "n-" + p.id(),
                        "paper",
                        "Trending paper",
                        p.title().substring(0, Math.min(80, p.title().length())) + "…",
                        java.time.Instant.now().toString(),
                        true))
                .toList();
    }

    private String resolvePaperTitle(Paper paper) {
        String title = paper.getTitle();
        if (!isLikelyCorruptedText(title)) {
            return title != null ? title : "";
        }
        if (StringUtils.hasText(paper.getAbstractText())) {
            String snippet = paper.getAbstractText().strip();
            int end = Math.min(snippet.length(), 120);
            int space = snippet.lastIndexOf(' ', end);
            if (space > 40) {
                end = space;
            }
            return snippet.substring(0, end) + (snippet.length() > end ? "…" : "");
        }
        if (StringUtils.hasText(paper.getDoi())) {
            return "DOI: " + paper.getDoi();
        }
        return title != null ? title : "Untitled paper";
    }

    private String resolvePaperJournal(Paper paper) {
        String journal = paper.getJournal();
        if (!isLikelyCorruptedText(journal)) {
            return journal != null ? journal : "";
        }
        return paper.getPrimarySource() != null ? paper.getPrimarySource() : "Unknown journal";
    }

    private boolean isLikelyCorruptedText(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.contains("????") || text.contains("???")) {
            return true;
        }
        long questionMarks = text.chars().filter(ch -> ch == '?' || ch == '\uFFFD').count();
        return questionMarks * 3 >= text.length();
    }

    private String mapSource(String source) {
        if (source == null) {
            return "OpenAlex";
        }
        return switch (source.toLowerCase()) {
            case "crossref" -> "CrossRef";
            case "semantic scholar", "semanticscholar", "s2" -> "Semantic Scholar"; // Legacy compatibility for existing database records
            default -> "OpenAlex";
        };
    }

    private double estimateImpactFactor(int citations, int year) {
        int age = Math.max(1, LocalDate.now().getYear() - year);
        return round(citations / (double) age / 10.0);
    }

    private int estimateHIndex(int citations) {
        int h = 0;
        while ((h + 1) * (h + 1) <= citations) {
            h++;
        }
        return Math.max(h, 1);
    }

    private String formatVolume(long total) {
        if (total >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", total / 1_000_000.0);
        }
        if (total >= 1_000) {
            return String.format(Locale.US, "%.1fK", total / 1_000.0);
        }
        return String.valueOf(total);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
