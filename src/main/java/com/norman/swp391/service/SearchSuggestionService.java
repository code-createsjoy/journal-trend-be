package com.norman.swp391.service;

import com.norman.swp391.dto.response.search.SearchSuggestionResponse;
import java.util.List;

/**
 * Service SearchSuggestionService — gợi ý autocomplete khi user đang gõ (BR-35).
 */
public interface SearchSuggestionService {

    /**
     * Trả về danh sách gợi ý (paper/author/keyword) khớp gần đúng với từ khóa đang gõ,
     * gộp từ cả 3 loại, giới hạn tổng số theo limit.
     */
    List<SearchSuggestionResponse> suggest(String q, int limit);
}
