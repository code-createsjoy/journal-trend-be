package com.norman.swp391.repository;

import com.norman.swp391.entity.RoleChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Kho truy cập nhật ký đổi role. */
public interface RoleChangeLogRepository extends JpaRepository<RoleChangeLog, Long> {

    /** JOIN FETCH operator/targetUser để tránh N+1 khi map sang response cho từng dòng trong danh sách. */
    @Query("""
            SELECT l FROM RoleChangeLog l
            JOIN FETCH l.operator
            JOIN FETCH l.targetUser
            WHERE l.targetUser.id = :targetUserId
            ORDER BY l.createdAt DESC
            """)
    Page<RoleChangeLog> findByTargetUserIdOrderByCreatedAtDesc(
            @Param("targetUserId") Long targetUserId, Pageable pageable);

    @Query("""
            SELECT l FROM RoleChangeLog l
            JOIN FETCH l.operator
            JOIN FETCH l.targetUser
            ORDER BY l.createdAt DESC
            """)
    Page<RoleChangeLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
