package com.norman.swp391.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình app.*.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String corsAllowedOrigins;
    private String frontendBaseUrl;
    private String backendBaseUrl = "http://localhost:8080";
    private int passwordResetExpirationMinutes;
    private int emailVerificationExpirationMinutes = 1440;
    /**
     * Thực hiện Jwt.
     */
    private int rateLimitPerMinute = 60;
    private boolean schedulerEnabled = true;
    private Jwt jwt = new Jwt();
    /**
     * Thực hiện OpenAlex.
     */
    private OpenAlex openalex = new OpenAlex();

    /**
     * Thực hiện Gemini AI.
     */
    private Gemini gemini = new Gemini();

    /**
     * Thực hiện Sync.
     */
    private Sync sync = new Sync();

    @Getter
    @Setter
    /**
     * Cấu hình JWT.
     */
    public static class Jwt {
        private String accessSecret;
        private String refreshSecret;
        private long accessExpirationMs = 900_000L;
        private long refreshExpirationMs = 604_800_000L;
    }

    @Getter
    @Setter
    /**
     * Cấu hình OpenAlex.
     */
    public static class OpenAlex {
        private String baseUrl = "https://api.openalex.org";
        private String mailto;
        private int perPage = 50;
        private String apiKey;
    }



    @Getter
    @Setter
    /**
     * Cấu hình Gemini AI.
     */
    public static class Gemini {
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-2.0-flash";
        private int maxOutputTokens = 2048;
        private int readTimeoutMs = 60_000;
    }

    @Getter
    @Setter
    /**
     * Cấu hình sync.
     */
    public static class Sync {
        private String cron = "0 0 2 * * *";
        private boolean onStartup = true;
        private int minKeywordPapers = 5;
        private int trendingThresholdPercent = 15;
        private int trendingConsecutiveMonths = 3;
        /** OpenAlex search terms — multiple queries broaden topic coverage for trends. */
        private List<String> searchQueries = new ArrayList<>(List.of(
                "computer science",
                "machine learning",
                "artificial intelligence",
                "data science"));
        private int maxPages = 5;
        /** Cap papers saved per sync run. */
        private int maxPapersPerRun = 1000;
        /** Only ingest works published on or after this date (ISO yyyy-MM-dd). */
        private String fromPublicationDate = "2026-01-01";
        /** Papers committed per DB transaction during ingest (higher = faster, more memory). */
        private int ingestBatchSize = 25;
        /** Re-fetch metadata for rows missing publication date after ingest. */
        private boolean enrichOnSync = false;
        /** Max papers to enrich when enrich-on-sync is enabled. */
        private int enrichBatchSize = 20;
        /** Pause between enrich HTTP calls (ms). */
        private int enrichDelayMs = 50;
        /** Mark RUNNING syncs as failed after this many minutes. */
        private int staleSyncMinutes = 10;
        /** HTTP connect timeout for OpenAlex (ms). */
        private int httpConnectTimeoutMs = 10_000;
        /** HTTP read timeout for OpenAlex (ms). */
        private int httpReadTimeoutMs = 30_000;
        /** Retries per OpenAlex HTTP call (transient failures). */
        private int openAlexRetryAttempts = 3;
        /** BR-55: tối đa keyword follow / user. */
        private int maxFollowKeywordsPerUser = 20;
        /** BR-56: tối đa journal follow / user. */
        private int maxFollowJournalsPerUser = 10;
        /** Tối đa author follow / user. */
        private int maxFollowAuthorsPerUser = 20;
        /** BR-57: tối đa bài lưu (bookmark) / user (tất cả collections). */
        private int maxBookmarkPapersPerUser = 200;
        /** BR-50: ngưỡng % tăng trưởng một tháng để gắn nhãn Anomaly. */
        private int anomalyThresholdPercent = 300;
        /** BR-97: số ngày pending review trước khi expired. */
        private int pendingReviewExpiryDays = 30;
        /** Sau sync/recalculate: backfill bao nhiêu tháng trend (0 = tắt). */
        private int trendBackfillMonths = 12;
        /** Number of overlap days for safer incremental sync. */
        private int overlapDays = 7;
        /** Flag to enable or disable early stopping during sync runs. */
        private boolean earlyStoppingEnabled = true;
        /** Dừng crawl keyword khi N trang liên tiếp không có paper mới nào. */
        private int earlyStopConsecutiveEmptyPages = 3;
        /** Cap keywords linked per paper during ingest. 0 = no limit. */
        private int maxKeywordsPerPaper = 10;
        /** Domains to allow when linking keywords to papers (empty list = allow all). Configured in application.yml. */
        private List<String> allowedKeywordDomains = new ArrayList<>();

        /** Cron cho job dự báo hot topic — mặc định 4AM ngày 1 hàng tháng. */
        private String forecastCron = "0 0 4 1 * *";
        /** Số tháng lịch sử tối thiểu để tính forecast. */
        private int forecastMinMonths = 6;
        /** Số tháng lịch sử tối đa dùng cho hồi quy / biểu đồ. */
        private int forecastHistoryWindow = 12;
        /** Số tháng dự báo về phía trước. */
        private int forecastHorizon = 6;
        /** Số keyword tối đa lưu vào bảng forecast. */
        private int forecastMaxKeywords = 200;
        /** Trọng số slope trong sTPS (mặc định 0.5). */
        private double forecastWeightSlope = 0.5;
        /** Trọng số acceleration trong sTPS (mặc định 0.3). */
        private double forecastWeightAcc = 0.3;
        /** Trọng số volume trong sTPS (mặc định 0.2). */
        private double forecastWeightVolume = 0.2;
    }
}


