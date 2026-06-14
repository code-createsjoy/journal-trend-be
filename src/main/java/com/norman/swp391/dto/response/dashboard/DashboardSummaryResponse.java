package com.norman.swp391.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {
    private KpiCardsDto kpi;
    private List<TrendingTopicDto> trendingTopics;
    private List<TrendingKeywordDto> trendingKeywords;
    private List<RecentPublicationDto> recentPublications;
    private List<TopJournalDto> topJournals;
    private SyncMonitorDto syncMonitor;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KpiCardsDto {
        private long totalPapers;
        private long totalJournals;
        private long totalKeywords;
        private long trendingKeywordsCount;
        private long trendingTopicsCount;
        private String lastSyncStatus;
        private LocalDateTime lastSyncTime;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendingTopicDto {
        private Long topicId;
        private String topicName;
        private int trendingKeywordsCount;
        private BigDecimal averageTrendScore;
        private List<TopicKeywordDto> topKeywords;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicKeywordDto {
        private String term;
        private BigDecimal trendScore;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendingKeywordDto {
        private Long keywordId;
        private int rank;
        private String keyword;
        private String domain;
        private BigDecimal trendScore;
        private int paperCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentPublicationDto {
        private Long paperId;
        private String title;
        private String journal;
        private LocalDate publicationDate;
        private int citationCount;
        private List<String> topKeywords;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopJournalDto {
        private Long journalId;
        private String journalName;
        private long totalPapers;
        private BigDecimal impactFactor;
        private String domain;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncMonitorDto {
        private LocalDateTime lastSyncTime;
        private String syncStatus;
        private int papersSynced;
        private long durationSeconds;
        private String errorMessage;
    }
}
