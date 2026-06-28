package com.norman.swp391.service;

public interface EmailService {

    /**
     * Gửi email xác nhận kích hoạt tài khoản (Email Verification).
     *
     * @param toEmail   Email người nhận
     * @param fullName  Họ và tên người nhận
     * @param token     Mã xác nhận
     */
    void sendVerificationEmail(String toEmail, String fullName, String token);

    /**
     * Gửi email khôi phục mật khẩu.
     *
     * @param toEmail   Email người nhận
     * @param token     Mã token đặt lại mật khẩu
     */
    void sendPasswordResetEmail(String toEmail, String token);

    /**
     * Gửi email thông báo khi có bài báo mới trùng khớp với keyword, journal, hoặc author mà người dùng follow.
     *
     * @param toEmail   Email người nhận
     * @param fullName  Họ và tên người nhận
     * @param papers    Danh sách bài báo mới cần thông báo
     */
    void sendNewPaperNotificationsEmail(String toEmail, String fullName, java.util.List<com.norman.swp391.entity.Paper> papers);
}

