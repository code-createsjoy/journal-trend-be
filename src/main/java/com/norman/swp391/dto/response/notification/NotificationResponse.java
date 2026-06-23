package com.norman.swp391.dto.response.notification;

import com.norman.swp391.entity.enums.NotificationReadStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO thông báo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String message;
    private NotificationReadStatus readStatus;
    private LocalDateTime createdAt;
    private String triggerType;
    private Long keywordId;
    private Long journalId;
    private Long authorId;
    private Long paperId;
}


