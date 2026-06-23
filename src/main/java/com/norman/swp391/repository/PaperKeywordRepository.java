package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperKeywordRepository extends JpaRepository<PaperKeyword, Long> {

    List<PaperKeyword> findByPaperId(Long paperId);

    @Query("SELECT pk FROM PaperKeyword pk JOIN FETCH pk.keyword WHERE pk.paper.id IN :paperIds")
    List<PaperKeyword> findByPaperIdInWithKeyword(@Param("paperIds") Collection<Long> paperIds);

    @Query("""
        SELECT pk.keyword.keywordId, COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE p.status = :status
          AND p.reviewStatus = :reviewStatus
          AND YEAR(p.publicationDate) = :year
          AND MONTH(p.publicationDate) = :month
        GROUP BY pk.keyword.keywordId
        """)
    List<Object[]> countPapersByKeywordForMonth(
            @Param("year") int year,
            @Param("month") int month,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pk.keyword.keywordId, COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE p.status = :status
          AND p.reviewStatus = :reviewStatus
          AND YEAR(p.publicationDate) = :year
        GROUP BY pk.keyword.keywordId
        """)
    List<Object[]> countPapersByKeywordForYear(
            @Param("year") int year,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE pk.keyword.keywordId = :keywordId
          AND p.status = :status
          AND p.reviewStatus = :reviewStatus
        """)
    int countByKeywordId(
            @Param("keywordId") Long keywordId,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pk.keyword.keywordId, COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pk.keyword.keywordId
        HAVING COUNT(pk) >= :minPapers
        """)
    List<Long> findKeywordIdsWithAtLeastPapers(
            @Param("minPapers") int minPapers,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pk.keyword.keywordId, pk.keyword.term, COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pk.keyword.keywordId, pk.keyword.term
        ORDER BY COUNT(pk) DESC
        """)
    List<Object[]> findTopKeywordsByPaperCount(
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus,
            Pageable pageable);

    @Query("""
        SELECT pk.keyword.keywordId, COUNT(pk) FROM PaperKeyword pk
        JOIN pk.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pk.keyword.keywordId
        """)
    List<Object[]> countAllPapersByKeyword(
            @Param("status") PaperStatus status, @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("SELECT COUNT(DISTINCT pk.keyword.keywordId) FROM PaperKeyword pk")
    long countDistinctKeywords();

    @Query("""
        SELECT pk.keyword.term, COUNT(pk.id) 
        FROM PaperAuthor pa
        JOIN PaperKeyword pk ON pa.paper.id = pk.paper.id
        WHERE pa.author.id = :authorId
        GROUP BY pk.keyword.term
        ORDER BY COUNT(pk.id) DESC
        """)
    List<Object[]> findTopKeywordsByAuthor(@Param("authorId") Long authorId, Pageable pageable);
}
