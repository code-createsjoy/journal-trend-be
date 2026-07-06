package com.norman.swp391.service;

import com.norman.swp391.dto.response.search.SearchHistoryResponse;
import com.norman.swp391.entity.enums.SearchType;
import java.util.List;

/**
 * Service SearchHistoryService — ghi và đọc lịch sử tìm kiếm của user.
 */
public interface SearchHistoryService {

    /**
     * Ghi lại 1 lượt tìm kiếm. Nếu user đã tìm cùng query+searchType trước đó,
     * chỉ cập nhật lại thời gian (đưa lên đầu danh sách) thay vì tạo bản ghi mới.
     */
    void recordSearch(String query, SearchType searchType);

    /**
     * Lấy danh sách tìm kiếm gần đây nhất của user hiện tại (tối đa 10).
     */
    List<SearchHistoryResponse> getRecentSearches();
}
