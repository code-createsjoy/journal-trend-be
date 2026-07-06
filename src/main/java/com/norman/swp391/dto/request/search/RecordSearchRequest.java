package com.norman.swp391.dto.request.search;

import com.norman.swp391.entity.enums.SearchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu ghi lại 1 lượt tìm kiếm của user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordSearchRequest {

    @NotBlank(message = "Query is required")
    private String query;

    @NotNull(message = "Search type is required")
    private SearchType searchType;
}
