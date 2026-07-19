package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiAnalysisHistoryDetailResponse;
import com.norman.swp391.dto.response.ai.AiAnalysisHistorySummaryResponse;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.service.AiAnalysisHistoryService;
import com.norman.swp391.service.AiAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AiAnalysisHistoryService aiAnalysisHistoryService;

    /**
     * Phân tích xu hướng keyword bằng Groq AI.
     * Yêu cầu đăng nhập. Gửi keywordId + months.
     */
    @PostMapping("/analyze-trend")
    public ApiResponse<AiTrendAnalysisResponse> analyzeTrend(@Valid @RequestBody AiTrendAnalysisRequest request) {
        return ApiResponse.ok("AI analysis completed", aiAnalysisService.analyzeTrend(request));
    }

    /** So sánh AI nhiều keyword trending cùng lúc, chỉ ra keyword nào đáng theo đuổi nhất. */
    @PostMapping("/analyze-top-trends")
    public ApiResponse<AiTopTrendsAnalysisResponse> analyzeTopTrends(@Valid @RequestBody AiTopTrendsAnalysisRequest request) {
        return ApiResponse.ok("AI top trends analysis completed", aiAnalysisService.analyzeTopTrends(request));
    }

    /** Danh sách lịch sử phân tích AI của user hiện tại, mới nhất trước. */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<AiAnalysisHistorySummaryResponse>> listHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(aiAnalysisHistoryService.listHistory(pageable));
    }

    /** Chi tiết 1 bản ghi lịch sử phân tích AI, kèm response AI gốc. */
    @GetMapping("/history/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiAnalysisHistoryDetailResponse> getHistoryDetail(@PathVariable Long id) {
        return ApiResponse.ok(aiAnalysisHistoryService.getHistoryDetail(id));
    }

    /** Xóa 1 bản ghi lịch sử phân tích AI. */
    @DeleteMapping("/history/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteHistory(@PathVariable Long id) {
        aiAnalysisHistoryService.deleteHistory(id);
        return ApiResponse.okMessage("Analysis history deleted");
    }

    /** Xóa toàn bộ lịch sử phân tích AI của user hiện tại. */
    @DeleteMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteAllHistory() {
        aiAnalysisHistoryService.deleteAllHistory();
        return ApiResponse.okMessage("All analysis history deleted");
    }
}
