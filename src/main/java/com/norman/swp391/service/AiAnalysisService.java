package com.norman.swp391.service;

import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;

public interface AiAnalysisService {
    AiTrendAnalysisResponse analyzeTrend(AiTrendAnalysisRequest request);
    AiTopTrendsAnalysisResponse analyzeTopTrends(AiTopTrendsAnalysisRequest request);
}
