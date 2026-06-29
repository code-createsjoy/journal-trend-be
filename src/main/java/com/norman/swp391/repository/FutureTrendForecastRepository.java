package com.norman.swp391.repository;

import com.norman.swp391.entity.FutureTrendForecast;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FutureTrendForecastRepository extends JpaRepository<FutureTrendForecast, Long> {

    /** Top N theo điểm cao nhất — dùng cho API danh sách. */
    @Query("""
        SELECT f FROM FutureTrendForecast f
        JOIN FETCH f.keyword
        ORDER BY f.potentialScore DESC
        """)
    List<FutureTrendForecast> findTopByScore(Pageable pageable);

    /** Chi tiết 1 keyword — dùng cho API detail. */
    @Query("""
        SELECT f FROM FutureTrendForecast f
        JOIN FETCH f.keyword
        WHERE f.keyword.keywordId = :keywordId
        """)
    Optional<FutureTrendForecast> findByKeywordId(@Param("keywordId") Long keywordId);

    /** Xóa bản ghi cũ trước khi insert mới — tránh deleteAll() gây downtime. */
    @Modifying
    @Query("DELETE FROM FutureTrendForecast f WHERE f.calculatedAt < :before")
    void deleteByCalculatedAtBefore(@Param("before") LocalDateTime before);

    /** Lần tính gần nhất — dùng để log. */
    @Query("SELECT MAX(f.calculatedAt) FROM FutureTrendForecast f")
    Optional<LocalDateTime> findLatestCalculatedAt();
}
