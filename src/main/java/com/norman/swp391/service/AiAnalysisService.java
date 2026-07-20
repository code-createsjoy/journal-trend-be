package com.norman.swp391.service;

import com.norman.swp391.dto.request.ai.AiCollectionAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiCollectionAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;

public interface AiAnalysisService {
    AiTrendAnalysisResponse analyzeTrend(AiTrendAnalysisRequest request);
    AiTopTrendsAnalysisResponse analyzeTopTrends(AiTopTrendsAnalysisRequest request);

    /**
     * Phân tích AI cho paper trong 1 collection của user hiện tại (dựa trên
     * metadata, không full text). Nếu request.paperIds có giá trị, chỉ phân
     * tích đúng các paper đó (phải thuộc collection); nếu không, tự lấy các
     * paper lưu gần nhất tối đa app.sync.max-papers-for-collection-analysis.
     */
    AiCollectionAnalysisResponse analyzeCollection(Long collectionId, AiCollectionAnalysisRequest request);
}
