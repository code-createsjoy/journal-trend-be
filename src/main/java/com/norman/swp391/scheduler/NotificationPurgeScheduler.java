package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BR-70: tự động xóa notification cũ hơn ngưỡng retention (mặc định 90 ngày). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPurgeScheduler {

    private final NotificationService notificationService;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 45 3 * * *")
    /**
     * Chạy hằng ngày lúc 3h45 sáng để dọn các notification quá hạn lưu trữ (BR-70).
     */
    public void purgeOldNotifications() {
        if (!appProperties.isSchedulerEnabled()) {
            return;
        }
        log.info("Running notification purge job (BR-70)");
        notificationService.purgeOldNotifications();
    }
}
