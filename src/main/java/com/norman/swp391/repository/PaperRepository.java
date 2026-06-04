package com.norman.swp391.repository;

import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Kho truy cập thực thể bài báo.
 */
public interface PaperRepository extends JpaRepository<Paper, Long> {
    interface PaperJournalBackfillRow {
        Long getId();
        String getJournal();
        Long getJournalRefId();
    }

    /**
     * Tìm kiếm: findByDoiIgnoreCase.
     */
    Optional<Paper> findByDoiIgnoreCase(String doi);

    /**
     * Tìm bài báo theo OpenAlex ID.
     */
    Optional<Paper> findByOpenAlexIdIgnoreCase(String openAlexId);

    /**
     * Lấy bài thiếu ngày xuất bản theo trạng thái.
     */
    Page<Paper> findByStatusAndPublicationDateIsNull(PaperStatus status, Pageable pageable);

    /**
     * Đếm số bài theo trạng thái.
     */
    long countByStatus(PaperStatus status);

    /**
     * Phân trang bài theo trạng thái.
     */
    Page<Paper> findByStatus(PaperStatus status, Pageable pageable);

    /**
     * Bài có lượt trích dẫn cao nhất theo trạng thái.
     */
    Optional<Paper> findFirstByStatusOrderByCitationCountDesc(PaperStatus status);

    /**
     * Tìm kiếm bài báo theo từ khóa, chủ đề và tác giả.
     */
    @Query("""
        SELECT DISTINCT p FROM Paper p
        LEFT JOIN PaperTopic pt ON pt.paper = p
        LEFT JOIN Topic t ON pt.topic = t
        LEFT JOIN PaperAuthor pa ON pa.paper = p
        LEFT JOIN Author a ON pa.author = a
        WHERE p.status = :status
          AND p.reviewStatus = :reviewStatus
          AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.doi) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:topicId IS NULL OR t.id = :topicId)
          AND (:authorId IS NULL OR a.id = :authorId)
        """)
    Page<Paper> search(
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus,
            @Param("q") String q,
            @Param("topicId") Long topicId,
            @Param("authorId") Long authorId,
            Pageable pageable);

    /**
     * Tổng lượt trích dẫn theo trạng thái.
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM Paper p WHERE p.status = :status")
    long sumCitationCountByStatus(@Param("status") PaperStatus status);

    /**
     * Thống kê số bài theo tháng xuất bản.
     */
    @Query("""
        SELECT YEAR(p.publicationDate), MONTH(p.publicationDate), COUNT(p)
        FROM Paper p
        WHERE p.status = :status AND p.publicationDate IS NOT NULL
        GROUP BY YEAR(p.publicationDate), MONTH(p.publicationDate)
        ORDER BY YEAR(p.publicationDate) ASC, MONTH(p.publicationDate) ASC
        """)
    List<Object[]> countActivePapersByPublicationMonth(@Param("status") PaperStatus status);

    /**
     * Năm xuất bản mới nhất trong kho (dùng fallback tính trend theo năm).
     */
    @Query("SELECT MAX(YEAR(p.publicationDate)) FROM Paper p WHERE p.status = :status AND p.publicationDate IS NOT NULL")
    Integer findMaxPublicationYear(@Param("status") PaperStatus status);

    /**
     * Bài cần bổ sung metadata (tóm tắt hoặc tác giả).
     */
    @Query("""
        SELECT DISTINCT p FROM Paper p
        WHERE p.status = :status
          AND (
            p.abstractText IS NULL
            OR TRIM(p.abstractText) = ''
            OR NOT EXISTS (SELECT 1 FROM PaperAuthor pa WHERE pa.paper = p)
          )
        ORDER BY p.citationCount DESC
        """)
    List<Paper> findPendingReview(@Param("status") PaperStatus status, Pageable pageable);

    Page<Paper> findByReviewStatus(PaperReviewStatus reviewStatus, Pageable pageable);

    Page<Paper> findByReviewStatusAndReviewFlaggedAtBetween(
            PaperReviewStatus reviewStatus,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    List<Paper> findByReviewStatusAndReviewFlaggedAtBefore(
            PaperReviewStatus reviewStatus, LocalDateTime before);

    long countByReviewStatus(PaperReviewStatus reviewStatus);

    @Query("""
        SELECT p.id as id, p.journal as journal, p.journalRef.id as journalRefId
        FROM Paper p
        """)
    List<PaperJournalBackfillRow> findAllForJournalBackfill();

    @Modifying
    @Query(value = "UPDATE papers SET journal_id = :journalId WHERE id = :paperId", nativeQuery = true)
    int linkJournal(@Param("paperId") Long paperId, @Param("journalId") Long journalId);

    @Query(value = """
        SELECT COUNT(DISTINCT p.id) FROM papers p
        INNER JOIN paper_topics pt ON pt.paper_id = p.id
        WHERE p.status = 'ACTIVE' AND p.review_status = 'NONE'
        """, nativeQuery = true)
    long countActiveWithAtLeastOneTopic();

    @Query("""
        SELECT p FROM Paper p
        WHERE (p.title LIKE '%?%' OR p.journal LIKE '%?%')
           OR p.reviewStatus = com.norman.swp391.entity.enums.PaperReviewStatus.PENDING_REVIEW
        ORDER BY CASE WHEN p.reviewStatus = com.norman.swp391.entity.enums.PaperReviewStatus.PENDING_REVIEW THEN 0 ELSE 1 END,
                 p.id DESC
        """)
    java.util.List<Paper> findNeedingMetadataRepair(org.springframework.data.domain.Pageable pageable);
}
