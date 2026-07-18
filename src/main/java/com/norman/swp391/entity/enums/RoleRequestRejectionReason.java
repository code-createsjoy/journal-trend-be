package com.norman.swp391.entity.enums;

/** Danh sách lý do từ chối cố định (dropdown ở FE), nội dung tiếng Anh để hiển thị/notification. */
public enum RoleRequestRejectionReason {
    INSUFFICIENT_PROOF("The proof provided is insufficient or unclear"),
    INVALID_PROOF("The proof provided is invalid or could not be verified"),
    ROLE_MISMATCH("The requested role does not match the reason provided"),
    DUPLICATE_REQUEST("A similar request has already been submitted or reviewed"),
    OTHER("Other reason");

    private final String description;

    RoleRequestRejectionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
