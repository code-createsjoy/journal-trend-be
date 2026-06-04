package com.norman.swp391.exception;

/**
 * Ngoại lệ 400.
 */
public class BadRequestException extends RuntimeException {

    /**
     * Tạo ngoại lệ với thông báo lỗi.
     */
    public BadRequestException(String message) {
        super(message);
    }
}

