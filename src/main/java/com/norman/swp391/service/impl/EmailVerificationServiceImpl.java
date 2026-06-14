package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.entity.EmailVerificationToken;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.repository.EmailVerificationTokenRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.service.EmailService;
import com.norman.swp391.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public EmailVerificationToken createVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        int expiryMinutes = appProperties.getEmailVerificationExpirationMinutes();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(expiryMinutes))
                .used(false)
                .build();
        return tokenRepository.save(verificationToken);
    }

    @Override
    @Transactional
    public String verifyToken(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Mã xác thực không tồn tại."));

        if (verificationToken.isUsed()) {
            throw new BadRequestException("Mã xác thực này đã được sử dụng.");
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Mã xác thực đã hết hạn.");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        user.setVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        tokenRepository.save(verificationToken);
        log.info("User {} verified successfully with token {}", user.getEmail(), token);
        return user.getEmail();
    }

    @Override
    @Transactional
    public void resendVerificationToken(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (user.isVerified()) {
            throw new BadRequestException("Tài khoản đã được xác thực email trước đó.");
        }

        // Invalidate previous tokens
        List<EmailVerificationToken> activeTokens = tokenRepository.findByUserAndUsedFalse(user);
        for (EmailVerificationToken t : activeTokens) {
            t.setUsed(true);
        }
        tokenRepository.saveAll(activeTokens);

        // Create new token and send email
        EmailVerificationToken newToken = createVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), newToken.getToken());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserVerified(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(u -> u.isVerified() || u.getRole() == UserRole.ADMIN || u.getRole() == UserRole.SUPER_ADMIN)
                .orElse(false);
    }
}
