package com.norman.swp391.service;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ tra cứu bài báo.
 */
public interface PaperService {

/**
 * Tìm kiếm/lọc: search.
 */
    PageResponse<PaperResponse> search(String q, Long topicId, Long authorId, Pageable pageable);

/**
 * Lấy dữ liệu: getById.
 */
    PaperDetailResponse getById(Long id);
}
