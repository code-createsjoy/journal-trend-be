package com.norman.swp391.repository;

import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import java.time.LocalDate;
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

    boolean existsByDoi(String doi);

    default boolean existsByOpenAlexId(String openAlexId) {
        return existsBySourceTypeAndSourceIdentifier("OPENALEX", openAlexId);
    }

    boolean existsBySourceTypeAndSourceIdentifier(String sourceType, String sourceIdentifier);

    /**
     * Batch lookup papers theo sourceType và danh sách sourceIdentifier.
     * Dùng cho references graph cross-reference.
     */
    @Query("SELECT p FROM Paper p WHERE p.sourceType = :sourceType AND p.sourceIdentifier IN :ids")
    List<Paper> findBySourceTypeAndSourceIdentifierIn(
            @Param("sourceType") String sourceType,
            @Param("ids") List<String> ids);

    @Query("SELECT p.doi FROM Paper p WHERE p.doi IN :dois")
    List<String> findExistingDois(@Param("dois") List<String> dois);

    @Query("SELECT p.sourceIdentifier FROM Paper p WHERE p.sourceType = 'OPENALEX' AND p.sourceIdentifier IN :ids")
    List<String> findExistingOpenAlexIds(@Param("ids") List<String> ids);

    /**
     * Load tất cả DOIs để dùng cho in-memory pre-filter khi sync.
     */
    @Query("SELECT LOWER(p.doi) FROM Paper p WHERE p.doi IS NOT NULL")
    List<String> findAllDois();

    /**
     * Load tất cả source identifiers để dùng cho in-memory pre-filter khi sync.
     */
    @Query("SELECT LOWER(p.sourceIdentifier) FROM Paper p WHERE p.sourceIdentifier IS NOT NULL")
    List<String> findAllSourceIdentifiers();

    @Query("""
            SELECT p FROM Paper p
            WHERE p.doi IN :dois OR (p.sourceType = 'OPENALEX' AND p.sourceIdentifier IN :ids)
            """)
    List<Paper> findByDoiInOrSourceIdentifierIn(
            @Param("dois") List<String> dois,
            @Param("ids") List<String> ids);

    /**
     * Tìm kiếm: findByDoiIgnoreCase.
     */
    Optional<Paper> findByDoi(String doi);

    /**
     * Tìm bài báo theo Source Type và Identifier.
     */
    Optional<Paper> findBySourceTypeAndSourceIdentifier(String sourceType, String sourceIdentifier);

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
     * Tìm kiếm bài báo theo từ khóa, keyword và tác giả.
     */
    @Query("""
            SELECT p FROM Paper p
            WHERE p.status = :status
              AND p.reviewStatus = :reviewStatus
              AND (:q IS NULL OR
                   (COALESCE(:searchType, '') = '' AND (
                       LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(p.doi) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(p.journal) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR EXISTS (
                           SELECT 1 FROM PaperKeyword pk
                           WHERE pk.paper = p AND LOWER(pk.keyword.term) LIKE LOWER(CONCAT('%', :q, '%'))
                       )
                       OR EXISTS (
                           SELECT 1 FROM PaperAuthor pa
                           WHERE pa.paper = p AND LOWER(pa.author.name) LIKE LOWER(CONCAT('%', :q, '%'))
                       )
                   )) OR
                   (:searchType = 'papers' AND LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))) OR
                   (:searchType = 'authors' AND EXISTS (
                       SELECT 1 FROM PaperAuthor pa
                       WHERE pa.paper = p AND LOWER(pa.author.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   )) OR
                   (:searchType = 'keywords' AND EXISTS (
                       SELECT 1 FROM PaperKeyword pk
                       WHERE pk.paper = p AND LOWER(pk.keyword.term) = LOWER(:q)
                   ))
                  )
              AND (:keywordId IS NULL OR EXISTS (
                  SELECT 1 FROM PaperKeyword pk2
                  WHERE pk2.paper = p AND pk2.keyword.keywordId = :keywordId
              ))
              AND (:authorId IS NULL OR EXISTS (
                  SELECT 1 FROM PaperAuthor pa2
                  WHERE pa2.paper = p AND pa2.author.id = :authorId
              ))
              AND (:fromYear IS NULL OR YEAR(p.publicationDate) >= :fromYear)
              AND (:toYear IS NULL OR YEAR(p.publicationDate) <= :toYear)
              AND (:category IS NULL OR EXISTS (
                  SELECT 1 FROM PaperKeyword pk3
                  WHERE pk3.paper = p AND LOWER(pk3.keyword.domain) = LOWER(:category)
              ))
              AND (:minCitations IS NULL OR p.citationCount >= :minCitations)
              AND (:journalId IS NULL OR p.journalRef.id = :journalId)
            """)
    Page<Paper> search(
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus,
            @Param("q") String q,
            @Param("searchType") String searchType,
            @Param("keywordId") Long keywordId,
            @Param("authorId") Long authorId,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear,
            @Param("category") String category,
            @Param("minCitations") Integer minCitations,
            @Param("journalId") Long journalId,
            Pageable pageable);

    /** BR-35: gợi ý autocomplete theo title, ưu tiên bài nhiều citation hơn. */
    @Query("""
            SELECT p FROM Paper p
            WHERE p.status = :status AND p.reviewStatus = :reviewStatus
              AND LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY p.citationCount DESC
            """)
    List<Paper> suggestByTitle(
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus,
            @Param("q") String q,
            Pageable pageable);

    /**
     * Lấy bài báo theo domain của keyword (để lấy bài báo theo topic/domain).
     */
    @Query(value = """
            SELECT TOP 50 * FROM papers p
            WHERE p.status = 'ACTIVE'
              AND p.review_status = 'NONE'
              AND p.id IN (
                  SELECT DISTINCT pk.paper_id FROM paper_keywords pk
                  INNER JOIN keywords k ON pk.keyword_id = k.keyword_id
                  WHERE LOWER(k.domain) = LOWER(:domain)
              )
            ORDER BY p.citation_count DESC
            """, nativeQuery = true)
    List<Paper> findByKeywordDomain(@Param("domain") String domain);

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
     * Danh sách các năm xuất bản có trong kho (distinct).
     */
    @Query("SELECT p.publicationDate FROM Paper p WHERE p.status = :status AND p.publicationDate IS NOT NULL")
    List<LocalDate> findAllPublicationDates(@Param("status") PaperStatus status);

    /**
     * Danh sách năm xuất bản distinct, tính thẳng ở DB thay vì load hết publicationDate về Java rồi map/distinct.
     */
    @Query(value = """
            SELECT DISTINCT YEAR(publication_date)
            FROM papers
            WHERE status = 'ACTIVE' AND publication_date IS NOT NULL
            ORDER BY 1 DESC
            """, nativeQuery = true)
    List<Integer> findDistinctPublicationYears();

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

    /**
     * Bài chưa gắn journal_ref, phân trang theo id (keyset pagination) để backfill theo batch.
     * Dùng "id > :lastId" thay vì OFFSET vì các dòng đã link trong batch trước sẽ biến mất khỏi
     * điều kiện WHERE journalRef IS NULL — OFFSET-based paging sẽ bỏ sót dòng khi tập kết quả co lại
     * giữa các lần gọi; keyset theo id (đơn điệu tăng, không đổi) không bị ảnh hưởng bởi việc đó.
     */
    @Query("""
            SELECT p.id as id, p.journal as journal, p.journalRef.id as journalRefId
            FROM Paper p
            WHERE p.journalRef IS NULL AND p.journal IS NOT NULL AND TRIM(p.journal) <> '' AND p.id > :lastId
            ORDER BY p.id ASC
            """)
    List<PaperJournalBackfillRow> findPendingForJournalBackfill(@Param("lastId") Long lastId, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE papers SET journal_id = :journalId WHERE id = :paperId", nativeQuery = true)
    int linkJournal(@Param("paperId") Long paperId, @Param("journalId") Long journalId);

    @Query(value = """
            SELECT COUNT(DISTINCT p.id) FROM papers p
            INNER JOIN paper_keywords pk ON pk.paper_id = p.id
            WHERE p.status = 'ACTIVE' AND p.review_status = 'NONE'
            """, nativeQuery = true)
    long countActiveWithAtLeastOneKeyword();

    @Query("""
            SELECT p FROM Paper p
            WHERE (p.title LIKE '%?%' OR p.journal LIKE '%?%')
               OR p.reviewStatus = com.norman.swp391.entity.enums.PaperReviewStatus.PENDING_REVIEW
            ORDER BY CASE WHEN p.reviewStatus = com.norman.swp391.entity.enums.PaperReviewStatus.PENDING_REVIEW THEN 0 ELSE 1 END,
                     p.id DESC
            """)
    java.util.List<Paper> findNeedingMetadataRepair(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT p, COUNT(cp.id) as bookmarkCount
            FROM PaperAuthor pa
            JOIN pa.paper p
            LEFT JOIN CollectionPaper cp ON p.id = cp.paper.id
            WHERE pa.author.id = :authorId AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
            GROUP BY p.id, p.title, p.abstractText, p.doi, p.publicationDate, p.citationCount, p.pdfUrl, p.sourceUrl, p.openAccess, p.sourceType, p.sourceIdentifier, p.primarySource, p.status, p.reviewStatus, p.createdAt, p.journal, p.journalRef, p.conflictAbstract, p.conflictSource, p.conflictTitle, p.reviewFlaggedAt
            ORDER BY COUNT(cp.id) DESC, p.citationCount DESC
            """)
    List<Object[]> findPopularPapersByAuthor(@Param("authorId") Long authorId);

    @Query("""
        SELECT DISTINCT p FROM Paper p
        JOIN PaperKeyword pk ON pk.paper.id = p.id
        WHERE pk.keyword.keywordId IN :keywordIds
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        ORDER BY p.citationCount DESC
        """)
    List<Paper> findTopCitedByKeywordIds(
            @Param("keywordIds") java.util.Collection<Long> keywordIds, 
            Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Paper p
        JOIN PaperAuthor pa ON pa.paper.id = p.id
        WHERE pa.author.id IN :authorIds
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        ORDER BY p.publicationDate DESC, p.id DESC
        """)
    List<Paper> findLatestByAuthorIds(@Param("authorIds") java.util.Collection<Long> authorIds, Pageable pageable);

    @Query("""
        SELECT p, COUNT(pk.id) as matchCount
        FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE pk.keyword.keywordId IN :keywordIds
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        GROUP BY p.id, p.title, p.abstractText, p.doi, p.publicationDate, p.citationCount, p.pdfUrl, p.sourceUrl, p.openAccess, p.sourceType, p.sourceIdentifier, p.primarySource, p.status, p.reviewStatus, p.createdAt, p.journal, p.journalRef, p.conflictAbstract, p.conflictSource, p.conflictTitle, p.reviewFlaggedAt
        ORDER BY COUNT(pk.id) DESC, p.citationCount DESC
        """)
    List<Object[]> findByKeywordOverlap(@Param("keywordIds") java.util.Collection<Long> keywordIds, Pageable pageable);

    @Query("""
        SELECT p, COUNT(pk.id) as matchCount, MAX(COALESCE(pk.keyword.trendScore, 0)) as maxTrend
        FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE pk.keyword.keywordId IN :keywordIds
          AND pk.keyword.trendScore >= 15.0
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        GROUP BY p.id, p.title, p.abstractText, p.doi, p.publicationDate, p.citationCount, p.pdfUrl, p.sourceUrl, p.openAccess, p.sourceType, p.sourceIdentifier, p.primarySource, p.status, p.reviewStatus, p.createdAt, p.journal, p.journalRef, p.conflictAbstract, p.conflictSource, p.conflictTitle, p.reviewFlaggedAt
        ORDER BY COUNT(pk.id) DESC, MAX(COALESCE(pk.keyword.trendScore, 0)) DESC, p.publicationDate DESC
        """)
    List<Object[]> findByTrendingKeywordIdsWithOverlap(@Param("keywordIds") java.util.Collection<Long> keywordIds, Pageable pageable);

    @Query("""
        SELECT p, COUNT(pk.id) as matchCount, MAX(COALESCE(pk.keyword.trendScore, 0)) as maxTrend
        FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE pk.keyword.keywordId IN :keywordIds
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        GROUP BY p.id, p.title, p.abstractText, p.doi, p.publicationDate, p.citationCount, p.pdfUrl, p.sourceUrl, p.openAccess, p.sourceType, p.sourceIdentifier, p.primarySource, p.status, p.reviewStatus, p.createdAt, p.journal, p.journalRef, p.conflictAbstract, p.conflictSource, p.conflictTitle, p.reviewFlaggedAt
        ORDER BY COUNT(pk.id) DESC, MAX(COALESCE(pk.keyword.trendScore, 0)) DESC, p.publicationDate DESC
        """)
    List<Object[]> findByKeywordIdsWithOverlapSortedByTrend(@Param("keywordIds") java.util.Collection<Long> keywordIds, Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Paper p
        WHERE p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
          AND p.publicationDate >= :since
          AND p.id IN (
              SELECT pk.paper.id FROM PaperKeyword pk
              WHERE pk.keyword.trendScore >= 15.0
          )
        ORDER BY p.publicationDate DESC, p.citationCount DESC
        """)
    List<Paper> findRisingPapersGlobal(@Param("since") java.time.LocalDate since, Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Paper p
        WHERE p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
          AND p.publicationDate >= :since
          AND p.id IN (
              SELECT pk.paper.id FROM PaperKeyword pk
              WHERE pk.keyword.keywordId IN :keywordIds
                AND pk.keyword.trendScore >= 15.0
          )
        ORDER BY p.publicationDate DESC, p.citationCount DESC
        """)
    List<Paper> findRisingPapers(@Param("keywordIds") java.util.Collection<Long> keywordIds,
                                 @Param("since") java.time.LocalDate since,
                                 Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Paper p
        WHERE p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
          AND p.journalRef.id IN :journalIds
          AND p.id IN (
              SELECT pk.paper.id FROM PaperKeyword pk
              WHERE pk.keyword.trendScore >= 15.0
          )
        ORDER BY p.publicationDate DESC, p.citationCount DESC
        """)
    List<Paper> findByFollowedJournalIdsTrending(@Param("journalIds") java.util.Collection<Long> journalIds,
                                                 Pageable pageable);
}
