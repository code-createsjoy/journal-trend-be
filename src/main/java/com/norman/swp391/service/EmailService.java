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
}
