package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.PaperReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BR-97: tự động expire pending review quá hạn. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperReviewMaintenanceScheduler {

    private final PaperReviewService paperReviewService;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 30 3 * * *")
    public void expireStalePendingReviews() {
        if (!appProperties.isSchedulerEnabled()) {
            return;
        }
        log.info("Running pending review expiry job (BR-97)");
        paperReviewService.expireStalePendingReviews();
    }
}
