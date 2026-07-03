package com.norman.swp391.repository;

import com.norman.swp391.entity.Journal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Kho truy cập tạp chí.
 */
public interface JournalRepository extends JpaRepository<Journal, Long> {

/**
 * Tìm kiếm: findByNameIgnoreCase.
 */
    Optional<Journal> findByNameIgnoreCase(String name);

    @Query(value = "SELECT TOP 1 * FROM journals WHERE LOWER(name) = LOWER(:name)", nativeQuery = true)
    Optional<Journal> findFirstByNameNormalized(@Param("name") String name);

/**
 * Tìm kiếm: findByIssnIgnoreCase.
 */
    Optional<Journal> findByIssnIgnoreCase(String issn);

    @Query("SELECT j FROM Journal j WHERE :q IS NULL OR LOWER(j.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Journal> search(@Param("q") String q, Pageable pageable);

    @Query("""
        SELECT j.id, j.name, j.impactFactor, j.domain, COUNT(p.id)
        FROM Paper p
        JOIN p.journalRef j
        WHERE p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        GROUP BY j.id, j.name, j.impactFactor, j.domain
        ORDER BY COUNT(p.id) DESC
        """)
    java.util.List<Object[]> findTopJournalsByPaperCount(org.springframework.data.domain.Pageable pageable);

    /**
     * Tổng lượt trích dẫn và số bài ACTIVE theo từng tạp chí, dùng để tính Impact Factor proxy.
     * Gộp thành 1 query duy nhất (GROUP BY) để tránh N+1 khi chạy cho toàn bộ danh sách journal.
     */
    @Query("""
        SELECT j.id, COALESCE(SUM(p.citationCount), 0), COUNT(p.id)
        FROM Paper p
        JOIN p.journalRef j
        WHERE p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
          AND p.reviewStatus = com.norman.swp391.entity.enums.PaperReviewStatus.NONE
        GROUP BY j.id
        """)
    java.util.List<Object[]> aggregateCitationStatsByJournal();
}
