package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.service.FollowTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API theo dõi chủ đề (v1).
 */
@RestController
@RequestMapping("/api/v1/follow/topics")
@RequiredArgsConstructor
public class FollowTopicController {

    private final FollowTopicService followTopicService;

    @PostMapping("/{topicId}")
/**
 * Xử lý nghiệp vụ: follow.
 */
    public ApiResponse<Void> follow(@PathVariable Long topicId) {
        followTopicService.follow(topicId);
        return ApiResponse.okMessage("Topic followed");
    }

    @DeleteMapping("/{topicId}")
/**
 * Xử lý nghiệp vụ: unfollow.
 */
    public ApiResponse<Void> unfollow(@PathVariable Long topicId) {
        followTopicService.unfollow(topicId);
        return ApiResponse.okMessage("Topic unfollowed");
    }

    @GetMapping
/**
 * Danh sách: listFollowed.
 */
    public ApiResponse<List<TopicResponse>> listFollowed() {
        return ApiResponse.ok(followTopicService.listFollowed());
    }
}
