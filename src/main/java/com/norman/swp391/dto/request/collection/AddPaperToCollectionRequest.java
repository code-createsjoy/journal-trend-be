package com.norman.swp391.dto.request.collection;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu thêm bài vào collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddPaperToCollectionRequest {

    @NotNull(message = "Paper id is required")
    private Long paperId;
}


