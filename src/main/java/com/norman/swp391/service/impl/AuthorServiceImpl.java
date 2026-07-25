package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.author.AuthorDetailResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.author.AuthorSpotlightResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.AuthorMapper;
import com.norman.swp391.mapper.PaperMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.service.AuthorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
        Pageable unsorted = (pageable != null && pageable.isPaged())
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged();
        Page<Paper> page = paperAuthorRepository.findActivePapersByAuthorId(authorId, unsorted);
        return PageResponse.from(page, PaperMapper.toResponseList(page.getContent()));
    }

    /**
     * Lấy thông tin chi tiết 1 tác giả: tổng số paper, top keyword hay nghiên cứu,
     * và các paper nổi bật nhất của tác giả đó.
     */
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

    /**
     * 3 tác giả nổi bật: nhiều paper nhất, citation cao nhất, h-index cao nhất.
     * Mỗi field có thể null nếu chưa đủ dữ liệu phù hợp.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthorSpotlightResponse getSpotlight() {
        AuthorResponse mostPapers = null;
        List<Object[]> topByPapers = paperAuthorRepository.findAuthorsOrderByPaperCountDesc(PageRequest.of(0, 1));
        if (!topByPapers.isEmpty()) {
            Object[] row = topByPapers.get(0);
            mostPapers = AuthorMapper.toResponse((Author) row[0]);
            mostPapers.setPapers(((Long) row[1]).intValue());
        }

        List<Author> topByCitations = authorRepository.findTopByCitationCount(PageRequest.of(0, 1));
        AuthorResponse mostCitations = topByCitations.isEmpty()
                ? null
                : toResponseWithPaperCount(topByCitations.get(0));

        List<Author> topByHIndex = authorRepository.findTopByHIndex(PageRequest.of(0, 1));
        AuthorResponse mostHIndex = topByHIndex.isEmpty()
                ? null
                : toResponseWithPaperCount(topByHIndex.get(0));

        return AuthorSpotlightResponse.builder()
                .mostPapers(mostPapers)
                .mostCitations(mostCitations)
                .mostHIndex(mostHIndex)
                .build();
    }

    /**
     * Map Author entity sang AuthorResponse, kèm set số paper (chỉ đếm paper ACTIVE).
     */
    private AuthorResponse toResponseWithPaperCount(Author author) {
        AuthorResponse response = AuthorMapper.toResponse(author);
        // Dùng count ACTIVE-only để nhất quán với mostPapers (findAuthorsOrderByPaperCountDesc)
        response.setPapers((int) paperAuthorRepository.countActiveByAuthorId(author.getId()));
        return response;
    }
}
