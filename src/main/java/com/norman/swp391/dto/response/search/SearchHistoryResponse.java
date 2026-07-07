package com.norman.swp391.dto.response.search;

import com.norman.swp391.entity.enums.SearchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin 1 lượt tìm kiếm gần đây.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistoryResponse {

    private String query;
    private SearchType searchType;
}
