package com.norman.swp391.dto.request.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu cập nhật tuỳ chọn thông báo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationPreferencesRequest {

    private boolean notifyKeywords;
    private boolean notifyAuthors;
    private boolean notifyJournals;
    private boolean notifyEmail;
}
