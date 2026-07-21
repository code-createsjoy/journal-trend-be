package com.norman.swp391.dto.response.keyword;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trạng thái sẵn sàng của nút "Run Forecast" trên UI — suy ra từ sync_logs và
 * future_trend_forecasts, không lưu state riêng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastStatusResponse {
    private boolean canRunForecast;
    private LocalDateTime lastSyncedAt;        // null nếu chưa có sync nào nạp được bài mới
    private LocalDateTime lastForecastRunAt;   // null nếu chưa từng chạy forecast
    private String reasonCode;                 // null khi canRunForecast = true
    private String reasonIfDisabled;           // null khi canRunForecast = true
}
