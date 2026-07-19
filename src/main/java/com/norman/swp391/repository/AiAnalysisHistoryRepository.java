package com.norman.swp391.repository;

import com.norman.swp391.entity.AiAnalysisHistory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Kho truy cập lịch sử phân tích AI. */
public interface AiAnalysisHistoryRepository extends JpaRepository<AiAnalysisHistory, Long> {

    Page<AiAnalysisHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<AiAnalysisHistory> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
