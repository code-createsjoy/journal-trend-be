package com.norman.swp391.service;

import com.norman.swp391.dto.request.auth.ChangePasswordRequest;
import com.norman.swp391.dto.request.auth.ForgotPasswordRequest;
import com.norman.swp391.dto.request.auth.LoginRequest;
import com.norman.swp391.dto.request.auth.RefreshTokenRequest;
import com.norman.swp391.dto.request.auth.RegisterRequest;
import com.norman.swp391.dto.request.auth.ResetPasswordRequest;
import com.norman.swp391.dto.request.auth.UpdateNotificationPreferencesRequest;
import com.norman.swp391.dto.request.auth.UpdateProfileRequest;
import com.norman.swp391.dto.response.auth.AuthResponse;
import com.norman.swp391.dto.response.auth.TokenResponse;
import com.norman.swp391.dto.response.auth.UserResponse;

/**
 * Dịch vụ xác thực và quản lý tài khoản.
 */
public interface AuthService {

/**
 * Đăng ký tài khoản mới.
 */
    UserResponse register(RegisterRequest request);

/**
 * Đăng nhập và trả về token.
 */
    AuthResponse login(LoginRequest request);

/**
 * Làm mới access token bằng refresh token.
 */
    TokenResponse refresh(RefreshTokenRequest request);

/**
 * Đăng xuất (thu hồi refresh token).
 */
    void logout(RefreshTokenRequest request);

/**
 * Gửi email chứa link đặt lại mật khẩu.
 */
    void forgotPassword(ForgotPasswordRequest request);

/**
 * Đặt lại mật khẩu bằng token từ email.
 */
    void resetPassword(ResetPasswordRequest request);

/**
 * Đổi mật khẩu khi đã đăng nhập.
 */
    void changePassword(ChangePasswordRequest request);

/**
 * Cập nhật thông tin hồ sơ người dùng.
 */
    UserResponse updateProfile(UpdateProfileRequest request);

/**
 * Cập nhật tuỳ chọn thông báo của người dùng.
 */
    UserResponse updateNotificationPreferences(UpdateNotificationPreferencesRequest request);

/**
 * Lấy thông tin user đang đăng nhập.
 */
    UserResponse getCurrentUser();
}
