package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.UserMapper;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Triển khai dịch vụ super admin.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminServiceImpl implements SuperAdminService {

    private final UserRepository userRepository;

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: grantAdmin.
 */
    public UserAdminResponse grantAdmin(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("User is already super admin");
        }
        user.setRole(UserRole.ADMIN);
        return UserMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: revokeAdmin.
 */
    public UserAdminResponse revokeAdmin(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("Cannot revoke super admin role");
        }
        user.setRole(UserRole.RESEARCHER);
        return UserMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional
/**
 * Cập nhật vai trò người dùng.
 */
    public UserAdminResponse updateRole(Long userId, UserRole role) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new BadRequestException("Cannot change your own role");
        }
        if (user.getRole() == role) {
            throw new BadRequestException("User already has role: " + role.name());
        }
        user.setRole(role);
        return UserMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listAdmins.
 */
    public List<UserAdminResponse> listAdmins() {
        List<UserAdminResponse> admins = new ArrayList<>(UserMapper.toAdminResponseList(
                userRepository.findByRole(UserRole.ADMIN)));
        admins.addAll(UserMapper.toAdminResponseList(userRepository.findByRole(UserRole.SUPER_ADMIN)));
        return admins;
    }
}
