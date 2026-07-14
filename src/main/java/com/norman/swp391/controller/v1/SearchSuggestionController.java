package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.search.SearchSuggestionResponse;
import com.norman.swp391.service.SearchSuggestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API gợi ý autocomplete khi tìm kiếm (BR-35, v1).
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchSuggestionController {

    private final SearchSuggestionService searchSuggestionService;

    /** Gợi ý paper/author/keyword khớp gần đúng với từ khóa đang gõ, tối đa `limit` kết quả (mặc định 8). */
    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionResponse>> suggestions(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(searchSuggestionService.suggest(q, Math.min(limit, 20)));
    }
}
