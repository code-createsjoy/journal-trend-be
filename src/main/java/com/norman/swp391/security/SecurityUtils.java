package com.norman.swp391.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiện ích lấy người dùng từ SecurityContext.
 */
public final class SecurityUtils {

/**
 * Xử lý nghiệp vụ: SecurityUtils.
 */
    private SecurityUtils() {}

/**
 * Lấy dữ liệu: getCurrentUserId.
 */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return details.getId();
    }

/**
 * Lấy thông tin user đang đăng nhập.
 */
    public static CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return details;
    }
}
