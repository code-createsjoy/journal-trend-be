package com.norman.swp391.dto.response.ai;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiteratureMatrixResponse {

    private int totalPapers;

    /** Số lượt còn lại trong ngày sau lượt gọi này. -1 = không giới hạn (ADMIN/SUPER_ADMIN). */
    private int quotaRemainingToday;

    private List<MatrixRow> matrixRows;

    private String markdownTable;

    private LocalDateTime generatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatrixRow {
        private Long paperId;
        private String title;
        private String authors;
        private Integer year;
        private String objective;
        private String methodology;
        private String dataset;
        private String keyResults;
        private String limitations;
    }
}
