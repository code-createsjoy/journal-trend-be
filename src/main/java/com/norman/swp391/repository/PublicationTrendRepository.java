package com.norman.swp391.repository;

import com.norman.swp391.entity.PublicationTrend;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationTrendRepository extends JpaRepository<PublicationTrend, Long> {

    @Query("SELECT pt FROM PublicationTrend pt WHERE pt.keyword.keywordId = :keywordId AND pt.year = :year AND pt.month = :month")
    Optional<PublicationTrend> findByKeywordIdAndYearAndMonth(
            @Param("keywordId") Long keywordId, 
            @Param("year") int year, 
            @Param("month") int month);

    @Query("""
        SELECT pt FROM PublicationTrend pt
        JOIN FETCH pt.keyword
        WHERE pt.year = :year AND pt.month = :month
        """)
    List<PublicationTrend> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT pt FROM PublicationTrend pt WHERE pt.keyword.keywordId = :keywordId ORDER BY pt.year ASC, pt.month ASC")
    List<PublicationTrend> findByKeywordIdOrderByYearAscMonthAsc(@Param("keywordId") Long keywordId);

    @Query("SELECT pt FROM PublicationTrend pt WHERE pt.keyword.keywordId = :keywordId ORDER BY pt.year DESC, pt.month DESC")
    List<PublicationTrend> findByKeywordIdOrderByYearDescMonthDesc(@Param("keywordId") Long keywordId);

    /** Tải toàn bộ trend (kèm keyword) theo thứ tự thời gian — dùng cho job forecast, tránh N+1. */
    @Query("""
        SELECT pt FROM PublicationTrend pt
        JOIN FETCH pt.keyword k
        ORDER BY k.keywordId ASC, pt.year ASC, pt.month ASC
        """)
    List<PublicationTrend> findAllWithKeywordOrderedByDate();

    @Query("""
        SELECT pt FROM PublicationTrend pt
        JOIN FETCH pt.keyword
        WHERE pt.year = :year AND pt.month = :month
        ORDER BY pt.deltaPercent DESC
        """)
    List<PublicationTrend> findTopByYearMonth(@Param("year") int year, @Param("month") int month, Pageable pageable);

    long countByYearAndMonth(int year, int month);

    @Query("""
        SELECT COUNT(pt) FROM PublicationTrend pt
        WHERE pt.year = :year AND pt.month = :month
          AND pt.deltaPercent >= :threshold
        """)
    long countByYearAndMonthAndTrendScoreGreaterThanEqual(
            @Param("year") int year,
            @Param("month") int month,
            @Param("threshold") java.math.BigDecimal threshold);

    @Query("""
        SELECT pt FROM PublicationTrend pt
        WHERE pt.keyword.keywordId IN :keywordIds
          AND pt.year = :year AND pt.month = :month
        """)
    List<PublicationTrend> findByKeywordIdInAndYearAndMonth(
            @Param("keywordIds") Collection<Long> keywordIds,
            @Param("year") int year,
            @Param("month") int month);
}
