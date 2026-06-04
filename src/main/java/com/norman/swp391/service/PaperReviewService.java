package com.norman.swp391.service;

import com.norman.swp391.dto.request.admin.PaperReviewOverrideRequest;
import com.norman.swp391.dto.response.admin.PaperReviewResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface PaperReviewService {

    /** BR-10 + BR-17: cập nhật bài đã tồn tại, phát hiện conflict. */
    void applyIncomingMetadata(Paper paper, ExternalPaperMetadata incoming, String source);

    PageResponse<PaperReviewResponse> listByReviewStatus(
            PaperReviewStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    PaperReviewResponse accept(Long paperId, String note);

    PaperReviewResponse override(Long paperId, PaperReviewOverrideRequest request);

    void expireStalePendingReviews();

    long countByReviewStatus(PaperReviewStatus status);
}
