package com.norman.swp391.controller.v1;
 
import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.service.FollowAuthorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
/**
 * API theo dõi tác giả (v1).
 */
@RestController
@RequestMapping("/api/v1/follow/authors")
@RequiredArgsConstructor
public class FollowAuthorController {
 
    private final FollowAuthorService followAuthorService;
 
    @PostMapping("/{authorId}")
    public ApiResponse<Void> follow(@PathVariable Long authorId) {
        followAuthorService.follow(authorId);
        return ApiResponse.okMessage("Author followed");
    }
 
    @DeleteMapping("/{authorId}")
    public ApiResponse<Void> unfollow(@PathVariable Long authorId) {
        followAuthorService.unfollow(authorId);
        return ApiResponse.okMessage("Author unfollowed");
    }
 
    @GetMapping
    public ApiResponse<List<AuthorResponse>> listFollowed() {
        return ApiResponse.ok(followAuthorService.listFollowed());
    }
}
