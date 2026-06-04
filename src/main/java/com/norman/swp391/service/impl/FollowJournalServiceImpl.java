package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.journal.JournalResponse;
import com.norman.swp391.entity.FollowJournal;
import com.norman.swp391.entity.Journal;
import com.norman.swp391.entity.User;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.JournalMapper;
import com.norman.swp391.repository.FollowJournalRepository;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.FollowJournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Triển khai theo dõi tạp chí.
 */
@Service
@RequiredArgsConstructor
public class FollowJournalServiceImpl implements FollowJournalService {

    private final FollowJournalRepository followJournalRepository;
    private final JournalRepository journalRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: follow.
 */
    public void follow(Long journalId) {
        Long userId = requireUserId();
        if (followJournalRepository.existsByUserIdAndJournalId(userId, journalId)) {
            throw new BadRequestException("Already following this journal");
        }
        int max = appProperties.getSync().getMaxFollowJournalsPerUser();
        if (followJournalRepository.countByUserId(userId) >= max) {
            throw new BadRequestException(
                    "You have reached the maximum of " + max + " followed journals");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Journal journal =
                journalRepository.findById(journalId).orElseThrow(() -> new ResourceNotFoundException("Journal", journalId));
        followJournalRepository.save(FollowJournal.builder()
                .user(user)
                .journal(journal)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: unfollow.
 */
    public void unfollow(Long journalId) {
        Long userId = requireUserId();
        followJournalRepository
                .findByUserIdAndJournalId(userId, journalId)
                .ifPresent(followJournalRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listFollowed.
 */
    public List<JournalResponse> listFollowed() {
        Long userId = requireUserId();
        return followJournalRepository.findByUserId(userId).stream()
                .map(FollowJournal::getJournal)
                .map(JournalMapper::toResponse)
                .toList();
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
