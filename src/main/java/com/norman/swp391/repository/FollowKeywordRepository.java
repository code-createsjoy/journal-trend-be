package com.norman.swp391.repository;

import com.norman.swp391.entity.FollowKeyword;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface FollowKeywordRepository extends JpaRepository<FollowKeyword, Long> {

    @Query("SELECT CASE WHEN COUNT(fk) > 0 THEN true ELSE false END FROM FollowKeyword fk WHERE fk.user.id = :userId AND fk.keyword.keywordId = :keywordId")
    boolean existsByUserIdAndKeywordId(@Param("userId") Long userId, @Param("keywordId") Long keywordId);

    long countByUserId(Long userId);

    @Query("SELECT fk FROM FollowKeyword fk WHERE fk.user.id = :userId AND fk.keyword.keywordId = :keywordId")
    Optional<FollowKeyword> findByUserIdAndKeywordId(@Param("userId") Long userId, @Param("keywordId") Long keywordId);

    List<FollowKeyword> findByUserId(Long userId);

    @Query("SELECT fk FROM FollowKeyword fk WHERE fk.keyword.keywordId = :keywordId")
    List<FollowKeyword> findByKeywordId(@Param("keywordId") Long keywordId);

    @Query("""
        SELECT fk.keyword.keywordId, COUNT(fk) FROM FollowKeyword fk
        GROUP BY fk.keyword.keywordId
        ORDER BY COUNT(fk) DESC
        """)
    List<Object[]> countFollowsByKeyword(Pageable pageable);
}
