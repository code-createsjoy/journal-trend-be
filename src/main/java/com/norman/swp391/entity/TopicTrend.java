package com.norman.swp391.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "topic_trends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "trend_year", "trend_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "trend_year", nullable = false)
    private int year;

    @Column(name = "trend_month", nullable = false)
    private int month;

    @Column(name = "paper_count", nullable = false)
    private int paperCount;

    @Column(name = "trend_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal trendScore;

    /** BR-50: tăng trưởng > 300% trong một tháng. */
    @Column(name = "anomaly_flag", nullable = false)
    @Builder.Default
    private boolean anomalyFlag = false;

    @Column(name = "anomaly_detected_at")
    private LocalDateTime anomalyDetectedAt;
}
