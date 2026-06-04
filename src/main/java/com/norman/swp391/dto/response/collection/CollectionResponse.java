package com.norman.swp391.dto.response.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionResponse {

    private Long id;
    private String name;
    private String description;
    private int paperCount;
    private LocalDateTime createdAt;
}


