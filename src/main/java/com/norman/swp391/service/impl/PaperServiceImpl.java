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
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperKeyword;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.util.Comparator;

/**
 * Triển khai dịch vụ bài báo.
 */
@Service
@RequiredArgsConstructor
public class PaperServiceImpl implements PaperService {

    private final PaperRepository paperRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperAuthorRepository paperAuthorRepository;

    @Override
    @Transactional(readOnly = true)
/**
 * Tìm kiếm/lọc: search.
 */
    public PageResponse<PaperDetailResponse> search(String q, String searchType, Long topicId, Long authorId, Integer fromYear, Integer toYear, String category, Integer minCitations, Long journalId, Pageable pageable) {
        String query = (q != null && q.isBlank()) ? null : q;
        String cat = (category != null && category.trim().equalsIgnoreCase("all")) ? null : category;
        String type = (searchType != null && searchType.isBlank()) ? null : searchType;
        
        Sort sanitizedSort = pageable.getSort();
        if (sanitizedSort.isSorted()) {
            sanitizedSort = Sort.by(sanitizedSort.stream()
                .map(order -> "trendScore".equals(order.getProperty()) 
                    ? new Sort.Order(order.getDirection(), "citationCount") 
                    : order)
                .toList());
        }
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sanitizedSort);

        Page<Paper> page = paperRepository.search(
                PaperStatus.ACTIVE,
                PaperReviewStatus.NONE,
                query,
                type,
                topicId,
                authorId,
                fromYear,
                toYear,
                cat,
                minCitations,
                journalId,
                safePageable);
        
        List<Paper> papers = page.getContent();
        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        
        Map<Long, List<PaperAuthor>> authorsByPaperId = loadAuthorsForPapers(paperIds);
        Map<Long, List<PaperKeyword>> keywordsByPaperId = loadKeywordsForPapers(paperIds);

        List<PaperDetailResponse> content = papers.stream().map(p ->
            PaperMapper.toDetailResponseFromRelations(p,
                keywordsByPaperId.getOrDefault(p.getId(), Collections.emptyList()),
                authorsByPaperId.getOrDefault(p.getId(), Collections.emptyList()))
        ).toList();

        return PageResponse.from(page, content);
    }

    private Map<Long, List<PaperAuthor>> loadAuthorsForPapers(List<Long> paperIds) {
        if (paperIds.isEmpty()) return Collections.emptyMap();
        return paperAuthorRepository.findByPaperIdInWithAuthor(paperIds).stream()
                .collect(Collectors.groupingBy(pa -> pa.getPaper().getId()));
    }

    private Map<Long, List<PaperKeyword>> loadKeywordsForPapers(List<Long> paperIds) {
        if (paperIds.isEmpty()) return Collections.emptyMap();
        return paperKeywordRepository.findByPaperIdInWithKeyword(paperIds).stream()
                .collect(Collectors.groupingBy(pk -> pk.getPaper().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<PaperResponse> searchByDomain(String domain) {
        java.util.List<Paper> papers = paperRepository.findByKeywordDomain(domain);
        return PaperMapper.toResponseList(papers);
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
                paperKeywordRepository.findByPaperId(id),
                paperAuthorRepository.findByPaperId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperDetailResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Paper> papers = paperRepository.findAllById(ids).stream()
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE && p.getReviewStatus() == PaperReviewStatus.NONE)
                .toList();
        if (papers.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        Map<Long, List<PaperAuthor>> authorsByPaperId = loadAuthorsForPapers(paperIds);
        Map<Long, List<PaperKeyword>> keywordsByPaperId = loadKeywordsForPapers(paperIds);

        return papers.stream().map(p ->
            PaperMapper.toDetailResponseFromRelations(p,
                keywordsByPaperId.getOrDefault(p.getId(), Collections.emptyList()),
                authorsByPaperId.getOrDefault(p.getId(), Collections.emptyList()))
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getAvailableYears() {
        return paperRepository.findAllPublicationDates(PaperStatus.ACTIVE)
                .stream()
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
