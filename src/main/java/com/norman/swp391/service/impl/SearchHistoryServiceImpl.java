package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.search.SearchHistoryResponse;
import com.norman.swp391.entity.SearchHistory;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.SearchType;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.exception.UnauthorizedException;
import com.norman.swp391.repository.SearchHistoryRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.SearchHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private static final int RECENT_SEARCH_LIMIT = 10;

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    /** Ghi nhận 1 lượt tìm kiếm; nếu đã tìm cùng query+searchType trước đó thì chỉ cập nhật lại thời gian (đưa lên đầu). */
    @Override
    @Transactional
    public void recordSearch(String query, SearchType searchType) {
        if (!StringUtils.hasText(query) || searchType == null) {
            return;
        }
        Long userId = requireUserId();
        String trimmed = query.trim();

        SearchHistory existing = searchHistoryRepository
                .findByUserIdAndSearchTypeAndQueryIgnoreCase(userId, searchType, trimmed)
                .orElse(null);
        if (existing != null) {
            existing.setSearchedAt(LocalDateTime.now());
            searchHistoryRepository.save(existing);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        searchHistoryRepository.save(SearchHistory.builder()
                .user(user)
                .query(trimmed)
                .searchType(searchType)
                .searchedAt(LocalDateTime.now())
                .build());
    }

    /** Lấy tối đa 10 lượt tìm kiếm gần nhất của user hiện tại. */
    @Override
    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getRecentSearches() {
        Long userId = requireUserId();
        return searchHistoryRepository
                .findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, RECENT_SEARCH_LIMIT))
                .stream()
                .map(h -> SearchHistoryResponse.builder()
                        .query(h.getQuery())
                        .searchType(h.getSearchType())
                        .build())
                .toList();
    }

    /** Lấy userId của user đang đăng nhập, throw nếu chưa xác thực. */
    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }
}
