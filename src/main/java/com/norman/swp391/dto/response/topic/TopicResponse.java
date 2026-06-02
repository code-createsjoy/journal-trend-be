package com.norman.swp391.dto.response.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chủ đề.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicResponse {

    private Long id;
    private String name;
    private String description;
}


