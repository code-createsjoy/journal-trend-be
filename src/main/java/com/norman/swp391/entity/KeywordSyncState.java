package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.KeywordSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks crawler progress for each keyword + source combination during
 * historical dataset collection. Enables resume from the exact page
 * where synchronization previously stopped.
 */
@Entity
@Table(name = "keyword_sync_states",
        uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "source_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String keyword;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "last_page", nullable = false)
    @Builder.Default
    private int lastPage = 0;

    @Column(name = "last_publication_date")
    private LocalDate lastPublicationDate;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KeywordSyncStatus status = KeywordSyncStatus.IN_PROGRESS;
}
