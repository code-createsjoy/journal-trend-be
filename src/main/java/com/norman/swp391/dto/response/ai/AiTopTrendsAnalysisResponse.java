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
public class AiTopTrendsAnalysisResponse {
    /** GROWING, STABLE, hoặc MIXED */
    private String overallVerdict;
    /** Danh sách keyword IDs đã được phân tích */
    private List<String> analyzedKeywords;
    /** Các từ khóa tăng trưởng mạnh nhất trong nhóm */
    private List<String> topGrowingKeywords;
    /** Phân tích chi tiết so sánh các đường xu hướng bằng tiếng Việt */
    private String analysis;
    /** Các điểm nhận xét cốt lõi (tối thiểu 3 ý) */
    private List<String> keyInsights;
    /** Khuyến nghị hướng nghiên cứu tối ưu cho nhà khoa học */
    private String recommendation;
}
