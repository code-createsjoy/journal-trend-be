package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.entity.FollowAuthor;
import com.norman.swp391.entity.FollowJournal;
import com.norman.swp391.entity.FollowKeyword;
import com.norman.swp391.entity.Notification;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.NotificationReadStatus;
import com.norman.swp391.entity.enums.NotificationTriggerType;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.NotificationMapper;
import com.norman.swp391.repository.FollowAuthorRepository;
import com.norman.swp391.repository.FollowJournalRepository;
import com.norman.swp391.repository.FollowKeywordRepository;
import com.norman.swp391.repository.NotificationRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.NotificationService;
import com.norman.swp391.service.EmailService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
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
    private final FollowAuthorRepository followAuthorRepository;
    private final PaperRepository paperRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final EmailService emailService;

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
        
        // Fast path: if there are no followers at all, skip the 100,000+ queries.
        if (followKeywordRepository.count() == 0 
                && followJournalRepository.count() == 0 
                && followAuthorRepository.count() == 0) {
            return;
        }

        Map<User, Set<Paper>> userNewPapersMap = new HashMap<>();

        for (Long paperId : newPaperIds) {
            Paper paper = paperRepository.findById(paperId).orElse(null);
            if (paper == null) {
                continue;
            }
            Set<Long> notifiedUsers = new HashSet<>();
            notifyJournalFollowers(paper, notifiedUsers, userNewPapersMap);
            notifyKeywordFollowers(paper, notifiedUsers, userNewPapersMap);
            notifyAuthorFollowers(paper, notifiedUsers, userNewPapersMap);
        }

        // Send email notifications
        userNewPapersMap.forEach((user, papers) -> {
            if (user.getEmail() != null) {
                emailService.sendNewPaperNotificationsEmail(
                        user.getEmail(),
                        user.getFullName(),
                        new ArrayList<>(papers)
                );
            }
        });
    }

    private void notifyJournalFollowers(Paper paper, Set<Long> notifiedUsers, Map<User, Set<Paper>> userNewPapersMap) {
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
            userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
        }
    }

    private void notifyKeywordFollowers(Paper paper, Set<Long> notifiedUsers, Map<User, Set<Paper>> userNewPapersMap) {
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
                userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
            }
        }
    }

    private void notifyAuthorFollowers(Paper paper, Set<Long> notifiedUsers, Map<User, Set<Paper>> userNewPapersMap) {
        for (PaperAuthor pa : paperAuthorRepository.findByPaperId(paper.getId())) {
            Author author = pa.getAuthor();
            for (FollowAuthor follow : followAuthorRepository.findByAuthorId(author.getId())) {
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
                        .author(author)
                        .message("New paper from author you follow: " + author.getName() + " - " + truncate(paper.getTitle(), 80))
                        .triggerType(NotificationTriggerType.NEW_PAPER)
                        .readStatus(NotificationReadStatus.UNREAD)
                        .createdAt(LocalDateTime.now())
                        .build());
                userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long notificationId) {
        Long userId = requireUserId();
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public void deleteMultiple(List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        Long userId = requireUserId();
        notificationRepository.deleteByUserIdAndIds(userId, notificationIds);
    }

    @Override
    @Transactional
    public void deleteAll() {
        Long userId = requireUserId();
        notificationRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteAllRead() {
        Long userId = requireUserId();
        notificationRepository.deleteByUserIdAndReadStatus(userId, NotificationReadStatus.READ);
    }

    @Override
    @Transactional
    public void markMultipleAsRead(List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        Long userId = requireUserId();
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        List<Notification> toSave = notifications.stream()
                .filter(n -> n.getUser().getId().equals(userId))
                .peek(n -> n.setReadStatus(NotificationReadStatus.READ))
                .toList();
        notificationRepository.saveAll(toSave);
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
