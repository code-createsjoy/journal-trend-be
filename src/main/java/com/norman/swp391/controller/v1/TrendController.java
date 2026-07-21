package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.keyword.ForecastDetailResponse;
import com.norman.swp391.dto.response.keyword.ForecastListResponse;
import com.norman.swp391.dto.response.keyword.ForecastStatusResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.exception.ConflictException;
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
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(forecastService.getTopForecasts(limit, months));
    }

    /**
     * Chi tiết dự báo 1 keyword kèm lịch sử + N tháng tới (tham số months, 1-12).
     * Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/forecast/{keywordId}")
    public ApiResponse<ForecastDetailResponse> getForecastDetail(
            @PathVariable Long keywordId,
            @RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(forecastService.getForecastDetail(keywordId, months));
    }

    /**
     * Trạng thái nút "Run Forecast": đã có bài báo mới kể từ lần dự báo gần nhất hay chưa.
     * Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/forecast/status")
    public ApiResponse<ForecastStatusResponse> getForecastStatus() {
        return ApiResponse.ok("Fetch forecast status successfully", forecastService.getForecastStatus());
    }

    /**
     * Chạy lại job dự báo hot topic theo yêu cầu (nút "Run Forecast" trên UI).
     * Chỉ chạy được khi có bài báo mới kể từ lần dự báo gần nhất — tránh tính lại vô ích
     * trên cùng một tập dữ liệu. Student không có quyền truy cập.
     */
    @PreAuthorize("hasAnyRole('LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/forecast/run")
    public ApiResponse<List<ForecastListResponse>> runForecast(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "6") int months) {
        // Gọi qua forecastService (không gộp vào 1 method của service) để mọi lời gọi đều đi
        // qua Spring proxy — tránh bẫy self-invocation làm @Transactional mất tác dụng.
        ForecastStatusResponse status = forecastService.getForecastStatus();
        if (!status.isCanRunForecast()) {
            throw new ConflictException(status.getReasonIfDisabled());
        }
        forecastService.runForecastJob();
        return ApiResponse.ok("Forecast recalculated", forecastService.getTopForecasts(limit, months));
    }
}
