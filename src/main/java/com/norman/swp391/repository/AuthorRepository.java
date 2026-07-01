package com.norman.swp391.repository;

import com.norman.swp391.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Kho truy cập thực thể tác giả.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {
    /**
     * Tìm kiếm: findBySourceTypeAndSourceIdentifierIgnoreCase.
     */
    Optional<Author> findBySourceTypeAndSourceIdentifier(String sourceType, String sourceIdentifier);

    @Query("SELECT a FROM Author a WHERE a.sourceType = :sourceType AND a.sourceIdentifier IN :ids")
    java.util.List<Author> findBySourceTypeAndSourceIdentifierIn(
        @Param("sourceType") String sourceType, 
        @Param("ids") java.util.Collection<String> ids);

    /**
     * Bulk fetch authors by name (case-insensitive) — dùng thay thế N+1 lookup.
     */
    @Query("SELECT a FROM Author a WHERE LOWER(a.name) IN :names")
    java.util.List<Author> findByNameInIgnoreCase(@Param("names") java.util.Collection<String> names);

    /**
     * Tìm tác giả đầu tiên theo tên và đơn vị công tác.
     */
    Optional<Author> findFirstByNameAndAffiliationOrderByIdAsc(String name, String affiliation);

    /**
     * Tìm kiếm tác giả theo từ khóa trong tên.
     */
    @Query("SELECT a FROM Author a WHERE :q IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Author> search(@Param("q") String q, Pageable pageable);

    /**
     * Lấy tác giả nổi bật theo lượt trích dẫn giảm dần.
     */
    @Query("SELECT a FROM Author a ORDER BY a.citationCount DESC")
    Page<Author> findFeatured(Pageable pageable);

    /**
     * Tìm kiếm toàn bộ tác giả phân trang theo tên.
     */
    @Query("SELECT a FROM Author a WHERE :q IS NULL OR :q = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Author> findAllAuthors(@Param("q") String q, Pageable pageable);

    /**
     * Lấy authors chưa được enrich stats từ OpenAlex (hIndex IS NULL, có sourceIdentifier).
     */
    @Query("SELECT a FROM Author a WHERE a.sourceType = 'OPENALEX' AND a.hIndex IS NULL AND a.sourceIdentifier IS NOT NULL ORDER BY a.id ASC")
    java.util.List<Author> findUnenrichedAuthors(Pageable pageable);

    /**
     * Tìm kiếm tác giả phân trang theo lĩnh vực (domain/topic) và sắp xếp theo mức độ trending của tháng.
     */
    @Query(value = "SELECT a FROM Author a " +
           "JOIN PaperAuthor pa ON pa.author.id = a.id " +
           "JOIN Paper p ON pa.paper.id = p.id " +
           "JOIN PaperKeyword pk ON pk.paper.id = p.id " +
           "JOIN Keyword k ON pk.keyword.keywordId = k.keywordId " +
           "LEFT JOIN PublicationTrend t ON t.keyword.keywordId = k.keywordId AND t.year = :year AND t.month = :month " +
           "WHERE (k.domain = :domain) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "GROUP BY a.id, a.name, a.affiliation, a.citationCount, a.hIndex, a.sourceType, a.sourceIdentifier " +
           "ORDER BY MAX(COALESCE(t.deltaPercent, 0)) DESC, a.citationCount DESC",
           countQuery = "SELECT COUNT(DISTINCT a) FROM Author a " +
           "JOIN PaperAuthor pa ON pa.author.id = a.id " +
           "JOIN Paper p ON pa.paper.id = p.id " +
           "JOIN PaperKeyword pk ON pk.paper.id = p.id " +
           "JOIN Keyword k ON pk.keyword.keywordId = k.keywordId " +
           "WHERE (k.domain = :domain) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Author> findTrendingAuthorsByDomain(@Param("domain") String domain, @Param("q") String q, @Param("year") int year, @Param("month") int month, Pageable pageable);
}

