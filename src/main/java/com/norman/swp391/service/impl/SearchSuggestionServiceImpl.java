package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.search.SearchSuggestionResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SearchType;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.service.SearchSuggestionService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Triển khai dịch vụ gợi ý autocomplete (BR-35): gộp kết quả từ paper/author/keyword,
 * xen kẽ 3 loại để danh sách hiển thị đa dạng thay vì dồn hết 1 loại lên đầu.
 */
@Service
@RequiredArgsConstructor
public class SearchSuggestionServiceImpl implements SearchSuggestionService {

    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final KeywordRepository keywordRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> suggest(String q, int limit) {
        if (!StringUtils.hasText(q) || limit <= 0) {
            return List.of();
        }
        String trimmed = q.trim();
        int perType = Math.max(3, limit / 3 + 1);

        List<SearchSuggestionResponse> keywords = keywordRepository
                .suggestByTerm(trimmed, PageRequest.of(0, perType))
                .stream()
                .map(this::toKeywordSuggestion)
                .toList();

        List<SearchSuggestionResponse> papers = paperRepository
                .suggestByTitle(PaperStatus.ACTIVE, PaperReviewStatus.NONE, trimmed, PageRequest.of(0, perType))
                .stream()
                .map(this::toPaperSuggestion)
                .toList();

        List<SearchSuggestionResponse> authors = authorRepository
                .suggestByName(trimmed, PageRequest.of(0, perType))
                .stream()
                .map(this::toAuthorSuggestion)
                .toList();

        return interleave(keywords, papers, authors, limit);
    }

    /** Xen kẽ theo vòng round-robin (keyword, paper, author, ...) rồi cắt về đúng limit. */
    private List<SearchSuggestionResponse> interleave(
            List<SearchSuggestionResponse> keywords,
            List<SearchSuggestionResponse> papers,
            List<SearchSuggestionResponse> authors,
            int limit) {
        List<SearchSuggestionResponse> result = new ArrayList<>(limit);
        int max = Math.max(keywords.size(), Math.max(papers.size(), authors.size()));
        for (int i = 0; i < max && result.size() < limit; i++) {
            if (i < keywords.size() && result.size() < limit) {
                result.add(keywords.get(i));
            }
            if (i < papers.size() && result.size() < limit) {
                result.add(papers.get(i));
            }
            if (i < authors.size() && result.size() < limit) {
                result.add(authors.get(i));
            }
        }
        return result;
    }

    private SearchSuggestionResponse toKeywordSuggestion(Keyword k) {
        return SearchSuggestionResponse.builder()
                .type(SearchType.KEYWORDS)
                .id(String.valueOf(k.getKeywordId()))
                .label(k.getTerm())
                .subtitle(k.getDomain())
                .build();
    }

    private SearchSuggestionResponse toPaperSuggestion(Paper p) {
        return SearchSuggestionResponse.builder()
                .type(SearchType.PAPERS)
                .id(String.valueOf(p.getId()))
                .label(p.getTitle())
                .subtitle(p.getJournal())
                .build();
    }

    private SearchSuggestionResponse toAuthorSuggestion(Author a) {
        return SearchSuggestionResponse.builder()
                .type(SearchType.AUTHORS)
                .id(String.valueOf(a.getId()))
                .label(a.getName())
                .subtitle(a.getAffiliation())
                .build();
    }
}
