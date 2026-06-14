package com.norman.swp391.controller.v1;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.auth.EmailVerificationStatusResponse;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.EmailVerificationToken;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.repository.EmailVerificationTokenRepository;
import com.norman.swp391.service.EmailVerificationService;
import com.norman.swp391.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    /**
     * Xác thực email thông qua token gửi từ mail.
     */
    @GetMapping("/auth/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        String loginUrl = appProperties.getFrontendBaseUrl() + "/login";
        try {
            String email = emailVerificationService.verifyToken(token);
            
            String redirectUrl = appProperties.getFrontendBaseUrl() + "/verify-email?email=" + email;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(redirectUrl))
                    .build();
        } catch (Exception ex) {
            String errorHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Xác thực thất bại - Helix Analytics</title>
                    <style>
                        body {
                            font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;
                            background-color: #0b0f19;
                            color: #f3f4f6;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            margin: 0;
                        }
                        .card {
                            background: rgba(17, 25, 40, 0.75);
                            backdrop-filter: blur(16px);
                            -webkit-backdrop-filter: blur(16px);
                            border: 1px solid rgba(255, 255, 255, 0.08);
                            border-radius: 24px;
                            padding: 48px;
                            text-align: center;
                            max-width: 450px;
                            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                        }
                        .icon-circle {
                            width: 80px;
                            height: 80px;
                            background: linear-gradient(135deg, #ef4444 0%%, #dc2626 100%%);
                            border-radius: 50%%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            margin: 0 auto 24px;
                            box-shadow: 0 0 20px rgba(239, 68, 68, 0.4);
                        }
                        .icon {
                            width: 40px;
                            height: 40px;
                            fill: #ffffff;
                        }
                        h1 {
                            font-size: 24px;
                            font-weight: 700;
                            margin: 0 0 16px;
                            background: linear-gradient(135deg, #ffffff 0%%, #fca5a5 100%%);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        p {
                            font-size: 15px;
                            line-height: 1.6;
                            color: #9ca3af;
                            margin: 0 0 32px;
                        }
                        .btn {
                            display: inline-block;
                            background: rgba(255, 255, 255, 0.08);
                            color: #ffffff;
                            font-weight: 600;
                            font-size: 14px;
                            padding: 12px 32px;
                            text-decoration: none;
                            border-radius: 12px;
                            border: 1px solid rgba(255, 255, 255, 0.1);
                            transition: all 0.2s;
                        }
                        .btn:hover {
                            background: rgba(255, 255, 255, 0.15);
                            transform: translateY(-2px);
                        }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="icon-circle">
                            <svg class="icon" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>
                        </div>
                        <h1>Xác thực thất bại</h1>
                        <p>%s</p>
                        <a href="%s" class="btn">Trở lại Đăng nhập</a>
                    </div>
                </body>
                </html>
                """.formatted(ex.getMessage(), loginUrl);
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML).body(errorHtml);
        }
    }

    /**
     * Polling kiểm tra trạng thái xác thực.
     */
    @GetMapping({"/api/auth/check-verification-status", "/api/v1/auth/check-verification-status"})
    public EmailVerificationStatusResponse checkVerificationStatus(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long userId) {
        
        boolean verified = false;
        if (email != null && !email.isBlank()) {
            verified = emailVerificationService.isUserVerified(email);
        } else if (userId != null) {
            verified = userRepository.findById(userId)
                    .map(User::isVerified)
                    .orElse(false);
        }
        return new EmailVerificationStatusResponse(verified);
    }

    /**
     * Gửi lại email xác thực.
     */
    @PostMapping({"/api/auth/resend-verification", "/api/v1/auth/resend-verification"})
    public ApiResponse<Void> resendVerification(
            @RequestParam(required = false) String email,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> body) {
        
        String targetEmail = email;
        if ((targetEmail == null || targetEmail.isBlank()) && body != null) {
            targetEmail = body.get("email");
        }
        
        if (targetEmail == null || targetEmail.isBlank()) {
            return ApiResponse.okMessage("Email is required");
        }
        
        emailVerificationService.resendVerificationToken(targetEmail);
        return ApiResponse.okMessage("Email xác thực đã được gửi lại thành công.");
    }
}
