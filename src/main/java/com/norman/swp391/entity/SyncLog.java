package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.SyncStatus;
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

@Entity
@Table(name = "sync_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SyncStatus status;

    @Column(name = "papers_fetched", nullable = false)
    private int papersFetched;

    @Column(name = "api_calls")
    @Builder.Default
    private Integer apiCalls = 0;

    @Column(name = "pages_fetched")
    @Builder.Default
    private Integer pagesFetched = 0;

    @Column(name = "papers_inserted")
    @Builder.Default
    private Integer papersInserted = 0;

    @Column(name = "papers_skipped")
    @Builder.Default
    private Integer papersSkipped = 0;

    @Column(name = "early_stop_triggered")
    @Builder.Default
    private Boolean earlyStopTriggered = false;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_admin_id")
    private User triggeredByAdmin;
}