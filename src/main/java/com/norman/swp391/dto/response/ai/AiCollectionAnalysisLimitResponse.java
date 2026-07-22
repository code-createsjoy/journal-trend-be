package com.norman.swp391.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cap hiện tại số paper/lượt phân tích AI collection — FE dùng để không hard-code 30. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCollectionAnalysisLimitResponse {
    private int maxPapers;
}
