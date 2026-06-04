package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.PaperMapper;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperTopicRepository;
import com.norman.swp391.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ bài báo.
 */
@Service
@RequiredArgsConstructor
public class PaperServiceImpl implements PaperService {

    private final PaperRepository paperRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final PaperAuthorRepository paperAuthorRepository;

    @Override
    @Transactional(readOnly = true)
/**
 * Tìm kiếm/lọc: search.
 */
    public PageResponse<PaperResponse> search(String q, Long topicId, Long authorId, Pageable pageable) {
        String query = (q != null && q.isBlank()) ? null : q;
        Page<Paper> page = paperRepository.search(
                PaperStatus.ACTIVE, PaperReviewStatus.NONE, query, topicId, authorId, pageable);
        return PageResponse.from(page, PaperMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getById.
 */
    public PaperDetailResponse getById(Long id) {
        Paper paper = paperRepository
                .findById(id)
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE && p.getReviewStatus() == PaperReviewStatus.NONE)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", id));
        return PaperMapper.toDetailResponseFromRelations(
                paper,
                paperTopicRepository.findByPaperId(id),
                paperAuthorRepository.findByPaperId(id));
    }
}
