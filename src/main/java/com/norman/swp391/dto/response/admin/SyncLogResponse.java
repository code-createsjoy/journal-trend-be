package com.norman.swp391.dto.response.admin;

import com.norman.swp391.entity.enums.SyncStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO nhật ký sync.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogResponse {

    private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private SyncStatus status;
    private int papersFetched;
    private int apiCalls;
    private int pagesFetched;
    private int papersInserted;
    private int papersSkipped;
    private boolean earlyStopTriggered;
    private String errorMessage;
    private Long triggeredByAdminId;
    private String triggeredByAdminEmail;
}


