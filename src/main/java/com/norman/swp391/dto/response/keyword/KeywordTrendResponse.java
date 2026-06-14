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
public class KeywordTrendResponse {
    private Long trendId;
    private Long keywordId;
    private String term;
    private int year;
    private int month;
    private int paperCount;
    private BigDecimal deltaPercent;
}
