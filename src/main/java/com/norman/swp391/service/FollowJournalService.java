package com.norman.swp391.service;

import com.norman.swp391.dto.response.journal.JournalResponse;
import java.util.List;

/**
 * Dịch vụ theo dõi tạp chí.
 */
public interface FollowJournalService {

/**
 * Xử lý nghiệp vụ: follow.
 */
    void follow(Long journalId);

/**
 * Xử lý nghiệp vụ: unfollow.
 */
    void unfollow(Long journalId);

/**
 * Danh sách: listFollowed.
 */
    List<JournalResponse> listFollowed();
}
