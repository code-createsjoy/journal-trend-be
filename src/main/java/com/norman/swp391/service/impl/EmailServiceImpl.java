package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * Triển khai dịch vụ gửi email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Async
    @Override
    public void sendRegistrationConfirmEmail(String toEmail, String fullName) {
        log.info("Starting asynchronous email sending process for: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Đăng ký tài khoản thành công");

            String loginUrl = appProperties.getFrontendBaseUrl() + "/login";
            String registerTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            String htmlContent = buildHtmlContent(fullName, toEmail, registerTime, loginUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent registration confirmation email to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send registration confirmation email to: {}", toEmail, e);
        }
    }

    /**
     * Tạo nội dung email dạng HTML với thiết kế giao diện hiện đại.
     */
    private String buildHtmlContent(String fullName, String email, String registerTime, String loginUrl) {
        int currentYear = Year.now().getValue();
        return """
               <!DOCTYPE html>
               <html>
               <head>
                   <meta charset="UTF-8">
                   <title>Đăng ký tài khoản thành công</title>
                   <style>
                       body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 0; color: #1f2937; }
                       .container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; }
                       .header { background-color: #f3f4f6; padding: 24px; text-align: center; border-bottom: 1px solid #e5e7eb; }
                       .logo { font-size: 24px; font-weight: bold; color: #6366f1; text-transform: uppercase; letter-spacing: 0.05em; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
                       .logo-symbol { background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%); width: 32px; height: 32px; border-radius: 8px; display: inline-block; vertical-align: middle; }
                       .content { padding: 32px; }
                       h2 { color: #4f46e5; margin-top: 0; font-size: 20px; font-weight: 700; }
                       p { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px; }
                       .info-table { width: 100%%; border-collapse: collapse; margin-bottom: 30px; border-radius: 8px; overflow: hidden; border: 1px solid #e5e7eb; }
                       .info-table td { padding: 12px 16px; font-size: 14px; border-bottom: 1px solid #e5e7eb; }
                       .info-table td.label { font-weight: 600; color: #374151; width: 35%%; background-color: #f9fafb; }
                       .info-table td.value { color: #4b5563; }
                       .info-table tr:last-child td { border-bottom: none; }
                       .btn-wrapper { text-align: center; margin-bottom: 32px; }
                       .btn { display: inline-block; background: linear-gradient(135deg, #6366f1 0%%, #8b5cf6 100%%); color: #ffffff !important; font-weight: 600; font-size: 15px; padding: 12px 32px; text-decoration: none; border-radius: 8px; box-shadow: 0 4px 10px rgba(99, 102, 241, 0.3); transition: transform 0.2s; }
                       .footer { padding: 24px 32px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; text-align: center; font-size: 12px; color: #9ca3af; }
                   </style>
               </head>
               <body>
                   <div class="container">
                       <div class="header">
                           <div class="logo">
                               <span class="logo-symbol"></span>
                               <span style="margin-left: 8px;">Helix Analytics</span>
                           </div>
                       </div>
                       <div class="content">
                           <h2>Xin chào %s,</h2>
                           <p>Bạn đã đăng ký tài khoản thành công trên hệ thống <strong>JournalTrend (Helix Analytics)</strong>.</p>
                           <p>Thông tin tài khoản đăng ký của bạn:</p>
                           <table class="info-table">
                               <tr>
                                   <td class="label">Email</td>
                                   <td class="value">%s</td>
                               </tr>
                               <tr>
                                   <td class="label">Thời gian đăng ký</td>
                                   <td class="value">%s</td>
                               </tr>
                           </table>
                           <div class="btn-wrapper">
                               <a href="%s" class="btn" target="_blank">Đăng nhập ngay</a>
                           </div>
                           <p>Cảm ơn bạn đã sử dụng dịch vụ của chúng tôi.</p>
                           <p style="margin-bottom: 0;">Trân trọng,<br><strong>JournalTrend Team</strong></p>
                       </div>
                       <div class="footer">
                           &copy; %d Helix Analytics. All rights reserved.
                       </div>
                   </div>
               </body>
               </html>
               """.formatted(fullName, email, registerTime, loginUrl, currentYear);
    }
}
