package com.norman.swp391.repository;

import com.norman.swp391.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập refresh token.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

/**
 * Tìm kiếm: findByTokenAndRevokedFalse.
 */
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

/**
 * Tìm kiếm: findByUserIdAndRevokedFalse.
 */
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

/**
 * Xóa: deleteByUserId.
 */
    void deleteByUserId(Long userId);
}
