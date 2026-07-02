package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Kho truy cập liên kết bài báo–tác giả.
 */
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, Long> {

/**
 * Tìm kiếm: findByPaperId.
 */
    List<PaperAuthor> findByPaperId(Long paperId);

/**
 * Tìm kiếm: findByAuthorId.
 */
    List<PaperAuthor> findByAuthorId(Long authorId);

    @Query("SELECT pa FROM PaperAuthor pa JOIN FETCH pa.paper WHERE pa.author.id = :authorId")
    List<PaperAuthor> findByAuthorIdWithPaper(@Param("authorId") Long authorId);

    @Query("SELECT pa FROM PaperAuthor pa JOIN FETCH pa.author JOIN FETCH pa.paper WHERE pa.paper.id IN :paperIds")
    List<PaperAuthor> findByPaperIdInWithAuthor(@Param("paperIds") List<Long> paperIds);

/**
 * Xử lý nghiệp vụ: countByAuthorId.
 */
    long countByAuthorId(Long authorId);

    @Query("""
        SELECT pa.author, COUNT(DISTINCT p)
        FROM PaperAuthor pa
        JOIN pa.paper p
        JOIN PaperKeyword pk ON pk.paper.id = p.id
        WHERE pk.keyword.keywordId IN :keywordIds
          AND p.status = com.norman.swp391.entity.enums.PaperStatus.ACTIVE
        GROUP BY pa.author, pa.author.id, pa.author.name, pa.author.affiliation, pa.author.citationCount, pa.author.hIndex, pa.author.sourceType, pa.author.sourceIdentifier
        ORDER BY COUNT(DISTINCT p) DESC, pa.author.hIndex DESC
        """)
    List<Object[]> findTopAuthorsByKeywordIds(@Param("keywordIds") java.util.Collection<Long> keywordIds, org.springframework.data.domain.Pageable pageable);
}
