package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.User;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.hibernate.Hibernate;

/**
 * Mapper SyncLogMapper.
 */
@UtilityClass
public class SyncLogMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static SyncLogResponse toResponse(SyncLog syncLog) {
        return toResponse(syncLog, null, null);
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static SyncLogResponse toResponse(SyncLog syncLog, Long adminIdOverride, String adminEmailOverride) {
        if (syncLog == null) {
            return null;
        }
        Long adminId = adminIdOverride;
        String adminEmail = adminEmailOverride;
        if (adminId == null && adminEmail == null) {
            User admin = syncLog.getTriggeredByAdmin();
            if (admin != null && Hibernate.isInitialized(admin)) {
                adminId = admin.getId();
                adminEmail = admin.getEmail();
            }
        }
        return SyncLogResponse.builder()
                .id(syncLog.getId())
                .startedAt(syncLog.getStartedAt())
                .finishedAt(syncLog.getFinishedAt())
                .status(syncLog.getStatus())
                .papersFetched(syncLog.getPapersFetched())
                .apiCalls(syncLog.getApiCalls() != null ? syncLog.getApiCalls() : 0)
                .pagesFetched(syncLog.getPagesFetched() != null ? syncLog.getPagesFetched() : 0)
                .papersInserted(syncLog.getPapersInserted() != null ? syncLog.getPapersInserted() : 0)
                .papersSkipped(syncLog.getPapersSkipped() != null ? syncLog.getPapersSkipped() : 0)
                .earlyStopTriggered(Boolean.TRUE.equals(syncLog.getEarlyStopTriggered()))
                .errorMessage(syncLog.getErrorMessage())
                .triggeredByAdminId(adminId)
                .triggeredByAdminEmail(adminEmail)
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<SyncLogResponse> toResponseList(List<SyncLog> syncLogs) {
        return syncLogs.stream().map(SyncLogMapper::toResponse).toList();
    }
}


