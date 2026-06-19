package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.admin.RoleUpdateRequest;
import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.service.SuperAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
/**
 * REST SuperAdminController.
 */
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    /**
     * Xử lý API grantAdmin.
     */
    @PostMapping("/users/{id}/grant-admin")
    public ApiResponse<UserAdminResponse> grantAdmin(@PathVariable Long id) {
        return ApiResponse.ok("Admin role granted", superAdminService.grantAdmin(id));
    }

    /**
     * Xử lý API revokeAdmin.
     */
    @PostMapping("/users/{id}/revoke-admin")
    public ApiResponse<UserAdminResponse> revokeAdmin(@PathVariable Long id) {
        return ApiResponse.ok("Admin role revoked", superAdminService.revokeAdmin(id));
    }

    /**
     * Cập nhật vai trò người dùng (STUDENT, LECTURER, RESEARCHER, ADMIN, SUPER_ADMIN).
     */
    @PutMapping("/users/{id}/role")
    public ApiResponse<UserAdminResponse> updateRole(
            @PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok("Role updated", superAdminService.updateRole(id, request.getRole()));
    }

    /**
     * Xử lý API listAdmins.
     */
    @GetMapping("/admins")
    public ApiResponse<List<UserAdminResponse>> listAdmins() {
        return ApiResponse.ok(superAdminService.listAdmins());
    }
}


