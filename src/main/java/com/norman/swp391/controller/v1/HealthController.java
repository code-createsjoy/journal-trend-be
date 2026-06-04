package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint kiểm tra trạng thái service.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * Trả về trạng thái UP của API.
     */
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "research-trend-api"));
    }
}


