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
        name = "publication_trends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"keyword_id", "trend_year", "trend_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trend_id")
    private Long trendId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Column(name = "trend_year", nullable = false)
    private int year;

    @Column(name = "trend_month", nullable = false)
    private int month;

    @Column(name = "paper_count", nullable = false)
    private int paperCount;

    @Column(name = "delta_percent", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaPercent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
