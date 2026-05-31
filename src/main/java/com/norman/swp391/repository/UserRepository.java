package com.norman.swp391.repository;

import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập người dùng.
 */
public interface UserRepository extends JpaRepository<User, Long> {

/**
 * Tìm kiếm: findByEmailIgnoreCase.
 */
    Optional<User> findByEmailIgnoreCase(String email);

/**
 * Xử lý nghiệp vụ: existsByEmailIgnoreCase.
 */
    boolean existsByEmailIgnoreCase(String email);

/**
 * Xử lý nghiệp vụ: countByRole.
 */
    long countByRole(UserRole role);

/**
 * Xử lý nghiệp vụ: countByStatus.
 */
    long countByStatus(UserStatus status);

    @Query("SELECT u FROM User u WHERE (:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<User> search(@Param("q") String q, Pageable pageable);

/**
 * Tìm kiếm: findByRole.
 */
    List<User> findByRole(UserRole role);
}
