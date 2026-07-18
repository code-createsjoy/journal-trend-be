package com.norman.swp391.service;

import com.norman.swp391.dto.request.role.RoleRequestApproveRequest;
import com.norman.swp391.dto.request.role.RoleRequestRejectRequest;
import com.norman.swp391.dto.request.role.RoleUpgradeRequestCreateRequest;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.role.RoleChangeLogResponse;
import com.norman.swp391.dto.response.role.RoleUpgradeRequestResponse;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import org.springframework.data.domain.Pageable;

/** Xử lý nghiệp vụ đơn xin đổi role: nộp đơn, duyệt, từ chối, xem nhật ký. */
public interface RoleManagementService {

    RoleUpgradeRequestResponse submitRequest(RoleUpgradeRequestCreateRequest request);

    /** Đơn PENDING hiện tại của user đang đăng nhập, null nếu không có. */
    RoleUpgradeRequestResponse getMyPendingRequest();

    PageResponse<RoleUpgradeRequestResponse> listRequests(RoleRequestStatus status, Pageable pageable);

    RoleUpgradeRequestResponse approve(Long requestId, RoleRequestApproveRequest request);

    RoleUpgradeRequestResponse reject(Long requestId, RoleRequestRejectRequest request);

    PageResponse<RoleChangeLogResponse> listChangeLogs(Long targetUserId, Pageable pageable);

    /** Xóa đơn PENDING quá hạn (mặc định 30 ngày) và đơn đã duyệt/từ chối quá hạn lưu trữ (mặc định 7 ngày). */
    void purgeExpiredRequests();
}
