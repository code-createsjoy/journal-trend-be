package com.norman.swp391.service;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.Keyword;
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
 * Xử lý nghiệp vụ: notifyTrendingForFollowedKeywords.
 */
    void notifyTrendingForFollowedKeywords(List<Keyword> trendingKeywords);

/**
 * Xử lý nghiệp vụ: notifyNewPapersForSubscriptions.
 */
    void notifyNewPapersForSubscriptions(Set<Long> newPaperIds);

    void delete(Long notificationId);

    void deleteMultiple(List<Long> notificationIds);

    void deleteAll();

    void deleteAllRead();

    void markMultipleAsRead(List<Long> notificationIds);
}
