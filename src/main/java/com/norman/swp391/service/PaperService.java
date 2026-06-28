package com.norman.swp391.service;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Dịch vụ tra cứu bài báo.
 */
public interface PaperService {

/**
 * Tìm kiếm/lọc: search.
 */
    PageResponse<PaperDetailResponse> search(String q, String searchType, Long topicId, Long authorId, Integer fromYear, Integer toYear, String category, Integer minCitations, Long journalId, Pageable pageable);

    List<Integer> getAvailableYears();

/**
 * Lấy bài báo theo domain của keyword (topic).
 */
    java.util.List<PaperResponse> searchByDomain(String domain);

/**
 * Lấy dữ liệu: getById.
 */
    PaperDetailResponse getById(Long id);

    java.util.List<PaperDetailResponse> getByIds(java.util.List<Long> ids);
}
