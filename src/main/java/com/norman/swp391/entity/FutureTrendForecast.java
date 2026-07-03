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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả dự báo hot topic 6 tháng tới cho mỗi keyword.
 * Tính lại định kỳ bởi {@code FutureTrendForecastScheduler}.
 */
@Entity
@Table(name = "future_trend_forecasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FutureTrendForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Column(name = "potential_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal potentialScore;

    @Column(name = "predicted_papers_6m", nullable = false)
    private int predictedPapers6m;

    @Column(name = "predicted_growth_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedGrowthRate;

    // Ma ForecastCategory (EARLY_BOOM / BREAKOUT / STEADY) — ASCII, an toan voi cot khong Unicode.
    @Column(name = "forecast_reason", nullable = false, length = 30)
    private String forecastReason;

    // JSON string: [{"year":2026,"month":7,"paperCount":52}, ...]
    @Column(name = "forecast_months_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String forecastMonthsJson;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
