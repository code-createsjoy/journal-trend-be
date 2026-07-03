package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.service.KeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API từ khóa và xu hướng (v1).
 */
@RestController
@RequestMapping("/api/v1/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    /**
     * Xử lý API listAll.
     */
    @GetMapping
    public ApiResponse<List<KeywordResponse>> listAll() {
        return ApiResponse.ok(keywordService.listAll());
    }

    /**
     * Xử lý API getTrending.
     */
    @GetMapping("/trending")
    public ApiResponse<List<TrendingKeywordResponse>> getTrending(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ApiResponse.ok(keywordService.getTrendingKeywords(year, month));
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<KeywordResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(keywordService.getById(id));
    }

    /**
     * Xử lý API getTrendChart.
     */
    @GetMapping("/{id}/trends")
    public ApiResponse<List<KeywordTrendResponse>> getTrendChart(
            @PathVariable Long id, @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(keywordService.getKeywordTrendChart(id, months));
    }
}
