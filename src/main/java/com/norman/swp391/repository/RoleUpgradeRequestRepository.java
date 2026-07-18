package com.norman.swp391.repository;

import com.norman.swp391.entity.RoleUpgradeRequest;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Kho truy cập yêu cầu xin đổi role. */
public interface RoleUpgradeRequestRepository extends JpaRepository<RoleUpgradeRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, RoleRequestStatus status);

    /** Đơn PENDING hiện tại của user (nếu có) — dùng để hiện trạng thái ở Profile. */
    Optional<RoleUpgradeRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, RoleRequestStatus status);

    /**
     * Khóa pessimistic khi đọc theo id — ngăn 2 admin cùng duyệt/từ chối 1 đơn đồng thời
     * (cùng pattern với SyncLogRepository.findByStatusForUpdate).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoleUpgradeRequest r JOIN FETCH r.user WHERE r.id = :id")
    Optional<RoleUpgradeRequest> findByIdForUpdate(@Param("id") Long id);

    /** JOIN FETCH user/reviewedBy để tránh N+1 khi map sang response cho từng dòng trong danh sách. */
    @Query("""
            SELECT r FROM RoleUpgradeRequest r
            JOIN FETCH r.user
            LEFT JOIN FETCH r.reviewedBy
            WHERE r.status = :status
            ORDER BY r.createdAt DESC
            """)
    Page<RoleUpgradeRequest> findByStatusOrderByCreatedAtDesc(
            @Param("status") RoleRequestStatus status, Pageable pageable);

    @Query("""
            SELECT r FROM RoleUpgradeRequest r
            JOIN FETCH r.user
            LEFT JOIN FETCH r.reviewedBy
            ORDER BY r.createdAt DESC
            """)
    Page<RoleUpgradeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Xóa đơn còn PENDING quá hạn (chưa ai duyệt sau N ngày). */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RoleUpgradeRequest r WHERE r.status = com.norman.swp391.entity.enums.RoleRequestStatus.PENDING AND r.createdAt < :threshold")
    int deletePendingCreatedBefore(@Param("threshold") LocalDateTime threshold);

    /** Xóa đơn đã APPROVED/REJECTED quá hạn lưu trữ (sau N ngày kể từ lúc duyệt/từ chối). */
    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM RoleUpgradeRequest r
            WHERE r.status <> com.norman.swp391.entity.enums.RoleRequestStatus.PENDING
              AND r.reviewedAt < :threshold
            """)
    int deleteReviewedBefore(@Param("threshold") LocalDateTime threshold);
}
