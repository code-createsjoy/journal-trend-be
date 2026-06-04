package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.response.journal.JournalResponse;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.service.FollowJournalService;
import com.norman.swp391.service.FollowTopicService;
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

    private final FollowTopicService followTopicService;
    private final FollowJournalService followJournalService;

    /**
     * Xử lý API listFollowedTopics.
     */
    @GetMapping("/topics")
    public List<TopicResponse> listFollowedTopics() {
        return followTopicService.listFollowed();
    }

    /**
     * Xử lý API followTopic.
     */
    @PostMapping("/topics/{topicId}")
    public void followTopic(@PathVariable Long topicId) {
        followTopicService.follow(topicId);
    }

    /**
     * Xử lý API unfollowTopic.
     */
    @DeleteMapping("/topics/{topicId}")
    public void unfollowTopic(@PathVariable Long topicId) {
        followTopicService.unfollow(topicId);
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
}


