package com.norman.swp391.entity.enums;

/**
 * Trạng thái kiểm duyệt metadata (BR-17, BR-97, BR-104, BR-105).
 */
public enum PaperReviewStatus {
    /** Hiển thị bình thường trên search/trend. */
    NONE,
    /** Metadata mâu thuẫn — chờ admin (BR-17). */
    PENDING_REVIEW,
    /** Admin đã xử lý (chuyển về NONE). */
    RESOLVED,
    /** Quá 30 ngày chưa xử lý — ẩn khỏi trend (BR-97). */
    EXPIRED
}
