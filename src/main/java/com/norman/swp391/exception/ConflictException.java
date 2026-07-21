package com.norman.swp391.exception;

/**
 * Ngoại lệ 409 — thao tác hợp lệ nhưng xung đột với trạng thái hiện tại của hệ thống.
 */
public class ConflictException extends RuntimeException {

    /**
     * Tạo ngoại lệ với thông báo lỗi.
     */
    public ConflictException(String message) {
        super(message);
    }
}
