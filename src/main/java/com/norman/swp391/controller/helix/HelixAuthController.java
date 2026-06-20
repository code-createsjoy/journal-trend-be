package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixAuthSession;
import com.norman.swp391.dto.helix.HelixDtos.HelixLoginRequest;
import com.norman.swp391.dto.helix.HelixDtos.HelixRegisterRequest;
import com.norman.swp391.dto.helix.HelixDtos.HelixUpdateProfileRequest;
import com.norman.swp391.dto.request.auth.ForgotPasswordRequest;
import com.norman.swp391.dto.request.auth.ResetPasswordRequest;
import com.norman.swp391.dto.request.auth.RefreshTokenRequest;
import com.norman.swp391.dto.request.auth.UpdateProfileRequest;
import com.norman.swp391.service.AuthService;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * API xác thực cho frontend Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class HelixAuthController {

    private final HelixApiService helixApiService;
    private final AuthService authService;

    @PostMapping("/login")
/**
 * Đăng nhập và trả về token.
 */
    public HelixAuthSession login(@Valid @RequestBody HelixLoginRequest request) {
        return helixApiService.login(request);
    }

    @PostMapping("/register")
/**
 * Đăng ký tài khoản mới.
 */
    public HelixAuthSession register(@Valid @RequestBody HelixRegisterRequest request) {
        return helixApiService.register(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            authService.logout(request);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
/**
 * Xử lý nghiệp vụ: me.
 */
    public HelixAuthSession me(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : "";
        return new HelixAuthSession(helixApiService.currentUser(), token, null);
    }

    @PutMapping("/profile")
    public HelixAuthSession updateProfile(
            @Valid @RequestBody HelixUpdateProfileRequest request, HttpServletRequest httpRequest) {
        authService.updateProfile(UpdateProfileRequest.builder().fullName(request.name()).build());
        String bearer = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : "";
        return new HelixAuthSession(helixApiService.currentUser(), token, null);
    }
}
