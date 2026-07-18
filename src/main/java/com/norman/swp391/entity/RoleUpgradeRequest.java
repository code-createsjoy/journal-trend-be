package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.RoleRequestRejectionReason;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import com.norman.swp391.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Yêu cầu xin đổi role (STUDENT/RESEARCHER/LECTURER) do user gửi, chờ Admin/Super Admin duyệt. */
@Entity
@Table(name = "role_upgrade_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 30)
    private UserRole requestedRole;

    /** Lý do xin đổi role — FE dùng Tiptap nên nội dung có thể là rich text/HTML, không chỉ plain text. */
    @Nationalized
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String reason;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoleRequestStatus status = RoleRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 30)
    private RoleRequestRejectionReason rejectionReason;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
