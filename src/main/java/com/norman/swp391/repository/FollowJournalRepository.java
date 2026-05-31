package com.norman.swp391.repository;

import com.norman.swp391.entity.FollowJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập theo dõi tạp chí.
 */
public interface FollowJournalRepository extends JpaRepository<FollowJournal, Long> {
    /**
     * Tìm kiếm: findByUserId.
     */
    List<FollowJournal> findByUserId(Long userId);

    /**
     * Tìm bản ghi theo dõi user–tạp chí.
     */
    Optional<FollowJournal> findByUserIdAndJournalId(Long userId, Long journalId);

    /**
     * Kiểm tra user đã theo dõi tạp chí chưa.
     */
    boolean existsByUserIdAndJournalId(Long userId, Long journalId);

    long countByUserId(Long userId);

    /**
     * Lấy mọi người theo dõi một tạp chí.
     */
    List<FollowJournal> findByJournalId(Long journalId);
}
