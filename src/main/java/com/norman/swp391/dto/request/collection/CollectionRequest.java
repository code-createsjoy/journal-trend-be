package com.norman.swp391.dto.request.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu tạo/sửa collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionRequest {

    @NotBlank(message = "Collection name is required")
    @Size(max = 120, message = "Collection name must not exceed 120 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}


