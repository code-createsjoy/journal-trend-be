package com.norman.swp391.dto.request.role;

import com.norman.swp391.entity.enums.RoleRequestRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lý do từ chối — chọn từ danh sách cố định (dropdown ở FE), không phải free text. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRequestRejectRequest {

    @NotNull(message = "Rejection reason is required")
    private RoleRequestRejectionReason rejectionReason;

    /**
     * Nội dung lý do chi tiết do Admin tự nhập, bắt buộc khi rejectionReason == OTHER.
     * Giới hạn 500 để notification message ("...Reason: " + customReason) không vượt cột message (1000).
     */
    @Size(max = 500, message = "Custom reason is too long")
    private String customReason;
}
