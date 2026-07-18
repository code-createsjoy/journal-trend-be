package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.role.RoleUpgradeRequestCreateRequest;
import com.norman.swp391.dto.response.role.RoleUpgradeRequestResponse;
import com.norman.swp391.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tự phục vụ cho user hiện tại (v1).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final RoleManagementService roleManagementService;

    /**
     * Nộp đơn xin đổi role (chỉ STUDENT/RESEARCHER/LECTURER).
     */
    @PostMapping("/me/role-request")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RoleUpgradeRequestResponse> submitRoleRequest(
            @Valid @RequestBody RoleUpgradeRequestCreateRequest request) {
        return ApiResponse.ok("Role upgrade request submitted", roleManagementService.submitRequest(request));
    }

    /**
     * Đơn xin đổi role đang PENDING của user hiện tại (null nếu không có) — FE dùng để hiện trạng thái ở Profile.
     */
    @GetMapping("/me/role-request")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RoleUpgradeRequestResponse> getMyRoleRequest() {
        return ApiResponse.ok(roleManagementService.getMyPendingRequest());
    }
}
