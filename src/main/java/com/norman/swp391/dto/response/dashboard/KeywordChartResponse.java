package com.norman.swp391.dto.response.dashboard;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordChartResponse {
    private Long keywordId;
    private String keyword;
    private List<KeywordChartPointDto> history;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeywordChartPointDto {
        private int year;
        private int month;
        private int paperCount;
        private BigDecimal trendScore;
    }
}
