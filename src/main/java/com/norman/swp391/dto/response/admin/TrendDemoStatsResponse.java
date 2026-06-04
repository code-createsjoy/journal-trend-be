package com.norman.swp391.dto.response.admin;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Số liệu minh bạch cho báo cáo / demo trend. */
@Value
@Builder
public class TrendDemoStatsResponse {
    long activePapers;
    long papersWithTopics;
    long totalTopics;
    long topicsWithMinPapers;
    long topicTrendRowsCurrentMonth;
    long topicTrendRowsWithScoreGe15;
    long officialTrendingTopics;
    long topMonthlyTopics;
    int minTopicPapers;
    int trendingThresholdPercent;
    int consecutiveMonthsRequired;
    int trendBackfillMonths;
    String formulaMom;
    String formulaOfficialTrending;
    List<TopicMonthSample> topTopicsByPaperCount;

    @Value
    @Builder
    public static class TopicMonthSample {
        Long topicId;
        String topicName;
        long totalPapers;
        Double currentMonthTrendScore;
    }
}
