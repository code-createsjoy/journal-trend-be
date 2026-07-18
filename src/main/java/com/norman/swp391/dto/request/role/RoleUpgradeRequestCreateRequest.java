package com.norman.swp391.dto.request.role;

import com.norman.swp391.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu nộp đơn xin đổi role (chỉ cho phép STUDENT/RESEARCHER/LECTURER, kiểm tra ở service). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpgradeRequestCreateRequest {

    @NotNull(message = "Requested role is required")
    private UserRole requestedRole;

    /** Nội dung do Tiptap (FE) sinh ra, có thể là HTML — không giới hạn quá chặt. */
    @NotBlank(message = "Reason is required")
    @Size(max = 20000, message = "Reason is too long")
    private String reason;

    @Size(max = 500, message = "Proof URL is too long")
    private String proofUrl;
}
