package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.dto.response.admin.SystemStatsResponse;
import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.enums.SyncStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ quản trị hệ thống.
 */
public interface AdminService {

/**
 * Kích hoạt đồng bộ dữ liệu bài báo từ API ngoài.
 */
    SyncLogResponse triggerSync();

    /**
     * Danh sách nhật ký đồng bộ (lọc theo trạng thái/thời gian).
     */
    PageResponse<SyncLogResponse> listSyncLogs(
            SyncStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

/**
 * Tìm kiếm người dùng theo email hoặc tên.
 */
    PageResponse<UserAdminResponse> searchUsers(String q, Pageable pageable);

/**
 * Khóa tài khoản người dùng.
 */
    UserAdminResponse lockUser(Long userId);

/**
 * Mở khóa tài khoản người dùng.
 */
    UserAdminResponse unlockUser(Long userId);

/**
 * Xóa mềm bài báo (đổi trạng thái).
 */
    void softDeletePaper(Long paperId);

/**
 * Thống kê tổng quan hệ thống cho admin.
 */
    SystemStatsResponse getSystemStats();
}
