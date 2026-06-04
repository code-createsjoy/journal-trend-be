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
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
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
    private final TopicService topicService;
    private final TopicTrendService topicTrendService;
    private final DashboardHighlightService dashboardHighlightService;
    private final CollectionService collectionService;
    private final NotificationService notificationService;
    private final AuthorService authorService;
    private final AdminService adminService;
    private final PaperSyncService paperSyncService;
    private final PaperRepository paperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final TopicTrendRepository topicTrendRepository;
    private final PaperCollectionRepository paperCollectionRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final SyncLogRepository syncLogRepository;
    private final TopicAnomalyService topicAnomalyService;
    private final PaperReviewService paperReviewService;

/**
 * Đăng nhập và trả về token.
 */
    public HelixAuthSession login(HelixLoginRequest request) {
        return toHelixSession(authService.login(new LoginRequest(request.email(), request.password())));
    }

/**
 * Đăng ký tài khoản mới.
 */
    public HelixAuthSession register(HelixRegisterRequest request) {
        return toHelixSession(authService.register(RegisterRequest.builder()
                .fullName(request.name())
                .email(request.email())
                .password(request.password())
                .build()));
    }

/**
 * Xử lý nghiệp vụ: currentUser.
 */
    public HelixUser currentUser() {
        return toHelixUser(authService.getCurrentUser());
    }

/**
 * Danh sách: listPapers.
 */
    public List<HelixPaper> listPapers(String category, String excludeId, Integer limit, String q, Long topicId) {
        int pageSize = limit != null && limit > 0 ? Math.min(limit, 100) : 100;
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by("citationCount").descending());
        String query = (q == null || q.isBlank()) ? null : q;
        Page<Paper> page;
        if (topicId != null) {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, query, topicId, null, pageable);
        } else if (query == null) {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, null, null, null, pageable);
        } else {
            page = paperRepository.search(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, query, null, null, pageable);
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

/**
 * Lấy dữ liệu: getPaper.
 */
    public HelixPaper getPaper(String id) {
        return toHelixPaper(paperService.getById(Long.parseLong(id)));
    }

    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getAuthorProfile.
 */
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
                round(Math.min(99, Math.log10(citations + 1) * 22)),
                author.getOpenAlexId(),
                author.getOpenAlexId() != null ? "OpenAlex" : "Local DB");
    }

    @Transactional(readOnly = true)
/**
 * Danh sách: listFeaturedAuthors.
 */
    public List<HelixAuthor> listFeaturedAuthors(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return authorService.getFeatured(PageRequest.of(0, size)).getContent().stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        (int) paperAuthorRepository.countByAuthorId(a.getId()),
                        a.getCitationCount(),
                        estimateHIndex(a.getCitationCount()),
                        round(Math.min(99, Math.log10(a.getCitationCount() + 1) * 22))))
                .toList();
    }

/**
 * Danh sách: listAuthorPapers.
 */
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

/**
 * Danh sách: listTrendingTopics.
 */
    public List<HelixTopicTrend> listTrendingTopics(int limit) {
        return selectConsecutiveTrending(limit).stream()
                .map(topic -> new HelixTopicTrend(
                        String.valueOf(topic.getTopicId()),
                        topic.getTopicName(),
                        topic.getPaperCount(),
                        topic.getTrendScore() != null ? topic.getTrendScore().doubleValue() : 0,
                        topic.getRank()))
                .toList();
    }

/**
 * Lấy dữ liệu: getTopicDetail.
 */
    public HelixTopicDetail getTopicDetail(String id) {
        TopicResponse topic = topicService.getById(Long.parseLong(id));
        var monthly = topicTrendService.getCurrentMonthTrend(topic.getId());
        double score = monthly.getTrendScore() != null ? monthly.getTrendScore().doubleValue() : 0.0;
        int paperCount = monthly.getPaperCount();
        return new HelixTopicDetail(
                String.valueOf(topic.getId()),
                topic.getName(),
                topic.getDescription(),
                paperCount,
                score);
    }

/**
 * Danh sách: listPapersByTopic.
 */
    public List<HelixPaper> listPapersByTopic(String topicId, Integer limit) {
        return listPapers(null, null, limit, null, Long.parseLong(topicId));
    }

/**
 * Xử lý nghiệp vụ: analyticsSnapshot.
 */
    public HelixAnalyticsSnapshot analyticsSnapshot() {
        var stats = adminService.getSystemStats();
        // Topic trend: 3 tháng liên tiếp >= 15%
        List<TrendingTopicResponse> monthlyTop = topicTrendService.findTopByTrendScore(10);
        List<TrendingTopicResponse> topicTrends = selectConsecutiveTrending(10);
        List<TrendingTopicResponse> forDisplay = topicTrends.isEmpty() ? monthlyTop : topicTrends;
        var authorsPage = authorService.getFeatured(PageRequest.of(0, 10));

        double avgMonthlyTrend = monthlyTop.stream()
                .map(TrendingTopicResponse::getTrendScore)
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
        List<HelixCategorySlice> slices = buildCategorySlices(monthlyTop);
        List<HelixRadarFieldPoint> radar = buildRadarFields(monthlyTop);
        List<HelixHeatmapCell> heatmap = buildHeatmap((int) stats.getTotalPapers());
        List<HelixKeyword> keywords = forDisplay.stream()
                .limit(8)
                .map(t -> new HelixKeyword(
                        String.valueOf(t.getTopicId()),
                        t.getTopicName(),
                        t.getPaperCount(),
                        t.getTrendScore() != null ? t.getTrendScore().doubleValue() : 0,
                        topicTrends.isEmpty() ? 1 : 3,
                        "Research"))
                .toList();
        List<HelixAuthor> helixAuthors = authorsPage.getContent().stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        (int) paperAuthorRepository.countByAuthorId(a.getId()),
                        a.getCitationCount(),
                        estimateHIndex(a.getCitationCount()),
                        round(Math.min(99, a.getCitationCount() / 10.0))))
                .toList();
        List<HelixTopicTrend> helixTopics = forDisplay.stream()
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

    /** Topic trend chính thức: 3 tháng liên tiếp có trendScore >= 15%. */
    private List<TrendingTopicResponse> selectConsecutiveTrending(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return topicTrendService.findTrendingTopicResponses().stream()
                .filter(t -> t.getTrendScore() != null)
                .sorted(Comparator.comparing(TrendingTopicResponse::getTrendScore)
                        .reversed()
                        .thenComparing(TrendingTopicResponse::getPaperCount, Comparator.reverseOrder()))
                .limit(size)
                .toList();
    }

/**
 * Danh sách: listNotifications.
 */
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

/**
 * Xử lý nghiệp vụ: markNotificationRead.
 */
    public void markNotificationRead(Long notificationId) {
        notificationService.markAsRead(notificationId);
    }

/**
 * Xử lý nghiệp vụ: markAllNotificationsRead.
 */
    public void markAllNotificationsRead() {
        notificationService.markAllAsRead();
    }

/**
 * Danh sách: listCollections.
 */
    public List<HelixCollection> listCollections() {
        return collectionService.listForCurrentUser().stream()
                .map(c -> toHelixCollection(c.getId()))
                .toList();
    }

/**
 * Lấy dữ liệu: getCollection.
 */
    public HelixCollection getCollection(String id) {
        return toHelixCollection(Long.parseLong(id));
    }

    @Transactional
/**
 * Tạo hoặc lưu: createCollection.
 */
    public HelixCollection createCollection(String name) {
        var created = collectionService.create(CollectionRequest.builder().name(name).build());
        return toHelixCollection(created.getId());
    }

    @Transactional
/**
 * Cập nhật: updateCollection.
 */
    public HelixCollection updateCollection(String id, String name) {
        collectionService.update(Long.parseLong(id), CollectionRequest.builder().name(name).build());
        return toHelixCollection(Long.parseLong(id));
    }

    @Transactional
/**
 * Xóa: deleteCollection.
 */
    public void deleteCollection(String id) {
        collectionService.delete(Long.parseLong(id));
    }

    @Transactional
/**
 * Tạo hoặc lưu: savePaperToCollections.
 */
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
/**
 * Xóa: removePaperFromCollection.
 */
    public HelixCollection removePaperFromCollection(HelixRemovePaperRequest request) {
        collectionService.removePaper(Long.parseLong(request.collectionId()), Long.parseLong(request.paperId()));
        return toHelixCollection(Long.parseLong(request.collectionId()));
    }

/**
 * Xử lý nghiệp vụ: adminOverview.
 */
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
                        PaperReviewStatus.PENDING_REVIEW, PageRequest.of(0, 10, Sort.by("reviewFlaggedAt").descending()))
                .getContent()
                .stream()
                .map(this::toPendingReview)
                .toList();
        List<HelixTopicAnomaly> anomalies = topicAnomalyService.listCurrentAnomalies(10).stream()
                .map(a -> new HelixTopicAnomaly(
                        String.valueOf(a.getTopicId()),
                        a.getTopicName(),
                        a.getTrendScore() != null ? a.getTrendScore().doubleValue() : 0,
                        a.getPaperCount(),
                        a.getDetectedAt() != null ? a.getDetectedAt().toString() : ""))
                .toList();
        return new HelixAdminOverview(
                logs, pending, anomalies, paperReviewService.countByReviewStatus(PaperReviewStatus.PENDING_REVIEW));
    }

/**
 * Xử lý nghiệp vụ: triggerAdminSync.
 */
    public HelixSyncResult triggerAdminSync() {
        SyncLogResponse log = adminService.triggerSync();
        return new HelixSyncResult(log.getPapersFetched(), log.getStatus().name(), toSyncMessage(log));
    }

/**
 * Xử lý nghiệp vụ: latestSyncStatus.
 */
    public HelixSyncResult latestSyncStatus() {
        SyncLogResponse log = paperSyncService.getLatestSyncStatus();
        if (log == null) {
            return new HelixSyncResult(0, "NONE", "No sync runs yet");
        }
        return new HelixSyncResult(log.getPapersFetched(), log.getStatus().name(), toSyncMessage(log));
    }

/**
 * Xử lý nghiệp vụ: resetStaleSync.
 */
    public HelixSyncResult resetStaleSync() {
        paperSyncService.resetStaleRunningSyncs();
        return latestSyncStatus();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toHelixCollection.
 */
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

/**
 * Ánh xạ sang DTO/phản hồi: toPendingReview.
 */
    private HelixPendingReviewPaper toPendingReview(Paper paper) {
        int year = paper.getPublicationDate() != null ? paper.getPublicationDate().getYear() : LocalDate.now().getYear();
        Map<Long, List<HelixAuthorRef>> refs = loadAuthorRefsByPaper(List.of(paper.getId()));
        List<String> authors = refs.getOrDefault(paper.getId(), List.of()).stream()
                .map(HelixAuthorRef::name)
                .toList();
        List<String> keywords = paperTopicRepository.findByPaperId(paper.getId()).stream()
                .map(pt -> pt.getTopic().getName())
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

/**
 * Ánh xạ sang DTO/phản hồi: toSyncMessage.
 */
    private String toSyncMessage(SyncLogResponse log) {
        if (log.getStatus() == SyncStatus.RUNNING) {
            return "Syncing metadata from OpenAlex…";
        }
        if (log.getStatus() == SyncStatus.SUCCESS) {
            return "Complete · " + log.getPapersFetched() + " papers";
        }
        String err = log.getErrorMessage();
        return "Failed · " + (err != null ? err : "see audit log");
    }

/**
 * Ánh xạ sang DTO/phản hồi: toHelixSession.
 */
    private HelixAuthSession toHelixSession(AuthResponse auth) {
        return new HelixAuthSession(toHelixUser(auth.getUser()), auth.getAccessToken());
    }

/**
 * Ánh xạ sang DTO/phản hồi: toHelixUser.
 */
    private HelixUser toHelixUser(UserResponse user) {
        String role = user.getRole() == UserRole.SUPER_ADMIN ? "SUPER_ADMIN" : user.getRole().name();
        return new HelixUser(user.getFullName(), user.getEmail(), role);
    }

/**
 * Xử lý nghiệp vụ: loadAuthorRefsByPaper.
 */
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

/**
 * Ánh xạ sang DTO/phản hồi: toHelixPaper.
 */
    private HelixPaper toHelixPaper(PaperDetailResponse detail) {
        List<String> authors = detail.getAuthors() != null
                ? detail.getAuthors().stream().map(AuthorResponse::getName).toList()
                : List.of();
        List<String> keywords = detail.getTopics() != null
                ? detail.getTopics().stream().map(TopicResponse::getName).toList()
                : List.of();
        int year = detail.getPublicationDate() != null
                ? detail.getPublicationDate().getYear()
                : LocalDate.now().getYear();
        String category = keywords.isEmpty() ? "General" : keywords.get(0);
        List<HelixAuthorRef> refs = detail.getAuthors() != null
                ? detail.getAuthors().stream()
                        .map(a -> new HelixAuthorRef(String.valueOf(a.getId()), a.getName()))
                        .toList()
                : List.of();
        List<Long> topicIds = detail.getTopics() != null
                ? detail.getTopics().stream().map(TopicResponse::getId).toList()
                : List.of();
        double trendScore = maxMonthlyTopicTrendScore(topicIds);
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

/**
 * Ánh xạ sang DTO/phản hồi: toHelixPaperSummary.
 */
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

    private record PaperTopicMeta(List<String> keywords, String category, double trendScore) {
        static PaperTopicMeta empty() {
            return new PaperTopicMeta(List.of(), "General", 0);
        }
    }

    private Map<Long, PaperTopicMeta> loadPaperTopicMeta(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return Map.of();
        }
        List<PaperTopic> allLinks = paperTopicRepository.findByPaperIdInWithTopic(paperIds);
        LocalDate now = LocalDate.now();
        Map<Long, Double> trendByTopicId = loadMonthlyTopicTrendScores(
                allLinks.stream().map(pt -> pt.getTopic().getId()).collect(Collectors.toSet()),
                now.getYear(),
                now.getMonthValue());

        Map<Long, List<PaperTopic>> topicsByPaper = new HashMap<>();
        for (PaperTopic pt : allLinks) {
            topicsByPaper.computeIfAbsent(pt.getPaper().getId(), k -> new ArrayList<>()).add(pt);
        }

        Map<Long, PaperTopicMeta> result = new HashMap<>();
        for (Long paperId : paperIds) {
            List<PaperTopic> links = topicsByPaper.getOrDefault(paperId, List.of());
            List<String> keywords = links.stream()
                    .map(pt -> pt.getTopic().getName())
                    .distinct()
                    .toList();
            double trendScore = links.stream()
                    .mapToDouble(pt -> trendByTopicId.getOrDefault(pt.getTopic().getId(), 0.0))
                    .max()
                    .orElse(0);
            String category = keywords.isEmpty() ? "General" : keywords.get(0);
            result.put(paperId, new PaperTopicMeta(keywords, category, round(trendScore)));
        }
        return result;
    }

    /** % tăng trưởng trong tháng (không áp rule 3 tháng liên tiếp). */
    private Map<Long, Double> loadMonthlyTopicTrendScores(Set<Long> topicIds, int year, int month) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> scores = new HashMap<>();
        for (TopicTrend trend : topicTrendRepository.findByTopicIdInAndYearAndMonth(topicIds, year, month)) {
            if (trend.getTrendScore() != null) {
                scores.put(trend.getTopic().getId(), trend.getTrendScore().doubleValue());
            }
        }
        return scores;
    }

    private double maxMonthlyTopicTrendScore(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return 0;
        }
        LocalDate now = LocalDate.now();
        Map<Long, Double> scores =
                loadMonthlyTopicTrendScores(new HashSet<>(topicIds), now.getYear(), now.getMonthValue());
        return round(
                topicIds.stream().mapToDouble(id -> scores.getOrDefault(id, 0.0)).max().orElse(0));
    }

/**
 * Tạo/ghép dữ liệu: buildPublicationVelocity.
 */
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

/**
 * Tạo/ghép dữ liệu: buildCategorySlices.
 */
    private List<HelixCategorySlice> buildCategorySlices(List<TrendingTopicResponse> trending) {
        String[] fills = {"#6366f1", "#22c55e", "#f59e0b", "#ef4444", "#8b5cf6"};
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

/**
 * Tạo/ghép dữ liệu: buildRadarFields.
 */
    private List<HelixRadarFieldPoint> buildRadarFields(List<TrendingTopicResponse> trending) {
        return trending.stream()
                .limit(6)
                .map(t -> new HelixRadarFieldPoint(
                        t.getTopicName(),
                        t.getTrendScore() != null ? Math.min(100, t.getTrendScore().intValue()) : 50,
                        45))
                .toList();
    }

/**
 * Tạo/ghép dữ liệu: buildHeatmap.
 */
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

/**
 * Tạo/ghép dữ liệu: buildFallbackNotifications.
 */
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

/**
 * Chuyển đổi entity sang DTO: mapSource.
 */
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
            case "semantic scholar", "semanticscholar", "s2" -> "Semantic Scholar";
            default -> "OpenAlex";
        };
    }

/**
 * Xử lý nghiệp vụ: estimateImpactFactor.
 */
    private double estimateImpactFactor(int citations, int year) {
        int age = Math.max(1, LocalDate.now().getYear() - year);
        return round(citations / (double) age / 10.0);
    }

/**
 * Xử lý nghiệp vụ: estimateHIndex.
 */
    private int estimateHIndex(int citations) {
        int h = 0;
        while ((h + 1) * (h + 1) <= citations) {
            h++;
        }
        return Math.max(h, 1);
    }

/**
 * Xử lý nghiệp vụ: formatVolume.
 */
    private String formatVolume(long total) {
        if (total >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", total / 1_000_000.0);
        }
        if (total >= 1_000) {
            return String.format(Locale.US, "%.1fK", total / 1_000.0);
        }
        return String.valueOf(total);
    }

/**
 * Xử lý nghiệp vụ: round.
 */
    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
