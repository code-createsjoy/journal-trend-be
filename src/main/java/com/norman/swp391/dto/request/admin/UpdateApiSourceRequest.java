package com.norman.swp391.dto.request.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu cập nhật nguồn API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateApiSourceRequest {
    private Boolean enabled;
    private String syncSchedule;
}


