package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
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
import com.norman.swp391.entity.PasswordResetToken;
import com.norman.swp391.entity.RefreshToken;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.UserMapper;
import com.norman.swp391.repository.PasswordResetTokenRepository;
import com.norman.swp391.repository.RefreshTokenRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.JwtTokenProvider;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.AuthService;
import com.norman.swp391.service.EmailService;
import com.norman.swp391.service.EmailVerificationService;
import com.norman.swp391.entity.EmailVerificationToken;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Triển khai dịch vụ xác thực.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;

    @Override
    @Transactional
/**
 * Đăng ký tài khoản mới.
 */
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        UserRole assignedRole = request.getRole();
        if (assignedRole == null || assignedRole == UserRole.ADMIN || assignedRole == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("Role must be STUDENT, LECTURER or RESEARCHER");
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(assignedRole)
                .status(UserStatus.ACTIVE)
                .enabled(false)
                .verified(false)
                .build();
        user = userRepository.save(user);
        
        EmailVerificationToken token = emailVerificationService.createVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), token.getToken());
        
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
/**
 * Đăng nhập và trả về token.
 */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getEmail()));
        
        if (!user.isVerified() && user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUPER_ADMIN) {
            throw new BadRequestException("EMAIL_NOT_VERIFIED");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Account is locked");
        }
        
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().trim(), request.getPassword()));
        
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
/**
 * Làm mới access token bằng refresh token.
 */
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new BadRequestException("Refresh token expired");
        }
        if (!jwtTokenProvider.validateRefreshToken(request.getRefreshToken())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new BadRequestException("Invalid refresh token");
        }
        User user = stored.getUser();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(request.getRefreshToken())
                .expiresIn(jwtTokenProvider.getAccessExpirationMs() / 1000)
                .build();
    }

    @Override
    @Transactional
/**
 * Đăng xuất (thu hồi refresh token).
 */
    public void logout(RefreshTokenRequest request) {
        if (!StringUtils.hasText(request.getRefreshToken())) {
            return;
        }
        refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Override
    @Transactional
/**
 * Gửi email chứa link đặt lại mật khẩu.
 */
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.getEmail().trim()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiresAt(LocalDateTime.now()
                            .plusMinutes(appProperties.getPasswordResetExpirationMinutes()))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        });
    }

    @Override
    @Transactional
/**
 * Đặt lại mật khẩu bằng token từ email.
 */
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenAndUsedFalse(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Override
    @Transactional
/**
 * Đổi mật khẩu khi đã đăng nhập.
 */
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional
/**
 * Cập nhật thông tin hồ sơ người dùng.
 */
    public UserResponse updateProfile(UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName().trim());
        }
        user = userRepository.save(user);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy thông tin user đang đăng nhập.
 */
    public UserResponse getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserMapper.toResponse(user);
    }

/**
 * Tạo/ghép dữ liệu: buildAuthResponse.
 */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshTokenValue = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole());
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshExpirationMs() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return UserMapper.toAuthResponse(accessToken, refreshTokenValue, user);
    }

}
