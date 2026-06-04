package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.entity.enums.UserRole;

import java.util.List;

/**
 * Dịch vụ super admin (gán / thu hồi quyền admin).
 */
public interface SuperAdminService {

/**
 * Xử lý nghiệp vụ: grantAdmin.
 */
    UserAdminResponse grantAdmin(Long userId);

/**
 * Xử lý nghiệp vụ: revokeAdmin.
 */
    UserAdminResponse revokeAdmin(Long userId);

/**
 * Cập nhật vai trò người dùng.
 */
    UserAdminResponse updateRole(Long userId, UserRole role);

/**
 * Danh sách: listAdmins.
 */
    List<UserAdminResponse> listAdmins();
}
