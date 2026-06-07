package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.auth.ChangePasswordRequest;
import com.norman.swp391.dto.request.auth.ForgotPasswordRequest;
import com.norman.swp391.dto.request.auth.LoginRequest;
import com.norman.swp391.dto.request.auth.RefreshTokenRequest;
import com.norman.swp391.dto.request.auth.RegisterRequest;
import com.norman.swp391.dto.request.auth.ResetPasswordRequest;
import com.norman.swp391.dto.request.auth.UpdateProfileRequest;
import com.norman.swp391.dto.response.auth.AuthResponse;
import com.norman.swp391.dto.response.auth.TokenResponse;
import com.norman.swp391.dto.response.auth.UserResponse;
import com.norman.swp391.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đăng ký, đăng nhập và quản lý hồ sơ (v1).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản mới.
     */
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Registration successful", authService.register(request));
    }

    /**
     * Đăng nhập và cấp token.
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * Làm mới access token.
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    /**
     * Đăng xuất.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request != null ? request : new RefreshTokenRequest());
        return ApiResponse.okMessage("Logged out successfully");
    }

    /**
     * Gửi email đặt lại mật khẩu.
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.okMessage("If the email exists, a reset link has been sent");
    }

    /**
     * Đặt lại mật khẩu.
     */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.okMessage("Password reset successful");
    }

    /**
     * Đổi mật khẩu.
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.okMessage("Password changed successfully");
    }

    /**
     * Cập nhật hồ sơ.
     */
    @PutMapping("/profile")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(request));
    }

    /**
     * Lấy user đang đăng nhập.
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser() {
        return ApiResponse.ok(authService.getCurrentUser());
    }
}


