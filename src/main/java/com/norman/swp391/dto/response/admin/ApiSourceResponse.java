package com.norman.swp391.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * DTO nguồn API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiSourceResponse {
    String name;
    String baseUrl;
    boolean enabled;
    String syncSchedule;
    String lastSyncAt;
    Double successRate;
}


