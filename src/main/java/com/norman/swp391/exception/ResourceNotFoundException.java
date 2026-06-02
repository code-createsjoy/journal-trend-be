package com.norman.swp391.exception;

/**
 * Ngoại lệ khi không tìm thấy tài nguyên (HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Tạo ngoại lệ với thông báo tùy chỉnh.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Tạo ngoại lệ khi không tìm thấy bản ghi theo ID.
     */
    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id));
    }
}
