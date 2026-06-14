package com.norman.swp391.service;

import com.norman.swp391.dto.response.keyword.KeywordResponse;
import java.util.List;

public interface FollowKeywordService {
    void follow(Long keywordId);
    void unfollow(Long keywordId);
    List<KeywordResponse> listFollowed();
}
