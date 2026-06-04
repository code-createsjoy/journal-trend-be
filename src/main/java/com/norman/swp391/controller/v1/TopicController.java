package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.dto.response.topic.TopicTrendResponse;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API chủ đề và xu hướng (v1).
 */
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    /**
     * Xử lý API listAll.
     */
    @GetMapping
    public ApiResponse<List<TopicResponse>> listAll() {
        return ApiResponse.ok(topicService.listAll());
    }

    /**
     * Xử lý API getTrending.
     */
    @GetMapping("/trending")
    public ApiResponse<List<TrendingTopicResponse>> getTrending() {
        return ApiResponse.ok(topicService.getTrendingTopics());
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<TopicResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(topicService.getById(id));
    }

    /**
     * Xử lý API getTrendChart.
     */
    @GetMapping("/{id}/trends")
    public ApiResponse<List<TopicTrendResponse>> getTrendChart(
            @PathVariable Long id, @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(topicService.getTopicTrendChart(id, months));
    }
}


