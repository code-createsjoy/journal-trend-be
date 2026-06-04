package com.norman.swp391.service;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.Topic;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ thông báo người dùng.
 */
public interface NotificationService {

/**
 * Danh sách: listForCurrentUser.
 */
    PageResponse<NotificationResponse> listForCurrentUser(Pageable pageable);

/**
 * Xử lý nghiệp vụ: markAsRead.
 */
    void markAsRead(Long notificationId);

/**
 * Xử lý nghiệp vụ: markAllAsRead.
 */
    void markAllAsRead();

/**
 * Xử lý nghiệp vụ: notifyTrendingForFollowedTopics.
 */
    void notifyTrendingForFollowedTopics(List<Topic> trendingTopics);

/**
 * Xử lý nghiệp vụ: notifyNewPapersForSubscriptions.
 */
    void notifyNewPapersForSubscriptions(Set<Long> newPaperIds);
}
