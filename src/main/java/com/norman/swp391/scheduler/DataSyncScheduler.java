package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.PaperSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch sync dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncScheduler {

    private final PaperSyncService paperSyncService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.sync.cron:0 0 2 * * *}")
    /**
     * Sync theo cron.
     */
    public void runScheduledSync() {
        if (!appProperties.isSchedulerEnabled()) {
            log.debug("Scheduler disabled, skipping sync");
            return;
        }
        log.info("Starting scheduled paper sync");
        paperSyncService.startSync(null);
    }
}


