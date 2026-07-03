package com.norman.swp391.service;

import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import java.util.List;

public interface KeywordService {
    List<KeywordResponse> listAll();
    KeywordResponse getById(Long id);
    List<TrendingKeywordResponse> getTrendingKeywords(Integer year, Integer month);
    List<KeywordTrendResponse> getKeywordTrendChart(Long keywordId, int months);
}
