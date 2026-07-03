package com.norman.swp391.service;

import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PublicationTrend;
import java.util.List;

public interface KeywordTrendService {
    void recalculateAll();
    void backfillHistoricalMonths(int monthsBack);
    List<Keyword> findTrendingKeywords(Integer year, Integer month);
    List<TrendingKeywordResponse> findTrendingKeywordResponses(Integer year, Integer month);
    List<TrendingKeywordResponse> findTopByTrendScore(int limit);
    PublicationTrend getCurrentMonthTrend(Long keywordId);
    List<TrendingTopicResponse> findTrendingTopics();
}
