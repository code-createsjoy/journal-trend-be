package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.journal.JournalResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.service.FollowAuthorService;
import com.norman.swp391.service.FollowJournalService;
import com.norman.swp391.service.FollowKeywordService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API theo dõi cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class HelixFollowController {

    private final FollowKeywordService followKeywordService;
    private final FollowJournalService followJournalService;
    private final FollowAuthorService followAuthorService;

    /**
     * Xử lý API listFollowedTopics.
     */
    @GetMapping("/topics")
    public List<KeywordResponse> listFollowedTopics() {
        return followKeywordService.listFollowed();
    }

    /**
     * Xử lý API followTopic.
     */
    @PostMapping("/topics/{topicId}")
    public void followTopic(@PathVariable("topicId") Long topicId) {
        followKeywordService.follow(topicId);
    }

    /**
     * Xử lý API unfollowTopic.
     */
    @DeleteMapping("/topics/{topicId}")
    public void unfollowTopic(@PathVariable("topicId") Long topicId) {
        followKeywordService.unfollow(topicId);
    }

    /**
     * Xử lý API listFollowedJournals.
     */
    @GetMapping("/journals")
    public List<JournalResponse> listFollowedJournals() {
        return followJournalService.listFollowed();
    }

    /**
     * Xử lý API followJournal.
     */
    @PostMapping("/journals/{journalId}")
    public void followJournal(@PathVariable Long journalId) {
        followJournalService.follow(journalId);
    }

    /**
     * Xử lý API unfollowJournal.
     */
    @DeleteMapping("/journals/{journalId}")
    public void unfollowJournal(@PathVariable Long journalId) {
        followJournalService.unfollow(journalId);
    }

    /**
     * Xử lý API listFollowedAuthors.
     */
    @GetMapping("/authors")
    public List<AuthorResponse> listFollowedAuthors() {
        return followAuthorService.listFollowed();
    }

    /**
     * Xử lý API followAuthor.
     */
    @PostMapping("/authors/{authorId}")
    public void followAuthor(@PathVariable Long authorId) {
        followAuthorService.follow(authorId);
    }

    /**
     * Xử lý API unfollowAuthor.
     */
    @DeleteMapping("/authors/{authorId}")
    public void unfollowAuthor(@PathVariable Long authorId) {
        followAuthorService.unfollow(authorId);
    }
}


