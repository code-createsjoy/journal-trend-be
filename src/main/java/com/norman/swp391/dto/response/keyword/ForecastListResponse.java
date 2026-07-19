package com.norman.swp391.dto.response.keyword;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng trong bảng xếp hạng dự báo hot topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastListResponse {
    private Long keywordId;
    private String term;
    private String domain;
    private BigDecimal potentialScore;       // sTPS 0-100
    private int predictedPapers;             // tổng bài dự báo trong forecastMonthsCount tháng
    private int forecastMonthsCount;         // số tháng dự báo (1-12)
    private BigDecimal predictedGrowthRate;  // % tăng trưởng
    private String forecastReason;           // ma ForecastCategory: EARLY_BOOM / BREAKOUT / STEADY
    private int currentPaperCount;           // số bài hiện tại
}
