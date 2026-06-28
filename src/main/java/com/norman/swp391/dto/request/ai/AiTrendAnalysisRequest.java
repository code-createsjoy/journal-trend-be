package com.norman.swp391.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiTrendAnalysisRequest {
    @NotNull(message = "keywordId is required")
    private Long keywordId;

    @Min(value = 1, message = "months must be at least 1")
    @Max(value = 24, message = "months must not exceed 24")
    private int months = 12;

    /** Base64-encoded chart image (optional). Max ~3MB raw image. */
    private String chartImageBase64;

    /** MIME type of the image. Defaults to image/png. */
    private String chartImageMimeType = "image/png";
}
