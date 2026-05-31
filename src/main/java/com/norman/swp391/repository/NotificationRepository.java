package com.norman.swp391.repository;

import com.norman.swp391.entity.Notification;
import com.norman.swp391.entity.enums.NotificationReadStatus;
import com.norman.swp391.entity.enums.NotificationTriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Kho truy cập thông báo.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

/**
 * Tìm kiếm: findByUserIdOrderByCreatedAtDesc.
 */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

/**
 * Xử lý nghiệp vụ: countByUserIdAndReadStatus.
 */
    long countByUserIdAndReadStatus(Long userId, NotificationReadStatus readStatus);

/**
 * Xử lý nghiệp vụ: existsByUserIdAndPaperId.
 */
    boolean existsByUserIdAndPaperId(Long userId, Long paperId);

    boolean existsByUserIdAndTopicIdAndTriggerType(
            Long userId, Long topicId, NotificationTriggerType triggerType);
}
