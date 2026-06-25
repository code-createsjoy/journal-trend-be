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

        // 1. Kiểm tra nhanh và load tất cả follows. Nếu không có ai follow, thoát sớm.
        List<FollowJournal> allFollowJournals = followJournalRepository.findAll();
        List<FollowKeyword> allFollowKeywords = followKeywordRepository.findAll();
        List<FollowAuthor> allFollowAuthors = followAuthorRepository.findAll();

        if (allFollowKeywords.isEmpty() && allFollowJournals.isEmpty() && allFollowAuthors.isEmpty()) {
            return;
        }

        // 2. Gom Map mapping follows theo ID thực thể để lookup O(1)
        Map<Long, List<FollowJournal>> journalFollowers = new HashMap<>();
        for (FollowJournal fj : allFollowJournals) {
            if (fj.getJournal() != null && fj.getJournal().getId() != null) {
                journalFollowers.computeIfAbsent(fj.getJournal().getId(), k -> new ArrayList<>()).add(fj);
            }
        }

        Map<Long, List<FollowKeyword>> keywordFollowers = new HashMap<>();
        for (FollowKeyword fk : allFollowKeywords) {
            if (fk.getKeyword() != null && fk.getKeyword().getKeywordId() != null) {
                keywordFollowers.computeIfAbsent(fk.getKeyword().getKeywordId(), k -> new ArrayList<>()).add(fk);
            }
        }

        Map<Long, List<FollowAuthor>> authorFollowers = new HashMap<>();
        for (FollowAuthor fa : allFollowAuthors) {
            if (fa.getAuthor() != null && fa.getAuthor().getId() != null) {
                authorFollowers.computeIfAbsent(fa.getAuthor().getId(), k -> new ArrayList<>()).add(fa);
            }
        }

        // 3. Load 1000 papers và các quan hệ (Keywords, Authors) bằng Bulk Query
        List<Paper> papers = paperRepository.findAllById(newPaperIds);
        if (papers.isEmpty()) {
            return;
        }
        List<Long> paperIdsList = papers.stream().map(Paper::getId).toList();

        List<PaperKeyword> paperKeywords = paperKeywordRepository.findByPaperIdInWithKeyword(paperIdsList);
        List<PaperAuthor> paperAuthors = paperAuthorRepository.findByPaperIdInWithAuthor(paperIdsList);

        // Group quan hệ theo paperId
        Map<Long, List<Keyword>> paperKeywordsMap = new HashMap<>();
        for (PaperKeyword pk : paperKeywords) {
            if (pk.getPaper() != null && pk.getKeyword() != null) {
                paperKeywordsMap.computeIfAbsent(pk.getPaper().getId(), k -> new ArrayList<>()).add(pk.getKeyword());
            }
        }

        Map<Long, List<Author>> paperAuthorsMap = new HashMap<>();
        for (PaperAuthor pa : paperAuthors) {
            if (pa.getPaper() != null && pa.getAuthor() != null) {
                paperAuthorsMap.computeIfAbsent(pa.getPaper().getId(), k -> new ArrayList<>()).add(pa.getAuthor());
            }
        }

        // 4. Load tất cả thông báo đã có của các paper này để tránh trùng lặp
        List<Object[]> existingNotifsRaw = notificationRepository.findUserIdAndPaperIdByPaperIdIn(paperIdsList);
        Set<String> existingNotifKeys = new HashSet<>();
        for (Object[] row : existingNotifsRaw) {
            if (row[0] != null && row[1] != null) {
                existingNotifKeys.add(row[0] + "-" + row[1]);
            }
        }

        // 5. Khớp nối in-memory và gom danh sách cần insert
        Map<User, Set<Paper>> userNewPapersMap = new HashMap<>();
        List<Notification> notificationsToSave = new ArrayList<>();

        for (Paper paper : papers) {
            Long paperId = paper.getId();
            Set<Long> notifiedUsersForPaper = new HashSet<>();

            // A. Theo dõi Journal
            if (paper.getJournalRef() != null) {
                Long journalId = paper.getJournalRef().getId();
                List<FollowJournal> fjs = journalFollowers.get(journalId);
                if (fjs != null) {
                    for (FollowJournal fj : fjs) {
                        User user = fj.getUser();
                        if (notifiedUsersForPaper.add(user.getId())) {
                            String key = user.getId() + "-" + paperId;
                            if (!existingNotifKeys.contains(key)) {
                                notificationsToSave.add(Notification.builder()
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
                    }
                }
            }

            // B. Theo dõi Keyword
            List<Keyword> keywords = paperKeywordsMap.get(paperId);
            if (keywords != null) {
                for (Keyword keyword : keywords) {
                    List<FollowKeyword> fks = keywordFollowers.get(keyword.getKeywordId());
                    if (fks != null) {
                        for (FollowKeyword fk : fks) {
                            User user = fk.getUser();
                            if (notifiedUsersForPaper.add(user.getId())) {
                                String key = user.getId() + "-" + paperId;
                                if (!existingNotifKeys.contains(key)) {
                                    notificationsToSave.add(Notification.builder()
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
                    }
                }
            }

            // C. Theo dõi Author
            List<Author> authors = paperAuthorsMap.get(paperId);
            if (authors != null) {
                for (Author author : authors) {
                    List<FollowAuthor> fas = authorFollowers.get(author.getId());
                    if (fas != null) {
                        for (FollowAuthor fa : fas) {
                            User user = fa.getUser();
                            if (notifiedUsersForPaper.add(user.getId())) {
                                String key = user.getId() + "-" + paperId;
                                if (!existingNotifKeys.contains(key)) {
                                    notificationsToSave.add(Notification.builder()
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
                    }
                }
            }
        }

        // 6. Bulk Save tất cả Notification
        if (!notificationsToSave.isEmpty()) {
            notificationRepository.saveAll(notificationsToSave);
        }

        // 7. Gửi mail bất đồng bộ (Async) cho từng user nhận được bài báo mới
        userNewPapersMap.forEach((user, papersList) -> {
            if (user.getEmail() != null) {
                emailService.sendNewPaperNotificationsEmail(
                        user.getEmail(),
                        user.getFullName(),
                        new ArrayList<>(papersList)
                );
            }
        });
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
