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
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        log.info("Starting asynchronous email verification sending process for: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Xác thực địa chỉ Email của bạn");

            String verifyUrl = appProperties.getBackendBaseUrl() + "/auth/verify?token=" + token;

            String htmlContent = buildVerificationHtmlContent(fullName, verifyUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent email verification to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email verification to: {}", toEmail, e);
        }
    }

    private String buildVerificationHtmlContent(String fullName, String verifyUrl) {
        int currentYear = Year.now().getValue();
        int expiryHours = appProperties.getEmailVerificationExpirationMinutes() / 60;
        return """
               <!DOCTYPE html>
               <html>
               <head>
                   <meta charset="UTF-8">
                   <title>Xác thực địa chỉ Email</title>
                   <style>
                       body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 0; color: #1f2937; }
                       .container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; }
                       .header { background-color: #f3f4f6; padding: 24px; text-align: center; border-bottom: 1px solid #e5e7eb; }
                       .logo { font-size: 24px; font-weight: bold; color: #6366f1; text-transform: uppercase; letter-spacing: 0.05em; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
                       .logo-symbol { background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%); width: 32px; height: 32px; border-radius: 8px; display: inline-block; vertical-align: middle; }
                       .content { padding: 32px; }
                       h2 { color: #4f46e5; margin-top: 0; font-size: 20px; font-weight: 700; }
                       p { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px; }
                       .btn-wrapper { text-align: center; margin-top: 30px; margin-bottom: 30px; }
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
                           <p>Cảm ơn bạn đã đăng ký tài khoản trên hệ thống <strong>JournalTrend (Helix Analytics)</strong>.</p>
                           <p>Vui lòng xác thực địa chỉ email của bạn để hoàn tất đăng ký bằng cách click vào liên kết bên dưới:</p>
                           <div class="btn-wrapper">
                               <a href="%s" class="btn" target="_blank">Xác thực Email</a>
                           </div>
                           <p>Hoặc bạn có thể sao chép liên kết bên dưới và dán vào thanh địa chỉ của trình duyệt:</p>
                           <p style="word-break: break-all; font-size: 13px;"><a href="%s" target="_blank" style="color: #6366f1; text-decoration: none;">%s</a></p>
                           <p>Liên kết xác nhận này sẽ hết hạn sau %d giờ.</p>
                           <p>Nếu bạn không đăng ký tài khoản này, vui lòng bỏ qua email này.</p>
                           <p style="margin-bottom: 0;">Trân trọng,<br><strong>JournalTrend Team</strong></p>
                       </div>
                       <div class="footer">
                           &copy; %d Helix Analytics. All rights reserved.
                       </div>
                   </div>
               </body>
               </html>
               """.formatted(fullName, verifyUrl, verifyUrl, verifyUrl, expiryHours, currentYear);
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        log.info("Starting asynchronous password reset email sending process for: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Đặt lại mật khẩu của bạn");

            String resetUrl = appProperties.getFrontendBaseUrl() + "/reset-password?token=" + token;
            String htmlContent = buildPasswordResetHtmlContent(resetUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent password reset email to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
        }
    }

    private String buildPasswordResetHtmlContent(String resetUrl) {
        int currentYear = Year.now().getValue();
        int expiryMinutes = appProperties.getPasswordResetExpirationMinutes();
        return """
               <!DOCTYPE html>
               <html>
               <head>
                   <meta charset="UTF-8">
                   <title>Đặt lại mật khẩu của bạn</title>
                   <style>
                       body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 0; color: #1f2937; }
                       .container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; }
                       .header { background-color: #f3f4f6; padding: 24px; text-align: center; border-bottom: 1px solid #e5e7eb; }
                       .logo { font-size: 24px; font-weight: bold; color: #6366f1; text-transform: uppercase; letter-spacing: 0.05em; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
                       .logo-symbol { background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%); width: 32px; height: 32px; border-radius: 8px; display: inline-block; vertical-align: middle; }
                       .content { padding: 32px; }
                       h2 { color: #4f46e5; margin-top: 0; font-size: 20px; font-weight: 700; }
                       p { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px; }
                       .btn-wrapper { text-align: center; margin-top: 30px; margin-bottom: 30px; }
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
                           <h2>Yêu cầu đặt lại mật khẩu,</h2>
                           <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với địa chỉ email này trên hệ thống <strong>JournalTrend (Helix Analytics)</strong>.</p>
                           <p>Vui lòng click vào nút bên dưới để tiến hành đặt lại mật khẩu mới:</p>
                           <div class="btn-wrapper">
                               <a href="%s" class="btn" target="_blank">Đặt lại mật khẩu</a>
                           </div>
                           <p>Hoặc bạn có thể sao chép liên kết bên dưới và dán vào thanh địa chỉ của trình duyệt:</p>
                           <p style="word-break: break-all; font-size: 13px;"><a href="%s" target="_blank" style="color: #6366f1; text-decoration: none;">%s</a></p>
                           <p>Yêu cầu này sẽ hết hạn sau %d phút.</p>
                           <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.</p>
                           <p style="margin-bottom: 0;">Trân trọng,<br><strong>JournalTrend Team</strong></p>
                       </div>
                       <div class="footer">
                           &copy; %d Helix Analytics. All rights reserved.
                       </div>
                   </div>
               </body>
               </html>
               """.formatted(resetUrl, resetUrl, resetUrl, expiryMinutes, currentYear);
    }

    @Async
    @Override
    public void sendNewPaperNotificationsEmail(String toEmail, String fullName, java.util.List<com.norman.swp391.entity.Paper> papers) {
        log.info("Starting asynchronous new papers email notification sending process for: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Thông báo: Có bài báo mới liên quan đến danh mục bạn theo dõi");

            String htmlContent = buildNewPapersHtmlContent(fullName, papers);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent new papers email notification to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send new papers email notification to: {}", toEmail, e);
        }
    }

    private String buildNewPapersHtmlContent(String fullName, java.util.List<com.norman.swp391.entity.Paper> papers) {
        int currentYear = Year.now().getValue();
        StringBuilder papersListHtml = new StringBuilder();
        
        for (com.norman.swp391.entity.Paper paper : papers) {
            String title = paper.getTitle();
            String journal = paper.getJournal() != null ? paper.getJournal() : "N/A";
            String date = paper.getPublicationDate() != null ? paper.getPublicationDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
            String link = paper.getSourceUrl() != null ? paper.getSourceUrl() : "#";
            String abstractSnippet = paper.getAbstractText() != null ? 
                (paper.getAbstractText().length() > 200 ? paper.getAbstractText().substring(0, 197) + "..." : paper.getAbstractText()) 
                : "Không có tóm tắt.";

            papersListHtml.append(String.format("""
                <div style="margin-bottom: 24px; padding: 16px; border: 1px solid #e5e7eb; border-radius: 8px; background-color: #f9fafb;">
                    <h3 style="margin-top: 0; margin-bottom: 8px; font-size: 16px; color: #1f2937;">
                        <a href="%s" target="_blank" style="color: #4f46e5; text-decoration: none; font-weight: 600;">%s</a>
                    </h3>
                    <div style="font-size: 13px; color: #6b7280; margin-bottom: 8px;">
                        <strong>Journal:</strong> %s &nbsp;|&nbsp; <strong>Xuất bản ngày:</strong> %s
                    </div>
                    <p style="font-size: 14px; line-height: 1.5; color: #4b5563; margin: 0;">
                        %s
                    </p>
                </div>
            """, link, title, journal, date, abstractSnippet));
        }

        return """
               <!DOCTYPE html>
               <html>
               <head>
                   <meta charset="UTF-8">
                   <title>Thông báo bài báo mới</title>
                   <style>
                       body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 0; color: #1f2937; }
                       .container { max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; }
                       .header { background-color: #f3f4f6; padding: 24px; text-align: center; border-bottom: 1px solid #e5e7eb; }
                       .logo { font-size: 24px; font-weight: bold; color: #6366f1; text-transform: uppercase; letter-spacing: 0.05em; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
                       .logo-symbol { background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%); width: 32px; height: 32px; border-radius: 8px; display: inline-block; vertical-align: middle; }
                       .content { padding: 32px; }
                       h2 { color: #4f46e5; margin-top: 0; font-size: 20px; font-weight: 700; }
                       p { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px; }
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
                           <p>Chúng tôi đã tìm thấy một số bài báo mới được cập nhật trên hệ thống trùng khớp với các keyword, journal hoặc author bạn đang theo dõi:</p>
                           
                           %s
                           
                           <p>Bạn có thể truy cập trang web để xem chi tiết các phân tích xu hướng mới nhất.</p>
                           <p style="margin-top: 24px; margin-bottom: 0;">Trân trọng,<br><strong>JournalTrend Team</strong></p>
                       </div>
                       <div class="footer">
                           &copy; %d Helix Analytics. All rights reserved.
                       </div>
                   </div>
               </body>
               </html>
               """.formatted(fullName, papersListHtml.toString(), currentYear);
    }
}
