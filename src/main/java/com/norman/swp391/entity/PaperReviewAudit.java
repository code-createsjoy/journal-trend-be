package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.PaperReviewAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Audit log resolve pending review (BR-104). */
@Entity
@Table(name = "paper_review_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperReviewAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaperReviewAction action;

    @Column(length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
