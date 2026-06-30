package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.keyword.ForecastDetailResponse;
import com.norman.swp391.dto.response.keyword.ForecastListResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.service.FutureTrendForecastService;
import com.norman.swp391.service.KeywordTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API xu hướng tổng hợp (v1).
 */
@RestController
@RequestMapping("/api/v1/trends")
@RequiredArgsConstructor
public class TrendController {

    private final KeywordTrendService keywordTrendService;
    private final FutureTrendForecastService forecastService;

    @GetMapping
    /**
     * Chủ đề xu hướng.
     */
    public ApiResponse<List<TrendingTopicResponse>> getTrendingTopics() {
        return ApiResponse.ok(keywordTrendService.findTrendingTopics());
    }

    /**
     * Danh sách top keyword có tiềm năng cao nhất (mặc định 10).
     * Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/forecast")
    public ApiResponse<List<ForecastListResponse>> getTopForecasts(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(forecastService.getTopForecasts(limit));
    }

    /**
     * Chi tiết dự báo 1 keyword kèm lịch sử + 6 tháng tới.
     * Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/forecast/{keywordId}")
    public ApiResponse<ForecastDetailResponse> getForecastDetail(
            @PathVariable Long keywordId) {
        return ApiResponse.ok(forecastService.getForecastDetail(keywordId));
    }

    /**
     * Chạy lại job dự báo hot topic theo yêu cầu (nút "Run Forecast" trên UI).
     * Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/forecast/run")
    public ApiResponse<List<ForecastListResponse>> runForecast(
            @RequestParam(defaultValue = "10") int limit) {
        forecastService.runForecastJob();
        return ApiResponse.ok("Forecast recalculated", forecastService.getTopForecasts(limit));
    }
}
