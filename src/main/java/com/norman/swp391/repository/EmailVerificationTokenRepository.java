package com.norman.swp391.repository;

import com.norman.swp391.entity.EmailVerificationToken;
import com.norman.swp391.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findByTokenAndUsedFalse(String token);

    List<EmailVerificationToken> findByUserAndUsedFalse(User user);
}
