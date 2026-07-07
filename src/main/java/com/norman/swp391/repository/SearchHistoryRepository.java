package com.norman.swp391.repository;

import com.norman.swp391.entity.SearchHistory;
import com.norman.swp391.entity.enums.SearchType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Optional<SearchHistory> findByUserIdAndSearchTypeAndQueryIgnoreCase(
            Long userId, SearchType searchType, String query);

    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);
}
