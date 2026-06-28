package com.norman.swp391.dto.response.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTrendAnalysisResponse {
    private String keyword;
    /** GROWING, STABLE, or DECLINING */
    private String verdict;
    /** 0–100: likelihood this research field is worth pursuing */
    private int feasibilityScore;
    private String analysis;
    private List<String> keyInsights;
    private String recommendation;
}
