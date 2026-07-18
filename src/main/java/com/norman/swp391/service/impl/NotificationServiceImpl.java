package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
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
import com.norman.swp391.entity.enums.RoleRequestRejectionReason;
import com.norman.swp391.entity.enums.UserRole;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ thông báo.
 */
@Slf4j
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
    private final AppProperties appProperties;

    /** Danh sách thông báo của user hiện tại, mới nhất trước, có phân trang. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listForCurrentUser(Pageable pageable) {
        Long userId = requireUserId();
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, NotificationMapper.toResponseList(page.getContent()));
    }

    /** Đánh dấu đã đọc 1 thông báo (chỉ nếu thuộc về user hiện tại). */
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

    /** Đánh dấu tất cả thông báo của user hiện tại là đã đọc. */
    @Override
    @Transactional
    public void markAllAsRead() {
        Long userId = requireUserId();
        Page<Notification> page =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
        page.getContent().forEach(n -> n.setReadStatus(NotificationReadStatus.READ));
        notificationRepository.saveAll(page.getContent());
    }

    /**
     * Tạo thông báo "keyword đang trending" cho user đang follow keyword đó
     * (chỉ nếu user bật notifyKeywords, và chưa từng được thông báo về keyword này trước đó).
     */
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
                if (!user.isNotifyKeywords()) {
                    continue;
                }
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

    /**
     * Tạo thông báo (in-app + gom email digest) cho các paper mới vừa sync, dựa trên 3 kiểu follow:
     * theo dõi Journal (khối A), theo dõi Keyword (khối B), theo dõi Author (khối C).
     * Mỗi khối chỉ tạo notification in-app nếu user bật cờ tương ứng (notifyJournals/Keywords/Authors),
     * nhưng vẫn gom vào email digest bất kể cờ đó (email có cờ riêng: notifyEmail, check ở bước 7).
     */
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
                        String key = user.getId() + "-" + paperId;
                        if (existingNotifKeys.contains(key)) {
                            continue;
                        }
                        // Email digest độc lập với công tắc in-app; dedup theo Set<Paper> mỗi user.
                        userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
                        // In-app: mỗi user tối đa 1 notification/paper, chỉ tạo khi bật notifyJournals.
                        if (user.isNotifyJournals() && notifiedUsersForPaper.add(user.getId())) {
                            notificationsToSave.add(Notification.builder()
                                    .user(user)
                                    .paper(paper)
                                    .journal(paper.getJournalRef())
                                    .message("New paper in journal you follow " + paper.getJournalRef().getName() + ": " + truncate(paper.getTitle(), 80))
                                    .triggerType(NotificationTriggerType.NEW_PAPER)
                                    .readStatus(NotificationReadStatus.UNREAD)
                                    .createdAt(LocalDateTime.now())
                                    .build());
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
                            String key = user.getId() + "-" + paperId;
                            if (existingNotifKeys.contains(key)) {
                                continue;
                            }
                            userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
                            if (user.isNotifyKeywords() && notifiedUsersForPaper.add(user.getId())) {
                                notificationsToSave.add(Notification.builder()
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
                            String key = user.getId() + "-" + paperId;
                            if (existingNotifKeys.contains(key)) {
                                continue;
                            }
                            userNewPapersMap.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(paper);
                            if (user.isNotifyAuthors() && notifiedUsersForPaper.add(user.getId())) {
                                notificationsToSave.add(Notification.builder()
                                        .user(user)
                                        .paper(paper)
                                        .author(author)
                                        .message("New paper from author you follow: " + author.getName() + " - " + truncate(paper.getTitle(), 80))
                                        .triggerType(NotificationTriggerType.NEW_PAPER)
                                        .readStatus(NotificationReadStatus.UNREAD)
                                        .createdAt(LocalDateTime.now())
                                        .build());
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
            if (user.getEmail() != null && user.isNotifyEmail()) {
                emailService.sendNewPaperNotificationsEmail(
                        user.getEmail(),
                        user.getFullName(),
                        new ArrayList<>(papersList)
                );
            }
        });
    }

    /** Xóa 1 thông báo (chỉ nếu thuộc về user hiện tại). */
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

    /** Xóa nhiều thông báo cùng lúc theo danh sách id (chỉ của user hiện tại). */
    @Override
    @Transactional
    public void deleteMultiple(List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        Long userId = requireUserId();
        notificationRepository.deleteByUserIdAndIds(userId, notificationIds);
    }

    /** Xóa toàn bộ thông báo của user hiện tại. */
    @Override
    @Transactional
    public void deleteAll() {
        Long userId = requireUserId();
        notificationRepository.deleteByUserId(userId);
    }

    /** Xóa các thông báo đã đọc của user hiện tại (giữ lại thông báo chưa đọc). */
    @Override
    @Transactional
    public void deleteAllRead() {
        Long userId = requireUserId();
        notificationRepository.deleteByUserIdAndReadStatus(userId, NotificationReadStatus.READ);
    }

    /** Đánh dấu đã đọc nhiều thông báo cùng lúc theo danh sách id (chỉ của user hiện tại). */
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

    /** Cắt ngắn text (VD title paper) nếu quá dài, thêm "…" ở cuối. */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** BR-70: xóa các notification cũ hơn ngưỡng retention (mặc định 90 ngày), chạy định kỳ qua scheduler. */
    @Override
    @Transactional
    public void purgeOldNotifications() {
        int retentionDays = appProperties.getSync().getNotificationRetentionDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = notificationRepository.deleteByCreatedAtBefore(threshold);
        if (deleted > 0) {
            log.info("[NOTIF_PURGE] Deleted {} notification(s) older than {} days", deleted, retentionDays);
        }
    }

    /** Thông báo đơn xin đổi role đã được duyệt. */
    @Override
    @Transactional
    public void notifyRoleRequestApproved(User targetUser, UserRole newRole) {
        notificationRepository.save(Notification.builder()
                .user(targetUser)
                .message("Your role upgrade request has been approved. Your new role is " + newRole.name() + ".")
                .triggerType(NotificationTriggerType.SYSTEM)
                .readStatus(NotificationReadStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Thông báo đơn xin đổi role bị từ chối, kèm lý do (tiếng Anh, chọn từ dropdown cố định). */
    @Override
    @Transactional
    public void notifyRoleRequestRejected(User targetUser, RoleRequestRejectionReason rejectionReason) {
        notificationRepository.save(Notification.builder()
                .user(targetUser)
                .message("Your role upgrade request has been rejected. Reason: " + rejectionReason.getDescription())
                .triggerType(NotificationTriggerType.SYSTEM)
                .readStatus(NotificationReadStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Lấy userId của user đang đăng nhập, throw nếu chưa xác thực. */
    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
