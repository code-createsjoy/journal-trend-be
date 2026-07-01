package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.SyncLogResponse;

/**
 * Service PaperSyncService.
 */
public interface PaperSyncService {

    /**
     * Bắt đầu đồng bộ nền; trả về ngay trạng thái RUNNING.
     */
    SyncLogResponse startSync(Long adminId);

    /**
     * Lấy trạng thái lần đồng bộ gần nhất.
     */
    SyncLogResponse getLatestSyncStatus();

    /**
     * Đánh dấu các sync RUNNING quá hạn là FAILED.
     */
    void resetStaleRunningSyncs();

    /**
     * Enrich citationCount + hIndex cho authors chưa có stats từ OpenAlex.
     * Trả về số authors đã được enrich.
     */
    int enrichAuthorStats(int limit);
}


