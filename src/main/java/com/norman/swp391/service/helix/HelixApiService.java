package com.norman.swp391.service.helix;

import com.norman.swp391.dto.helix.HelixDtos.*;
import com.norman.swp391.dto.request.auth.LoginRequest;
import com.norman.swp391.dto.request.auth.RegisterRequest;
import com.norman.swp391.dto.request.auth.UpdateNotificationPreferencesRequest;
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

    /**
     * Đăng nhập và trả về phiên làm việc Helix (kèm token).
     */
    public HelixAuthSession login(HelixLoginRequest request) {
        return toHelixSession(authService.login(new LoginRequest(request.email(), request.password())));
    }

    /**
     * Đăng ký tài khoản researcher mới và trả về phiên Helix tương ứng.
     */
    public HelixAuthSession register(HelixRegisterRequest request) {
        UserResponse user = authService.register(RegisterRequest.builder()
                .fullName(request.name())
                .email(request.email())
                .password(request.password())
                .role(UserRole.RESEARCHER)
                .build());
        return new HelixAuthSession(toHelixUser(user), null, null);
    }

    /**
     * Lấy thông tin người dùng hiện đang đăng nhập.
     */
    public HelixUser currentUser() {
        return toHelixUser(authService.getCurrentUser());
    }

    /**
     * Cập nhật tùy chọn nhận thông báo của người dùng hiện tại.
     */
    public HelixUser updateNotificationPreferences(UpdateNotificationPreferencesRequest request) {
        return toHelixUser(authService.updateNotificationPreferences(request));
    }

    /**
     * Liệt kê bài báo theo bộ lọc (danh mục, từ khóa, tìm kiếm) cho danh sách Helix.
     */
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

    /**
     * Lấy chi tiết một bài báo theo id.
     */
    public HelixPaper getPaper(String id) {
        return toHelixPaper(paperService.getById(Long.parseLong(id)));
    }

    /**
     * Lấy hồ sơ tác giả gồm số bài, số trích dẫn và h-index.
     */
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
                author.getHIndex() != null ? author.getHIndex() : estimateHIndex(citations),
                author.getSourceIdentifier(),
                author.getSourceType() != null ? author.getSourceType() : "Local DB");
    }

    /**
     * Lấy danh sách tác giả nổi bật, giới hạn theo limit.
     */
    @Transactional(readOnly = true)
    public List<HelixAuthor> listFeaturedAuthors(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        List<AuthorResponse> authors = authorService.getFeatured(PageRequest.of(0, size)).getContent();
        Map<Long, Integer> paperCounts = batchAuthorPaperCounts(authors.stream().map(AuthorResponse::getId).toList());
        return authors.stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        paperCounts.getOrDefault(a.getId(), 0),
                        a.getCitationCount(),
                        a.getHIndex() != null ? a.getHIndex() : estimateHIndex(a.getCitationCount())))
                .toList();
    }

    /** Đếm số paper theo nhiều author cùng lúc (batch) — tránh N+1 khi map danh sách author sang HelixAuthor. */
    private Map<Long, Integer> batchAuthorPaperCounts(List<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return paperAuthorRepository.countByAuthorIdIn(authorIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));
    }

    /**
     * Liệt kê tác giả có phân trang, hỗ trợ tìm kiếm, lọc theo chủ đề và sắp xếp.
     */
    @Transactional(readOnly = true)
    public PageResponse<HelixAuthor> listAuthors(int page, int size, String q, String topicId, String sortBy) {
        int pageSize = Math.max(1, Math.min(size, 100));

        // "papers" cần JOIN + GROUP BY (không phải cột trực tiếp trên Author) — xử lý riêng,
        // chỉ áp dụng khi không lọc theo topicId (giữ nguyên hành vi domain-trending cũ).
        if ("papers".equalsIgnoreCase(sortBy) && (topicId == null || topicId.isBlank())) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Object[]> rows = paperAuthorRepository.findAuthorsByPaperCountDesc(q, pageable);
            List<HelixAuthor> helixAuthors = rows.getContent().stream()
                    .map(row -> {
                        Author a = (Author) row[0];
                        long paperCount = (Long) row[1];
                        return new HelixAuthor(
                                String.valueOf(a.getId()),
                                a.getName(),
                                a.getAffiliation() != null ? a.getAffiliation() : "",
                                (int) paperCount,
                                a.getCitationCount(),
                                a.getHIndex() != null ? a.getHIndex() : estimateHIndex(a.getCitationCount()));
                    })
                    .toList();
            return PageResponse.from(rows, helixAuthors);
        }

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
            Sort sort = "hIndex".equalsIgnoreCase(sortBy)
                    ? Sort.by(Sort.Direction.DESC, "hIndex")
                    : Sort.by(Sort.Direction.DESC, "citationCount");
            pageable = PageRequest.of(page, pageSize, sort);
            authorPage = authorRepository.findAllAuthors(q, pageable);
        }

        List<Author> authorsContent = authorPage.getContent();
        Map<Long, Integer> paperCounts = batchAuthorPaperCounts(authorsContent.stream().map(Author::getId).toList());
        List<HelixAuthor> helixAuthors = authorsContent.stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        paperCounts.getOrDefault(a.getId(), 0),
                        a.getCitationCount(),
                        a.getHIndex() != null ? a.getHIndex() : estimateHIndex(a.getCitationCount())))
                .toList();

        return PageResponse.from(authorPage, helixAuthors);
    }

    /**
     * Liệt kê các bài báo của một tác giả cụ thể.
     */
    public List<HelixPaper> listAuthorPapers(String authorId, Integer limit) {
        var page = authorService.getPapersByAuthor(Long.parseLong(authorId), Pageable.unpaged());
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
     * Liệt kê các chủ đề đang thịnh hành, giới hạn theo limit.
     */
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

    /**
     * Lấy chi tiết một chủ đề (từ khóa hoặc trending topic) theo id.
     */
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

    /**
     * Liệt kê bài báo thuộc một chủ đề (từ khóa hoặc domain) cụ thể.
     */
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

    /**
     * Tổng hợp dữ liệu cho trang phân tích (KPI, xu hướng, biểu đồ, highlight).
     */
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

        List<AuthorResponse> spotlightAuthors = authorsPage.getContent();
        Map<Long, Integer> spotlightPaperCounts =
                batchAuthorPaperCounts(spotlightAuthors.stream().map(AuthorResponse::getId).toList());
        List<HelixAuthor> helixAuthors = spotlightAuthors.stream()
                .map(a -> new HelixAuthor(
                        String.valueOf(a.getId()),
                        a.getName(),
                        a.getAffiliation() != null ? a.getAffiliation() : "",
                        spotlightPaperCounts.getOrDefault(a.getId(), 0),
                        a.getCitationCount(),
                        a.getHIndex() != null ? a.getHIndex() : estimateHIndex(a.getCitationCount())))
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

    /**
     * Liệt kê thông báo của người dùng hiện tại; nếu rỗng thì trả về thông báo mặc định.
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
     * Đánh dấu một thông báo là đã đọc.
     */
    public void markNotificationRead(Long notificationId) {
        notificationService.markAsRead(notificationId);
    }

    /**
     * Đánh dấu tất cả thông báo là đã đọc.
     */
    public void markAllNotificationsRead() {
        notificationService.markAllAsRead();
    }

    /**
     * Liệt kê các bộ sưu tập của người dùng hiện tại.
     */
    public List<HelixCollection> listCollections() {
        List<CollectionResponse> collections = collectionService.listForCurrentUser();
        if (collections.isEmpty()) {
            return List.of();
        }
        List<Long> ids = collections.stream().map(CollectionResponse::getId).toList();
        Map<Long, List<String>> paperIdsByCollection = collectionPaperRepository
                .findByCollectionIdInOrderBySavedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(
                        cp -> cp.getCollection().getId(),
                        Collectors.mapping(cp -> String.valueOf(cp.getPaper().getId()), Collectors.toList())));
        return collections.stream()
                .map(c -> new HelixCollection(
                        String.valueOf(c.getId()),
                        c.getName(),
                        paperIdsByCollection.getOrDefault(c.getId(), List.of()),
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""))
                .toList();
    }

    /**
     * Lấy chi tiết một bộ sưu tập theo id.
     */
    public HelixCollection getCollection(String id) {
        return toHelixCollection(Long.parseLong(id));
    }

    /**
     * Tạo mới một bộ sưu tập cho người dùng hiện tại.
     */
    @Transactional
    public HelixCollection createCollection(String name) {
        var created = collectionService.create(CollectionRequest.builder().name(name).build());
        return toHelixCollection(created.getId());
    }

    /**
     * Cập nhật tên của một bộ sưu tập theo id.
     */
    @Transactional
    public HelixCollection updateCollection(String id, String name) {
        collectionService.update(Long.parseLong(id), CollectionRequest.builder().name(name).build());
        return toHelixCollection(Long.parseLong(id));
    }

    /**
     * Xóa một bộ sưu tập theo id.
     */
    @Transactional
    public void deleteCollection(String id) {
        collectionService.delete(Long.parseLong(id));
    }

    /**
     * Đồng bộ trạng thái một bài báo trong nhiều bộ sưu tập theo danh sách collectionIds yêu cầu.
     */
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

    /**
     * Xóa một bài báo khỏi bộ sưu tập.
     */
    @Transactional
    public HelixCollection removePaperFromCollection(HelixRemovePaperRequest request) {
        collectionService.removePaper(Long.parseLong(request.collectionId()), Long.parseLong(request.paperId()));
        return toHelixCollection(Long.parseLong(request.collectionId()));
    }

    /**
     * Tổng hợp dữ liệu cho trang tổng quan admin (nhật ký đồng bộ, bài chờ duyệt, bất thường).
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

    /**
     * Kích hoạt đồng bộ dữ liệu thủ công từ admin.
     */
    public HelixSyncResult triggerAdminSync() {
        SyncLogResponse log = adminService.triggerSync();
        return new HelixSyncResult(log.getPapersInserted(), log.getStatus().name(), toSyncMessage(log));
    }

    /**
     * Lấy trạng thái đồng bộ gần nhất.
     */
    public HelixSyncResult latestSyncStatus() {
        SyncLogResponse log = paperSyncService.getLatestSyncStatus();
        if (log == null) {
            return new HelixSyncResult(0, "NONE", "No sync runs yet");
        }
        return new HelixSyncResult(log.getPapersInserted(), log.getStatus().name(), toSyncMessage(log));
    }

    /**
     * Reset các tiến trình đồng bộ bị treo (stale) và trả về trạng thái mới nhất.
     */
    public HelixSyncResult resetStaleSync() {
        paperSyncService.resetStaleRunningSyncs();
        return latestSyncStatus();
    }

    /**
     * Chuyển đổi collectionId thành DTO HelixCollection kèm danh sách paperIds.
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
     * Chuyển đổi một Paper thành DTO HelixPendingReviewPaper cho danh sách chờ duyệt.
     */
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

    /**
     * Tạo thông điệp mô tả trạng thái của một lượt đồng bộ.
     */
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

    /**
     * Chuyển đổi AuthResponse thành HelixAuthSession (user + token).
     */
    private HelixAuthSession toHelixSession(AuthResponse auth) {
        return new HelixAuthSession(toHelixUser(auth.getUser()), auth.getAccessToken(), auth.getRefreshToken());
    }

    /**
     * Chuyển đổi UserResponse thành DTO HelixUser.
     */
    private HelixUser toHelixUser(UserResponse user) {
        String role = user.getRole() == UserRole.SUPER_ADMIN ? "SUPER_ADMIN" : user.getRole().name();
        return new HelixUser(
                user.getFullName(),
                user.getEmail(),
                role,
                user.isNotifyKeywords(),
                user.isNotifyAuthors(),
                user.isNotifyJournals(),
                user.isNotifyEmail());
    }

    /**
     * Nạp danh sách tác giả tham chiếu (HelixAuthorRef) theo từng paperId.
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
     * Chuyển đổi PaperDetailResponse thành DTO HelixPaper đầy đủ chi tiết.
     */
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

    /**
     * Chuyển đổi PaperResponse thành DTO HelixPaper dạng tóm tắt cho danh sách.
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

    private record PaperTopicMeta(List<HelixTopicRef> keywords, String category, double trendScore) {
        /**
         * Trả về PaperTopicMeta mặc định khi bài báo không có chủ đề nào.
         */
        static PaperTopicMeta empty() {
            return new PaperTopicMeta(List.of(), "General", 0);
        }
    }

    /**
     * Nạp metadata chủ đề (từ khóa, danh mục, điểm xu hướng) cho từng paperId.
     */
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

    /**
     * Nạp điểm xu hướng hàng tháng (delta phần trăm) cho tập từ khóa theo năm/tháng.
     */
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

    /**
     * Tính điểm xu hướng cao nhất trong tháng hiện tại trong số các từ khóa cho trước.
     */
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

    /**
     * Dựng dữ liệu tốc độ xuất bản (velocity) 12 tháng gần nhất cho biểu đồ.
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
     * Dựng danh sách lát cắt danh mục (kèm màu) từ top 5 chủ đề thịnh hành cho biểu đồ tròn.
     */
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

    /**
     * Dựng các điểm dữ liệu cho biểu đồ radar từ top 6 chủ đề thịnh hành.
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
     * Dựng dữ liệu bản đồ nhiệt (heatmap) giả lập theo tuần và ngày trong tuần.
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
     * Dựng thông báo mặc định khi người dùng chưa có thông báo thật nào (chào mừng hoặc bài trending).
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
     * Xử lý tiêu đề bài báo bị lỗi encoding bằng cách thay thế bằng đoạn abstract hoặc DOI.
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

    /**
     * Xử lý tên tạp chí bị lỗi encoding bằng cách thay thế bằng nguồn dữ liệu chính.
     */
    private String resolvePaperJournal(Paper paper) {
        String journal = paper.getJournal();
        if (!isLikelyCorruptedText(journal)) {
            return journal != null ? journal : "";
        }
        return paper.getPrimarySource() != null ? paper.getPrimarySource() : "Unknown journal";
    }

    /**
     * Kiểm tra một chuỗi có khả năng bị lỗi encoding (nhiều ký tự "?" hoặc thay thế) hay không.
     */
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

    /**
     * Ánh xạ mã nguồn dữ liệu nội bộ sang tên hiển thị (OpenAlex, CrossRef, Semantic Scholar).
     */
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

    /**
     * Ước tính impact factor dựa trên số trích dẫn chia cho tuổi bài báo.
     */
    private double estimateImpactFactor(int citations, int year) {
        int age = Math.max(1, LocalDate.now().getYear() - year);
        return round(citations / (double) age / 10.0);
    }

    /**
     * Ước tính h-index từ số lượt trích dẫn khi dữ liệu gốc không có sẵn.
     */
    private int estimateHIndex(int citations) {
        int h = 0;
        while ((h + 1) * (h + 1) <= citations) {
            h++;
        }
        return Math.max(h, 1);
    }

    /**
     * Định dạng số lượng lớn thành chuỗi rút gọn (ví dụ 1.2M, 3.4K).
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
     * Làm tròn số thực đến 1 chữ số thập phân.
     */
    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
