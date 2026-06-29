package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;
import com.norman.swp391.service.AiAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-powered trend analysis endpoint.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    /**
     * Phân tích xu hướng keyword bằng Gemini AI.
     * Yêu cầu đăng nhập. Gửi keywordId + months + ảnh biểu đồ base64 (tùy chọn).
     */
    @PostMapping("/analyze-trend")
    public ApiResponse<AiTrendAnalysisResponse> analyzeTrend(@Valid @RequestBody AiTrendAnalysisRequest request) {
        return ApiResponse.ok("AI analysis completed", aiAnalysisService.analyzeTrend(request));
    }

    @PostMapping("/analyze-top-trends")
    public ApiResponse<AiTopTrendsAnalysisResponse> analyzeTopTrends(@Valid @RequestBody AiTopTrendsAnalysisRequest request) {
        return ApiResponse.ok("AI top trends analysis completed", aiAnalysisService.analyzeTopTrends(request));
    }
}
