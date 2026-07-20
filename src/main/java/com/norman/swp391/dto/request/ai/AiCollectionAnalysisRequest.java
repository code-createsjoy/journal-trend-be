package com.norman.swp391.dto.request.ai;

import java.util.List;
import lombok.Data;

@Data
public class AiCollectionAnalysisRequest {

    /**
     * Danh sách paperId người dùng chọn thủ công để phân tích (phải thuộc
     * collection). Nếu để trống, hệ thống tự lấy các paper lưu gần nhất, tối đa
     * app.sync.max-papers-for-collection-analysis.
     */
    private List<Long> paperIds;
}
