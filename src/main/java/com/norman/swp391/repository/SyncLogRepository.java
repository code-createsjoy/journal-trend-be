package com.norman.swp391.repository;

import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.enums.SyncStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kho truy cập nhật ký đồng bộ.
 */
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

/**
 * Tìm kiếm: findFirstByOrderByStartedAtDesc.
 */
    SyncLog findFirstByOrderByStartedAtDesc();

/**
 * Tìm kiếm: findFirstByStatusOrderByStartedAtDesc.
 */
    SyncLog findFirstByStatusOrderByStartedAtDesc(SyncStatus status);

    @Query("""
        SELECT s FROM SyncLog s
        LEFT JOIN FETCH s.triggeredByAdmin
        WHERE (:status IS NULL OR s.status = :status)
          AND (:from IS NULL OR s.startedAt >= :from)
          AND (:to IS NULL OR s.startedAt <= :to)
        ORDER BY s.startedAt DESC
        """)
    Page<SyncLog> filter(
            @Param("status") SyncStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
        SELECT s FROM SyncLog s
        LEFT JOIN FETCH s.triggeredByAdmin
        WHERE s.status = :status
        ORDER BY s.startedAt DESC
        """)
    List<SyncLog> findByStatusWithAdmin(@Param("status") SyncStatus status, Pageable pageable);

    /**
     * Pessimistic lock query — ngăn 2 request đồng thời tạo 2 sync RUNNING.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM SyncLog s
        WHERE s.status = :status
        ORDER BY s.startedAt DESC
        """)
    List<SyncLog> findByStatusForUpdate(@Param("status") SyncStatus status, Pageable pageable);

    @Query("""
        SELECT s FROM SyncLog s
        LEFT JOIN FETCH s.triggeredByAdmin
        ORDER BY s.startedAt DESC
        """)
    /**
     * Tìm kiếm: findRecentWithAdmin.
     */
    List<SyncLog> findRecentWithAdmin(Pageable pageable);
}
