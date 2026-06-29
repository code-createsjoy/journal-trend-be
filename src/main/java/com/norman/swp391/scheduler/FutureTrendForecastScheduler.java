package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.FutureTrendForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch chạy job dự báo hot topic — mặc định 4:00 AM ngày mùng 1 hàng tháng
 * (sau DataSyncScheduler 2AM).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FutureTrendForecastScheduler {

    private final FutureTrendForecastService forecastService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.sync.forecast-cron:0 0 4 1 * *}")
    public void runMonthlyForecast() {
        if (!appProperties.isSchedulerEnabled()) {
            log.debug("[ForecastScheduler] Scheduler disabled, bỏ qua");
            return;
        }
        log.info("[ForecastScheduler] Bắt đầu job dự báo hot topic tháng mới");
        try {
            forecastService.runForecastJob();
        } catch (Exception e) {
            log.error("[ForecastScheduler] Job thất bại: {}", e.getMessage(), e);
        }
    }
}
