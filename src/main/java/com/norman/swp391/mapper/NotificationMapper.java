package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.Notification;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Mapper NotificationMapper.
 */
@UtilityClass
public class NotificationMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }
        return NotificationResponse.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .readStatus(notification.getReadStatus())
                .createdAt(notification.getCreatedAt())
                .triggerType(notification.getTriggerType() != null ? notification.getTriggerType().name() : null)
                .keywordId(notification.getKeyword() != null ? notification.getKeyword().getKeywordId() : null)
                .journalId(notification.getJournal() != null ? notification.getJournal().getId() : null)
                .authorId(notification.getAuthor() != null ? notification.getAuthor().getId() : null)
                .paperId(notification.getPaper() != null ? notification.getPaper().getId() : null)
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<NotificationResponse> toResponseList(List<Notification> notifications) {
        return notifications.stream().map(NotificationMapper::toResponse).toList();
    }
}


