package com.norman.swp391.service;

import com.norman.swp391.entity.EmailVerificationToken;
import com.norman.swp391.entity.User;

public interface EmailVerificationService {

    EmailVerificationToken createVerificationToken(User user);

    String verifyToken(String token);

    void resendVerificationToken(String email);

    boolean isUserVerified(String email);
}
