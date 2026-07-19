package com.norman.swp391.dto.response.ai;

import com.norman.swp391.entity.enums.AiAnalysisType;
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
public class AiAnalysisHistorySummaryResponse {
    private Long id;
    private AiAnalysisType analysisType;
    private List<String> targetKeywords;
    private String overallVerdict;
    private LocalDateTime createdAt;
}
