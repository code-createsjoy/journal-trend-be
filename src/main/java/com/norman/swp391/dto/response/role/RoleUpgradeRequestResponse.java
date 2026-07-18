package com.norman.swp391.dto.response.role;

import com.norman.swp391.entity.enums.RoleRequestRejectionReason;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import com.norman.swp391.entity.enums.UserRole;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpgradeRequestResponse {

    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private UserRole currentRole;
    private UserRole requestedRole;
    private String reason;
    private String proofUrl;
    private RoleRequestStatus status;
    private RoleRequestRejectionReason rejectionReason;
    private String rejectionReasonText;
    private String reviewNote;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
