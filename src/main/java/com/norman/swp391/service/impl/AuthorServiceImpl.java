package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.author.AuthorDetailResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.AuthorMapper;
import com.norman.swp391.mapper.PaperMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.service.AuthorService;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ tác giả.
 */
@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperRepository paperRepository;
    private final PaperKeywordRepository paperKeywordRepository;

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getFeatured.
 */
    public PageResponse<AuthorResponse> getFeatured(Pageable pageable) {
        Page<com.norman.swp391.entity.Author> page = authorRepository.findFeatured(pageable);
        return PageResponse.from(page, AuthorMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getById.
 */
    public AuthorResponse getById(Long id) {
        return AuthorMapper.toResponse(authorRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id)));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getPapersByAuthor.
 */
    public PageResponse<PaperResponse> getPapersByAuthor(Long authorId, Pageable pageable) {
        authorRepository.findById(authorId).orElseThrow(() -> new ResourceNotFoundException("Author", authorId));
        List<Paper> papers = paperAuthorRepository.findByAuthorId(authorId).stream()
                .map(PaperAuthor::getPaper)
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE)
                .sorted(Comparator.comparingInt(Paper::getCitationCount).reversed())
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), papers.size());
        List<Paper> pageContent = start >= papers.size() ? List.of() : papers.subList(start, end);
        Page<Paper> page = new PageImpl<>(pageContent, pageable, papers.size());
        return PageResponse.from(page, PaperMapper.toResponseList(pageContent));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorDetailResponse getAuthorDetail(Long authorId) {
        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author", authorId));

        long totalPapers = paperAuthorRepository.countByAuthorId(authorId);

        List<Object[]> keywordRows = paperKeywordRepository.findTopKeywordsByAuthor(authorId, PageRequest.of(0, 5));
        List<String> topKeywords = keywordRows.stream()
                .map(row -> (String) row[0])
                .toList();

        List<Object[]> popularPaperRows = paperRepository.findPopularPapersByAuthor(authorId);
        List<PaperResponse> popularPapers = popularPaperRows.stream()
                .map(row -> {
                    Paper paper = (Paper) row[0];
                    return PaperMapper.toResponse(paper);
                })
                .toList();

        return AuthorDetailResponse.builder()
                .id(author.getId())
                .name(author.getName())
                .affiliation(author.getAffiliation())
                .citationCount(author.getCitationCount())
                .hIndex(author.getHIndex())
                .totalPapers(totalPapers)
                .topKeywords(topKeywords)
                .popularPapers(popularPapers)
                .build();
    }
}
