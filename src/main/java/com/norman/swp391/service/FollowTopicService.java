package com.norman.swp391.service;

import com.norman.swp391.dto.response.topic.TopicResponse;
import java.util.List;

/**
 * Dịch vụ theo dõi chủ đề.
 */
public interface FollowTopicService {

/**
 * Xử lý nghiệp vụ: follow.
 */
    void follow(Long topicId);

/**
 * Xử lý nghiệp vụ: unfollow.
 */
    void unfollow(Long topicId);

/**
 * Danh sách: listFollowed.
 */
    List<TopicResponse> listFollowed();
}
