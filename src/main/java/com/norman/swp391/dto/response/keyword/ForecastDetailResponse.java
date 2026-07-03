package com.norman.swp391.dto.response.keyword;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết dự báo 1 keyword kèm lịch sử 12 tháng + dự báo 6 tháng tới.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastDetailResponse {
    private Long keywordId;
    private String term;
    private String domain;
    private BigDecimal potentialScore;
    private int predictedPapers6m;
    private BigDecimal predictedGrowthRate;
    private String forecastReason;   // ma ForecastCategory: EARLY_BOOM / BREAKOUT / STEADY

    // Lịch sử 12 tháng (nét liền trên chart)
    private List<ForecastMonthDto> historicalMonths;

    // Dự báo 6 tháng (nét đứt trên chart)
    private List<ForecastMonthDto> forecastMonths;
}
