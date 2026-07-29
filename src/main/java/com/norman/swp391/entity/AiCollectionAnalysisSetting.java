package com.norman.swp391.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cấu hình cap số paper/lượt phân tích AI collection — 1 dòng duy nhất (id=1), cho phép admin
 * chỉnh lúc chạy (không cần đổi application.yml + redeploy như app.sync.maxPapersForCollectionAnalysis).
 */
@Entity
@Table(name = "ai_collection_analysis_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCollectionAnalysisSetting {

    /** Luôn = 1L — bảng chỉ có đúng 1 dòng cấu hình. */
    @Id
    private Long id;

    @Column(name = "max_papers", nullable = false)
    private int maxPapers;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** userId admin đã đổi giá trị gần nhất, phục vụ audit — có thể null nếu là giá trị mặc định lúc seed. */
    @Column(name = "updated_by")
    private Long updatedBy;
}
