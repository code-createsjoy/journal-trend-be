package com.norman.swp391.repository;

import com.norman.swp391.entity.TopicTrend;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập xu hướng chủ đề.
 */
public interface TopicTrendRepository extends JpaRepository<TopicTrend, Long> {

/**
 * Tìm kiếm: findByTopicIdAndYearAndMonth.
 */
    Optional<TopicTrend> findByTopicIdAndYearAndMonth(Long topicId, int year, int month);

/**
 * Tìm kiếm: findByTopicIdOrderByYearDescMonthDesc.
 */
    List<TopicTrend> findByTopicIdOrderByYearDescMonthDesc(Long topicId);

    @Query("""
        SELECT tt FROM TopicTrend tt
        JOIN FETCH tt.topic t
        WHERE tt.year = :year AND tt.month = :month
        ORDER BY tt.trendScore DESC
        """)
    List<TopicTrend> findTopByYearMonth(
            @Param("year") int year, @Param("month") int month, Pageable pageable);

    @Query("""
        SELECT tt FROM TopicTrend tt
        WHERE tt.topic.id IN :topicIds AND tt.year = :year AND tt.month = :month
        """)
    List<TopicTrend> findByTopicIdInAndYearAndMonth(
            @Param("topicIds") Collection<Long> topicIds,
            @Param("year") int year,
            @Param("month") int month);

    List<TopicTrend> findByYearAndMonthAndAnomalyFlagTrueOrderByTrendScoreDesc(
            int year, int month, Pageable pageable);

    long countByYearAndMonth(int year, int month);

    long countByYearAndMonthAndTrendScoreGreaterThanEqual(int year, int month, BigDecimal trendScore);
}
