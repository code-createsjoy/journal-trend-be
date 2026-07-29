package com.norman.swp391.dto.request.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Admin đổi cap số paper/lượt phân tích AI collection. */
@Data
public class UpdateAiCollectionAnalysisLimitRequest {

    @Min(value = 1, message = "maxPapers must be at least 1")
    @Max(value = 100, message = "maxPapers must not exceed 100")
    private int maxPapers;
}
