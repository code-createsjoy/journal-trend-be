package com.norman.swp391.dto.response.topic;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chủ đề xu hướng.
 */
@Data
@Builder
public class TrendingTopicResponse {

    private Long topicId;
    private String topicName;
    private String description;
    private int paperCount;
    private BigDecimal trendScore;
    private int rank;
}


