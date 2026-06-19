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
}

