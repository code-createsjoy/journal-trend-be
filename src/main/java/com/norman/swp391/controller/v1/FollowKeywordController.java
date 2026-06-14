package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.service.FollowKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API theo dõi từ khóa (v1).
 */
@RestController
@RequestMapping("/api/v1/follow/keywords")
@RequiredArgsConstructor
public class FollowKeywordController {

    private final FollowKeywordService followKeywordService;

    @PostMapping("/{keywordId}")
/**
 * Xử lý nghiệp vụ: follow.
 */
    public ApiResponse<Void> follow(@PathVariable Long keywordId) {
        followKeywordService.follow(keywordId);
        return ApiResponse.okMessage("Keyword followed");
    }

    @DeleteMapping("/{keywordId}")
/**
 * Xử lý nghiệp vụ: unfollow.
 */
    public ApiResponse<Void> unfollow(@PathVariable Long keywordId) {
        followKeywordService.unfollow(keywordId);
        return ApiResponse.okMessage("Keyword unfollowed");
    }

    @GetMapping
/**
 * Danh sách: listFollowed.
 */
    public ApiResponse<List<KeywordResponse>> listFollowed() {
        return ApiResponse.ok(followKeywordService.listFollowed());
    }
}
