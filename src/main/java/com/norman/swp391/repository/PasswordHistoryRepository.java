package com.norman.swp391.repository;

import com.norman.swp391.entity.PasswordHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Kho truy cập lịch sử mật khẩu.
 */
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Lấy toàn bộ hash cũ của user, mới nhất trước (caller tự cắt lấy N-1).
     */
    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Xoá toàn bộ lịch sử mật khẩu của user.
     */
    void deleteByUserId(Long userId);
}
