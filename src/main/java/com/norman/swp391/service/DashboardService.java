package com.norman.swp391.service;

import com.norman.swp391.dto.response.dashboard.DashboardSummaryResponse;
import com.norman.swp391.dto.response.dashboard.KeywordChartResponse;

public interface DashboardService {
    DashboardSummaryResponse getDashboardSummary(boolean isAdmin);
    KeywordChartResponse getKeywordChartData(Long keywordId);
}
