package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.service.KeywordTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    /**
     * Chủ đề xu hướng.
     */
    public ApiResponse<List<TrendingTopicResponse>> getTrendingTopics() {
        return ApiResponse.ok(keywordTrendService.findTrendingTopics());
    }
}


