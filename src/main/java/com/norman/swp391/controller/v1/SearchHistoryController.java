package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.search.RecordSearchRequest;
import com.norman.swp391.dto.response.search.SearchHistoryResponse;
import com.norman.swp391.service.SearchHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API lịch sử tìm kiếm (v1).
 */
@RestController
@RequestMapping("/api/v1/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @PostMapping
    public ApiResponse<Void> record(@Valid @RequestBody RecordSearchRequest request) {
        searchHistoryService.recordSearch(request.getQuery(), request.getSearchType());
        return ApiResponse.okMessage("Search recorded");
    }

    @GetMapping("/recent")
    public ApiResponse<List<SearchHistoryResponse>> recent() {
        return ApiResponse.ok(searchHistoryService.getRecentSearches());
    }
}
