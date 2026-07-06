package com.norman.swp391.service;

import com.norman.swp391.dto.response.author.AuthorDetailResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.author.AuthorSpotlightResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ tác giả.
 */
public interface AuthorService {

/**
 * Lấy dữ liệu: getFeatured.
 */
    PageResponse<AuthorResponse> getFeatured(Pageable pageable);

/**
 * Lấy dữ liệu: getById.
 */
    AuthorResponse getById(Long id);

/**
 * Lấy dữ liệu: getPapersByAuthor.
 */
    PageResponse<PaperResponse> getPapersByAuthor(Long authorId, Pageable pageable);

    AuthorDetailResponse getAuthorDetail(Long authorId);

    /**
     * 3 tác giả nổi bật: nhiều paper nhất, citation cao nhất, h-index cao nhất.
     */
    AuthorSpotlightResponse getSpotlight();
}
