package com.norman.swp391.dto.response.topic;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Biểu đồ xu hướng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicTrendResponse {

    private Long id;
    private Long topicId;
    private String topicName;
    private int year;
    private int month;
    private int paperCount;
    private BigDecimal trendScore;
}


