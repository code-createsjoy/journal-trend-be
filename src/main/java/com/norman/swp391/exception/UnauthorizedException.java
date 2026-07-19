package com.norman.swp391.exception;

/**
 * Ngoại lệ 401 — chưa xác thực (chưa đăng nhập hoặc token hết hạn/không hợp lệ).
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Tạo ngoại lệ với thông báo lỗi.
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
