package com.norman.swp391.dto.response.keyword;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một điểm dữ liệu tháng (lịch sử hoặc dự báo) trong biểu đồ forecast.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastMonthDto {
    private int year;
    private int month;
    private int paperCount;
}
