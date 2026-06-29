package com.norman.swp391.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;

@Data
public class AiTopTrendsAnalysisRequest {

    /** Danh sách keyword IDs cần phân tích (tối đa 10). Nếu để trống, hệ thống tự lấy Top 10 trending. */
    private List<Long> keywordIds;

    @Min(value = 1, message = "months must be at least 1")
    @Max(value = 24, message = "months must not exceed 24")
    private int months = 12;

    /** Base64-encoded ảnh biểu đồ đa đường (tùy chọn). */
    private String chartImageBase64;

    /** MIME type của ảnh. Mặc định image/png. */
    private String chartImageMimeType = "image/png";
}
