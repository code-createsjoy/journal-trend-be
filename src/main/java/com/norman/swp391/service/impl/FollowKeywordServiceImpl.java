package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.entity.FollowKeyword;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.User;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.KeywordMapper;
import com.norman.swp391.repository.FollowKeywordRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.FollowKeywordService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowKeywordServiceImpl implements FollowKeywordService {

    private final FollowKeywordRepository followKeywordRepository;
    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void follow(Long keywordId) {
        Long userId = requireUserId();
        if (followKeywordRepository.existsByUserIdAndKeywordId(userId, keywordId)) {
            throw new BadRequestException("Already following this keyword");
        }
        int max = appProperties.getSync().getMaxFollowKeywordsPerUser();
        if (followKeywordRepository.countByUserId(userId) >= max) {
            throw new BadRequestException("You have reached the maximum of " + max + " followed keywords");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword", keywordId));
        followKeywordRepository.save(FollowKeyword.builder()
                .user(user)
                .keyword(keyword)
                .followedAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public void unfollow(Long keywordId) {
        Long userId = requireUserId();
        followKeywordRepository.findByUserIdAndKeywordId(userId, keywordId)
                .ifPresent(followKeywordRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeywordResponse> listFollowed() {
        Long userId = requireUserId();
        return followKeywordRepository.findByUserId(userId).stream()
                .map(FollowKeyword::getKeyword)
                .map(KeywordMapper::toResponse)
                .toList();
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
