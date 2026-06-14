package com.norman.swp391.service;

/**
 * Dịch vụ gửi email.
 */
public interface EmailService {

    /**
     * Gửi email xác nhận đăng ký tài khoản thành công.
     *
     * @param toEmail   Email người nhận
     * @param fullName  Họ và tên người nhận
     */
    void sendRegistrationConfirmEmail(String toEmail, String fullName);

    /**
     * Gửi email xác nhận kích hoạt tài khoản (Email Verification).
     *
     * @param toEmail   Email người nhận
     * @param fullName  Họ và tên người nhận
     * @param token     Mã xác nhận
     */
    void sendVerificationEmail(String toEmail, String fullName, String token);
}
