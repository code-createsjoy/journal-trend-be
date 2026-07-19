package com.norman.swp391.service;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.ai.AiAnalysisHistoryDetailResponse;
import com.norman.swp391.dto.response.ai.AiAnalysisHistorySummaryResponse;
import com.norman.swp391.entity.enums.AiAnalysisType;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** Service quản lý lịch sử phân tích xu hướng bằng AI của user. */
public interface AiAnalysisHistoryService {

    /**
     * Lưu 1 bản ghi lịch sử sau khi phân tích AI thành công. Không throw exception
     * ra ngoài — lỗi lưu lịch sử không được làm hỏng response AI chính.
     */
    void saveHistory(AiAnalysisType type, List<String> targetKeywords, String overallVerdict, Object rawResponse);

    PageResponse<AiAnalysisHistorySummaryResponse> listHistory(Pageable pageable);

    AiAnalysisHistoryDetailResponse getHistoryDetail(Long id);

    void deleteHistory(Long id);

    void deleteAllHistory();
}
