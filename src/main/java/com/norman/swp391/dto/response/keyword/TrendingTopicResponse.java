package com.norman.swp391.dto.response.keyword;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingTopicResponse {
    private Long topicId;
    private String topicName;
    private String description;
    private int paperCount; // representing number of trending keywords in domain
    private BigDecimal trendScore;
    private int rank;
}
