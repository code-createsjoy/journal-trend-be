package com.norman.swp391.repository;

import com.norman.swp391.entity.KeywordSyncState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for keyword-level sync progress tracking.
 */
public interface KeywordSyncStateRepository extends JpaRepository<KeywordSyncState, Long> {

    Optional<KeywordSyncState> findByKeywordAndSourceType(String keyword, String sourceType);
}
