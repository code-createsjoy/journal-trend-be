package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.FollowJournal;
import com.norman.swp391.entity.FollowTopic;
import com.norman.swp391.entity.Notification;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperTopic;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.NotificationReadStatus;
import com.norman.swp391.entity.enums.NotificationTriggerType;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.NotificationMapper;
import com.norman.swp391.repository.FollowJournalRepository;
import com.norman.swp391.repository.FollowTopicRepository;
import com.norman.swp391.repository.NotificationRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperTopicRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.NotificationService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ thông báo.
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final FollowTopicRepository followTopicRepository;
    private final FollowJournalRepository followJournalRepository;
    private final PaperRepository paperRepository;
    private final PaperTopicRepository paperTopicRepository;

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listForCurrentUser.
 */
    public PageResponse<NotificationResponse> listForCurrentUser(Pageable pageable) {
        Long userId = requireUserId();
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, NotificationMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: markAsRead.
 */
    public void markAsRead(Long notificationId) {
        Long userId = requireUserId();
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.setReadStatus(NotificationReadStatus.READ);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: markAllAsRead.
 */
    public void markAllAsRead() {
        Long userId = requireUserId();
        Page<Notification> page =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
        page.getContent().forEach(n -> n.setReadStatus(NotificationReadStatus.READ));
        notificationRepository.saveAll(page.getContent());
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: notifyTrendingForFollowedTopics.
 */
    public void notifyTrendingForFollowedTopics(List<Topic> trendingTopics) {
        if (trendingTopics == null || trendingTopics.isEmpty()) {
            return;
        }
        for (Topic topic : trendingTopics) {
            List<FollowTopic> followers = followTopicRepository.findByTopicId(topic.getId());
            for (FollowTopic follow : followers) {
                User user = follow.getUser();
                if (notificationRepository.existsByUserIdAndTopicIdAndTriggerType(
                        user.getId(), topic.getId(), NotificationTriggerType.TRENDING_TOPIC)) {
                    continue;
                }
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .topic(topic)
                        .message("Topic \"" + topic.getName() + "\" is trending")
                        .triggerType(NotificationTriggerType.TRENDING_TOPIC)
                        .readStatus(NotificationReadStatus.UNREAD)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: notifyNewPapersForSubscriptions.
 */
    public void notifyNewPapersForSubscriptions(Set<Long> newPaperIds) {
        if (newPaperIds == null || newPaperIds.isEmpty()) {
            return;
        }
        Set<Long> notifiedUsers = new HashSet<>();
        for (Long paperId : newPaperIds) {
            Paper paper = paperRepository.findById(paperId).orElse(null);
            if (paper == null) {
                continue;
            }
            notifyJournalFollowers(paper, notifiedUsers);
            notifyTopicFollowers(paper, notifiedUsers);
        }
    }

/**
 * Xử lý nghiệp vụ: notifyJournalFollowers.
 */
    private void notifyJournalFollowers(Paper paper, Set<Long> notifiedUsers) {
        if (paper.getJournalRef() == null) {
            return;
        }
        Long journalId = paper.getJournalRef().getId();
        for (FollowJournal follow : followJournalRepository.findByJournalId(journalId)) {
            User user = follow.getUser();
            if (!notifiedUsers.add(user.getId())) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndPaperId(user.getId(), paper.getId())) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .paper(paper)
                    .journal(paper.getJournalRef())
                    .message("New paper in journal you follow: " + truncate(paper.getTitle(), 120))
                    .triggerType(NotificationTriggerType.NEW_PAPER)
                    .readStatus(NotificationReadStatus.UNREAD)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

/**
 * Xử lý nghiệp vụ: notifyTopicFollowers.
 */
    private void notifyTopicFollowers(Paper paper, Set<Long> notifiedUsers) {
        for (PaperTopic pt : paperTopicRepository.findByPaperId(paper.getId())) {
            Topic topic = pt.getTopic();
            for (FollowTopic follow : followTopicRepository.findByTopicId(topic.getId())) {
                User user = follow.getUser();
                if (!notifiedUsers.add(user.getId())) {
                    continue;
                }
                if (notificationRepository.existsByUserIdAndPaperId(user.getId(), paper.getId())) {
                    continue;
                }
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .paper(paper)
                        .topic(topic)
                        .message("New paper in topic \"" + topic.getName() + "\": " + truncate(paper.getTitle(), 80))
                        .triggerType(NotificationTriggerType.NEW_PAPER)
                        .readStatus(NotificationReadStatus.UNREAD)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

/**
 * Xử lý nghiệp vụ: truncate.
 */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

/**
 * Xử lý nghiệp vụ: requireUserId.
 */
    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
