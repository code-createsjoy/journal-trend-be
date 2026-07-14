package com.norman.swp391.dto.response.search;

import com.norman.swp391.entity.enums.SearchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 gợi ý autocomplete khi user đang gõ (BR-35) — có thể là paper, author hoặc keyword.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchSuggestionResponse {

    private SearchType type;
    private String id;
    private String label;
    /** Thông tin phụ hiển thị mờ bên cạnh label (VD: domain của keyword, journal của paper). */
    private String subtitle;
}
