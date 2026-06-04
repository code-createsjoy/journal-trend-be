package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.entity.FollowTopic;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.User;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.TopicMapper;
import com.norman.swp391.repository.FollowTopicRepository;
import com.norman.swp391.repository.TopicRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.FollowTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Triển khai theo dõi chủ đề.
 */
@Service
@RequiredArgsConstructor
public class FollowTopicServiceImpl implements FollowTopicService {

    private final FollowTopicRepository followTopicRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: follow.
 */
    public void follow(Long topicId) {
        Long userId = requireUserId();
        if (followTopicRepository.existsByUserIdAndTopicId(userId, topicId)) {
            throw new BadRequestException("Already following this topic");
        }
        int max = appProperties.getSync().getMaxFollowTopicsPerUser();
        if (followTopicRepository.countByUserId(userId) >= max) {
            throw new BadRequestException(
                    "You have reached the maximum of " + max + " followed topics");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Topic topic = topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
        followTopicRepository.save(FollowTopic.builder()
                .user(user)
                .topic(topic)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: unfollow.
 */
    public void unfollow(Long topicId) {
        Long userId = requireUserId();
        followTopicRepository
                .findByUserIdAndTopicId(userId, topicId)
                .ifPresent(followTopicRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listFollowed.
 */
    public List<TopicResponse> listFollowed() {
        Long userId = requireUserId();
        return followTopicRepository.findByUserId(userId).stream()
                .map(FollowTopic::getTopic)
                .map(TopicMapper::toResponse)
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
