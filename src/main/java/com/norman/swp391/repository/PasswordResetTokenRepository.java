package com.norman.swp391.repository;

import com.norman.swp391.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Kho truy cập token đặt lại mật khẩu.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

/**
 * Tìm kiếm: findByTokenAndUsedFalse.
 */
    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);
}
