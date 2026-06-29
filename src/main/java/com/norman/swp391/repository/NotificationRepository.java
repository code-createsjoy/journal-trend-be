package com.norman.swp391.repository;

import com.norman.swp391.entity.Notification;
import com.norman.swp391.entity.enums.NotificationReadStatus;
import com.norman.swp391.entity.enums.NotificationTriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Kho truy cập thông báo.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

/**
 * Tìm kiếm: findByUserIdOrderByCreatedAtDesc.
 */
    @EntityGraph(attributePaths = {"keyword", "paper", "journal", "author"})
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

/**
 * Xử lý nghiệp vụ: countByUserIdAndReadStatus.
 */
    long countByUserIdAndReadStatus(Long userId, NotificationReadStatus readStatus);

/**
 * Xử lý nghiệp vụ: existsByUserIdAndPaperId.
 */
    boolean existsByUserIdAndPaperId(Long userId, Long paperId);

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM Notification n WHERE n.user.id = :userId AND n.keyword.keywordId = :keywordId AND n.triggerType = :triggerType")
    boolean existsByUserIdAndKeywordIdAndTriggerType(
            @Param("userId") Long userId, 
            @Param("keywordId") Long keywordId, 
            @Param("triggerType") NotificationTriggerType triggerType);

    @Query("SELECT n.user.id, n.paper.id FROM Notification n WHERE n.paper.id IN :paperIds")
    List<Object[]> findUserIdAndPaperIdByPaperIdIn(@Param("paperIds") List<Long> paperIds);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.id IN :ids")
    void deleteByUserIdAndIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.readStatus = :readStatus")
    void deleteByUserIdAndReadStatus(@Param("userId") Long userId, @Param("readStatus") NotificationReadStatus readStatus);
}
