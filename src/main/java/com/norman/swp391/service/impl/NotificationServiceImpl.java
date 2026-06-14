package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.FollowJournal;
import com.norman.swp391.entity.FollowKeyword;
import com.norman.swp391.entity.Notification;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.NotificationReadStatus;
import com.norman.swp391.entity.enums.NotificationTriggerType;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.NotificationMapper;
import com.norman.swp391.repository.FollowJournalRepository;
import com.norman.swp391.repository.FollowKeywordRepository;
import com.norman.swp391.repository.NotificationRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
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
    private final FollowKeywordRepository followKeywordRepository;
    private final FollowJournalRepository followJournalRepository;
    private final PaperRepository paperRepository;
    private final PaperKeywordRepository paperKeywordRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listForCurrentUser(Pageable pageable) {
        Long userId = requireUserId();
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, NotificationMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional
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
    public void markAllAsRead() {
        Long userId = requireUserId();
        Page<Notification> page =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
        page.getContent().forEach(n -> n.setReadStatus(NotificationReadStatus.READ));
        notificationRepository.saveAll(page.getContent());
    }

    @Override
    @Transactional
    public void notifyTrendingForFollowedKeywords(List<Keyword> trendingKeywords) {
        if (trendingKeywords == null || trendingKeywords.isEmpty()) {
            return;
        }
        for (Keyword keyword : trendingKeywords) {
            List<FollowKeyword> followers = followKeywordRepository.findByKeywordId(keyword.getKeywordId());
            for (FollowKeyword follow : followers) {
                User user = follow.getUser();
                if (notificationRepository.existsByUserIdAndKeywordIdAndTriggerType(
                        user.getId(), keyword.getKeywordId(), NotificationTriggerType.TRENDING_KEYWORD)) {
                    continue;
                }
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .keyword(keyword)
                        .message("Keyword \"" + keyword.getTerm() + "\" is trending")
                        .triggerType(NotificationTriggerType.TRENDING_KEYWORD)
                        .readStatus(NotificationReadStatus.UNREAD)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    @Override
    @Transactional
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
            notifyKeywordFollowers(paper, notifiedUsers);
        }
    }

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

    private void notifyKeywordFollowers(Paper paper, Set<Long> notifiedUsers) {
        for (PaperKeyword pk : paperKeywordRepository.findByPaperId(paper.getId())) {
            Keyword keyword = pk.getKeyword();
            for (FollowKeyword follow : followKeywordRepository.findByKeywordId(keyword.getKeywordId())) {
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
                        .keyword(keyword)
                        .message("New paper with keyword \"" + keyword.getTerm() + "\": " + truncate(paper.getTitle(), 80))
                        .triggerType(NotificationTriggerType.NEW_PAPER)
                        .readStatus(NotificationReadStatus.UNREAD)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
