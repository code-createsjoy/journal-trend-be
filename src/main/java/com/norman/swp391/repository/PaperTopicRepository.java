package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperTopic;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Kho truy cập liên kết bài báo–chủ đề.
 */
public interface PaperTopicRepository extends JpaRepository<PaperTopic, Long> {

/**
 * Tìm kiếm: findByPaperId.
 */
    List<PaperTopic> findByPaperId(Long paperId);

    @Query("SELECT pt FROM PaperTopic pt JOIN FETCH pt.topic WHERE pt.paper.id IN :paperIds")
    List<PaperTopic> findByPaperIdInWithTopic(@Param("paperIds") Collection<Long> paperIds);

    @Query("""
        SELECT pt.topic.id, COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE p.status = :status
          AND p.reviewStatus = :reviewStatus
          AND YEAR(p.publicationDate) = :year
          AND MONTH(p.publicationDate) = :month
        GROUP BY pt.topic.id
        """)
    List<Object[]> countPapersByTopicForMonth(
            @Param("year") int year,
            @Param("month") int month,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pt.topic.id, COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE p.status = :status
          AND p.reviewStatus = :reviewStatus
          AND YEAR(p.publicationDate) = :year
        GROUP BY pt.topic.id
        """)
    List<Object[]> countPapersByTopicForYear(
            @Param("year") int year,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE pt.topic.id = :topicId
          AND p.status = :status
          AND p.reviewStatus = :reviewStatus
        """)
    long countByTopicId(
            @Param("topicId") Long topicId,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pt.topic.id, COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pt.topic.id
        HAVING COUNT(pt) >= :minPapers
        """)
    List<Long> findTopicIdsWithAtLeastPapers(
            @Param("minPapers") int minPapers,
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("""
        SELECT pt.topic.id, pt.topic.name, COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pt.topic.id, pt.topic.name
        ORDER BY COUNT(pt) DESC
        """)
    List<Object[]> findTopTopicsByPaperCount(
            @Param("status") PaperStatus status,
            @Param("reviewStatus") PaperReviewStatus reviewStatus,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT pt.topic.id, COUNT(pt) FROM PaperTopic pt
        JOIN pt.paper p
        WHERE p.status = :status AND p.reviewStatus = :reviewStatus
        GROUP BY pt.topic.id
        """)
    List<Object[]> countAllPapersByTopic(
            @Param("status") PaperStatus status, @Param("reviewStatus") PaperReviewStatus reviewStatus);

    @Query("SELECT COUNT(DISTINCT pt.topic.id) FROM PaperTopic pt")
    long countDistinctTopics();
}
