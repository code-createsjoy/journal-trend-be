package com.norman.swp391.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thống kê hệ thống cho quản trị.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatsResponse {

    private long totalUsers;
    private long activeUsers;
    private long lockedUsers;
    private long totalPapers;
    private long totalKeywords;
    private long totalAuthors;
    private long totalCollections;
    private long lastSyncPapersFetched;
}
