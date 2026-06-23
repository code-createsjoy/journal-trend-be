package com.norman.swp391.service;
 
import com.norman.swp391.dto.response.author.AuthorResponse;
import java.util.List;
 
public interface FollowAuthorService {
 
    void follow(Long authorId);
 
    void unfollow(Long authorId);
 
    List<AuthorResponse> listFollowed();
}
