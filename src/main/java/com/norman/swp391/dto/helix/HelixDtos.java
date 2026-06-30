package com.norman.swp391.dto.helix;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 */
public final class HelixDtos {

    /**
     * Ngăn khởi tạo.
     */
    private HelixDtos() {}

    /**
     * User Helix.
     */
    public record HelixUser(String name, String email, String role) {}

    /**
     * Session Helix.
     */
    public record HelixAuthSession(HelixUser user, String accessToken, String refreshToken) {}

    /**
     * Login Helix.
     */
    public record HelixLoginRequest(String email, String password) {}

    /**
     * Register Helix.
     */
    public record HelixRegisterRequest(String name, String email, String password) {}

    /**
     * Update profile Helix.
     */
    public record HelixUpdateProfileRequest(String name) {}

    /**
     * Ref tác giả.
     */
    public record HelixAuthorRef(String id, String name) {}

    /**
     * Ref chủ đề.
     */
    public record HelixTopicRef(String id, String name) {}

    /**
     * Bài Helix.
     */
    public record HelixPaper(
            String id,
            String title,
            List<String> authors,
            String journal,
            String journalId,
            int year,
            int citations,
            double trendScore,
            List<HelixTopicRef> keywords,
            String category,
            double impactFactor,
            String doi,
            @JsonProperty("abstract") String abstractText,
            String source,
            List<HelixAuthorRef> authorRefs) {
        /**
         * Chuẩn hóa authorRefs.
         */
        public HelixPaper {
            authorRefs = authorRefs == null ? List.of() : List.copyOf(authorRefs);
        }
    }

    /**
     * Profile tác giả.
     */
    public record HelixAuthorProfile(
            String id,
            String name,
            String affiliation,
            int papers,
            int citations,
            int hIndex,
            String openAlexId,
            String source) {}

    /**
     * Tạp chí Helix.
     */
    public record HelixJournal(
            String id, String name, String publisher, String issn, String domain, double impactFactor) {}

    /**
     * Nguồn API Helix.
     */
    public record HelixApiSource(
            String name, String baseUrl, boolean enabled, String syncSchedule, String lastSyncAt, Double successRate) {}

    /**
     * Update nguồn.
     */
    public record HelixUpdateApiSourceRequest(Boolean enabled, String syncSchedule) {}

    /**
     * KPI.
     */
    public record HelixDashboardKpis(
            double trendScore,
            double trendScoreDelta,
            int activeKeywords,
            String citationVolume,
            double syncHealth,
            int trendingPapers,
            int trendingAuthors) {}

    /**
     * Vận tốc XB.
     */
    public record HelixPublicationVelocityPoint(String month, int papers, int citations) {}

    /**
     * Slice chart.
     */
    public record HelixCategorySlice(String name, int value, String fill) {}

    /**
     * Radar.
     */
    public record HelixRadarFieldPoint(String field, int current, int previous) {}

    /**
     * Heatmap.
     */
    public record HelixHeatmapCell(String week, String day, int value) {}

    public record HelixKeyword(
            String id, String term, int count, double trendScore, int monthsTrending, String category) {}

    /**
     * Author ngắn.
     */
    public record HelixAuthor(
            String id, String name, String affiliation, int papers, int citations, int hIndex) {}

    /**
     * Trend topic.
     */
    public record HelixTopicTrend(String id, String name, int paperCount, double trendScore, int rank) {}

    /**
     * Card highlight.
     */
    public record HelixHighlightCard(
            String id, String title, String subtitle, double metric, String metricLabel) {}

    /**
     * Highlights.
     */
    public record HelixDashboardHighlights(
            HelixHighlightCard topKeyword,
            HelixHighlightCard topAuthor,
            HelixHighlightCard topPaper,
            HelixHighlightCard topFollowedTopic) {}

    /**
     * Chi tiết topic.
     */
    public record HelixTopicDetail(String id, String name, String description, int paperCount, double trendScore) {}

    /**
     * Analytics.
     */
    public record HelixAnalyticsSnapshot(
            HelixDashboardKpis kpis,
            List<HelixPublicationVelocityPoint> publicationVelocity,
            List<HelixCategorySlice> categoryDistribution,
            List<HelixRadarFieldPoint> radarFields,
            List<HelixHeatmapCell> heatmap,
            List<HelixKeyword> trendingKeywords,
            List<HelixAuthor> trendingAuthors,
            List<HelixTopicTrend> trendingTopics,
            HelixDashboardHighlights highlights) {}

    /**
     * TB Helix.
     */
    public record HelixNotification(
            String id, String type, String title, String body, String time, boolean unread) {}

    /**
     * Collection Helix.
     */
    public record HelixCollection(String id, String name, List<String> paperIds, String updatedAt) {}

/**
 * Xử lý nghiệp vụ: HelixCollectionNameRequest.
 */
    public record HelixCollectionNameRequest(String name) {}

/**
 * Xử lý nghiệp vụ: HelixSavePaperRequest.
 */
    public record HelixSavePaperRequest(String paperId, List<String> collectionIds) {}

/**
 * Xử lý nghiệp vụ: HelixRemovePaperRequest.
 */
    public record HelixRemovePaperRequest(String paperId, String collectionId) {}

/**
 * Xử lý nghiệp vụ: HelixIdResponse.
 */
    public record HelixIdResponse(String id) {}

/**
 * Xử lý nghiệp vụ: HelixAuditLog.
 */
    public record HelixAuditLog(String id, String actor, String action, String target, String time, String status) {}

    public record HelixPendingReviewPaper(
            String id,
            String title,
            List<String> authors,
            String journal,
            int year,
            int citations,
            double trendScore,
            List<HelixTopicRef> keywords,
            String category,
            double impactFactor,
            String doi,
            @JsonProperty("abstract") String abstractText,
            String source,
            String status) {}

/**
 * Xử lý nghiệp vụ: HelixAdminOverview.
 */
    public record HelixTopicAnomaly(
            String topicId, String topicName, double trendScore, int paperCount, String detectedAt) {}

    public record HelixAdminOverview(
            List<HelixAuditLog> auditLogs,
            List<HelixPendingReviewPaper> pendingReview,
            List<HelixTopicAnomaly> anomalies,
            long pendingReviewCount) {}

/**
 * Xử lý nghiệp vụ: HelixSyncResult.
 */
    public record HelixSyncResult(int papersFetched, String status, String message) {}

    /**
     * Node trong References Graph — metadata nhẹ của referenced work.
     */
    public record HelixReferenceNode(
            String openAlexId,
            String title,
            Integer year,
            String doi,
            Integer citations,
            String localPaperId,
            boolean existsLocally) {}

    /**
     * Node trong Citation Graph — metadata nhẹ của citing work.
     */
    public record HelixCitationNode(
            String openAlexId,
            String title,
            Integer year,
            String doi,
            Integer citations,
            String localPaperId,
            boolean existsLocally) {}

    /**
     * Gộp References + Citations vào 1 response cho paper detail page.
     */
    public record HelixPaperGraph(
            List<HelixReferenceNode> references,
            List<HelixCitationNode> citations) {}
}
