package com.norman.swp391.dto.response.keyword;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendingKeywordResponse {
    private Long keywordId;
    private String term;
    private String domain;
    private int paperCount;
    private BigDecimal trendScore;
    private int rank;
}
