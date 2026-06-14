package com.norman.swp391.dto.response.admin;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Số liệu minh bạch cho báo cáo / demo trend. */
@Value
@Builder
public class TrendDemoStatsResponse {
    long activePapers;
    long papersWithKeywords;
    long totalKeywords;
    long keywordsWithMinPapers;
    long keywordTrendRowsCurrentMonth;
    long keywordTrendRowsWithScoreGe15;
    long officialTrendingKeywords;
    long topKeywordsCurrentMonth;
    int minKeywordPapers;
    int trendingThresholdPercent;
    int consecutiveMonthsRequired;
    int trendBackfillMonths;
    String formulaMom;
    String formulaOfficialTrending;
    List<KeywordMonthSample> topKeywordsByPaperCount;

    @Value
    @Builder
    public static class KeywordMonthSample {
        Long keywordId;
        String term;
        long totalPapers;
        Double currentMonthTrendScore;
    }
}
