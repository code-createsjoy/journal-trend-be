package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.RoleManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự động xóa đơn xin đổi role quá hạn: PENDING chưa ai duyệt sau N ngày (mặc định 30),
 * và đơn đã APPROVED/REJECTED quá hạn lưu trữ sau N ngày (mặc định 7).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleRequestPurgeScheduler {

    private final RoleManagementService roleManagementService;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 15 3 * * *")
    /**
     * Chạy hằng ngày lúc 3h15 sáng để dọn đơn xin đổi role quá hạn.
     */
    public void purgeExpiredRoleRequests() {
        if (!appProperties.isSchedulerEnabled()) {
            return;
        }
        log.info("Running role upgrade request purge job");
        roleManagementService.purgeExpiredRequests();
    }
}
