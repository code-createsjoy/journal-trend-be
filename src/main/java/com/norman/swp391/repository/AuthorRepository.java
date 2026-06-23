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
           "GROUP BY a.id, a.name, a.affiliation, a.citationCount, a.sourceType, a.sourceIdentifier " +
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

