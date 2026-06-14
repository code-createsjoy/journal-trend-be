package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.dashboard.DashboardSummaryResponse;
import com.norman.swp391.dto.response.dashboard.KeywordChartResponse;
import com.norman.swp391.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getSummary(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        return ApiResponse.ok(dashboardService.getDashboardSummary(isAdmin));
    }

    @GetMapping("/keyword-chart")
    public ApiResponse<KeywordChartResponse> getKeywordChart(@RequestParam Long keywordId) {
        return ApiResponse.ok(dashboardService.getKeywordChartData(keywordId));
    }
}
