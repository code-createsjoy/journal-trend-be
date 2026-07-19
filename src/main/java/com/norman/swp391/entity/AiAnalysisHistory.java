package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.AiAnalysisType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

/** Lịch sử 1 lần phân tích xu hướng bằng AI (Groq) của user. */
@Entity
@Table(name = "ai_analysis_histories",
        indexes = @Index(name = "idx_ai_history_user_date", columnList = "user_id, created_at DESC"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 50)
    private AiAnalysisType analysisType;

    /** JSON array chuỗi tên keyword đã phân tích, VD: ["AI","Machine Learning"]. */
    @Nationalized
    @Column(name = "target_keywords", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String targetKeywords;

    /** GROWING, STABLE, MIXED, hoặc DECLINING — tùy loại phân tích. */
    @Column(name = "overall_verdict", length = 50)
    private String overallVerdict;

    /** Toàn bộ response AI gốc (AiTrendAnalysisResponse hoặc AiTopTrendsAnalysisResponse), lưu dạng JSON. */
    @Nationalized
    @Column(name = "response_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
