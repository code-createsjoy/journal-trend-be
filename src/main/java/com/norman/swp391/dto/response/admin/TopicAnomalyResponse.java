package com.norman.swp391.dto.response.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/** BR-50 / BR-106: topic có tăng trưởng bất thường. */
@Data
@Builder
public class TopicAnomalyResponse {
    private Long topicId;
    private String topicName;
    private BigDecimal trendScore;
    private int paperCount;
    private int year;
    private int month;
    private LocalDateTime detectedAt;
}
