package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.role.RoleChangeLogResponse;
import com.norman.swp391.dto.response.role.RoleUpgradeRequestResponse;
import com.norman.swp391.entity.RoleChangeLog;
import com.norman.swp391.entity.RoleUpgradeRequest;
import com.norman.swp391.entity.enums.RoleRequestRejectionReason;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

/**
 * Mapper RoleManagementMapper.
 */
@UtilityClass
public class RoleManagementMapper {

    public static RoleUpgradeRequestResponse toResponse(RoleUpgradeRequest request) {
        if (request == null) {
            return null;
        }
        return RoleUpgradeRequestResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userFullName(request.getUser().getFullName())
                .userEmail(request.getUser().getEmail())
                .currentRole(request.getUser().getRole())
                .requestedRole(request.getRequestedRole())
                .reason(request.getReason())
                .proofUrl(request.getProofUrl())
                .status(request.getStatus())
                .rejectionReason(request.getRejectionReason())
                .rejectionReasonText(resolveRejectionReasonText(request))
                .reviewNote(request.getReviewNote())
                .reviewedById(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
                .reviewedByName(request.getReviewedBy() != null ? request.getReviewedBy().getFullName() : null)
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .build();
    }

    /**
     * Văn bản lý do từ chối để FE hiển thị. Khi Admin chọn OTHER và có ghi chú tùy chỉnh
     * thì trả đúng nội dung đó (thay vì mô tả chung "Other reason"), ngược lại dùng mô tả enum.
     */
    private static String resolveRejectionReasonText(RoleUpgradeRequest request) {
        RoleRequestRejectionReason reason = request.getRejectionReason();
        if (reason == null) {
            return null;
        }
        if (reason == RoleRequestRejectionReason.OTHER && StringUtils.hasText(request.getReviewNote())) {
            return request.getReviewNote();
        }
        return reason.getDescription();
    }

    public static List<RoleUpgradeRequestResponse> toResponseList(List<RoleUpgradeRequest> requests) {
        return requests.stream().map(RoleManagementMapper::toResponse).toList();
    }

    public static RoleChangeLogResponse toLogResponse(RoleChangeLog log) {
        if (log == null) {
            return null;
        }
        return RoleChangeLogResponse.builder()
                .id(log.getId())
                .operatorId(log.getOperator().getId())
                .operatorName(log.getOperator().getFullName())
                .targetUserId(log.getTargetUser().getId())
                .targetUserName(log.getTargetUser().getFullName())
                .oldRole(log.getOldRole())
                .newRole(log.getNewRole())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static List<RoleChangeLogResponse> toLogResponseList(List<RoleChangeLog> logs) {
        return logs.stream().map(RoleManagementMapper::toLogResponse).toList();
    }
}
