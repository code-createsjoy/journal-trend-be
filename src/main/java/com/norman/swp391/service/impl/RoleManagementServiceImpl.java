package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.role.RoleRequestApproveRequest;
import com.norman.swp391.dto.request.role.RoleRequestRejectRequest;
import com.norman.swp391.dto.request.role.RoleUpgradeRequestCreateRequest;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.role.RoleChangeLogResponse;
import com.norman.swp391.dto.response.role.RoleUpgradeRequestResponse;
import com.norman.swp391.entity.RoleChangeLog;
import com.norman.swp391.entity.RoleUpgradeRequest;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.RoleManagementMapper;
import com.norman.swp391.repository.RoleChangeLogRepository;
import com.norman.swp391.repository.RoleUpgradeRequestRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.NotificationService;
import com.norman.swp391.service.RoleManagementService;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Triển khai dịch vụ đơn xin đổi role.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementServiceImpl implements RoleManagementService {

    /** Chỉ cho phép user tự xin đổi qua lại giữa 3 role này (không được xin thẳng lên ADMIN/SUPER_ADMIN). */
    private static final Set<UserRole> REQUESTABLE_ROLES =
            EnumSet.of(UserRole.STUDENT, UserRole.RESEARCHER, UserRole.LECTURER);

    private final RoleUpgradeRequestRepository roleUpgradeRequestRepository;
    private final AppProperties appProperties;
    private final RoleChangeLogRepository roleChangeLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RoleUpgradeRequestResponse submitRequest(RoleUpgradeRequestCreateRequest request) {
        Long userId = requireUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!REQUESTABLE_ROLES.contains(request.getRequestedRole())) {
            throw new BadRequestException("You can only request STUDENT, RESEARCHER, or LECTURER role");
        }
        if (request.getRequestedRole() == user.getRole()) {
            throw new BadRequestException("You already have this role");
        }
        if (roleUpgradeRequestRepository.existsByUserIdAndStatus(userId, RoleRequestStatus.PENDING)) {
            throw new BadRequestException("You already have a pending role request");
        }

        RoleUpgradeRequest saved = roleUpgradeRequestRepository.save(RoleUpgradeRequest.builder()
                .user(user)
                .requestedRole(request.getRequestedRole())
                .reason(request.getReason())
                .proofUrl(request.getProofUrl())
                .status(RoleRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
        return RoleManagementMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleUpgradeRequestResponse getMyPendingRequest() {
        Long userId = requireUserId();
        return roleUpgradeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, RoleRequestStatus.PENDING)
                .map(RoleManagementMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleUpgradeRequestResponse> listRequests(RoleRequestStatus status, Pageable pageable) {
        Page<RoleUpgradeRequest> page = status != null
                ? roleUpgradeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : roleUpgradeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(page, RoleManagementMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional
    public RoleUpgradeRequestResponse approve(Long requestId, RoleRequestApproveRequest request) {
        RoleUpgradeRequest upgradeRequest = getPendingRequest(requestId);
        User operator = requireOperator(upgradeRequest);

        User targetUser = upgradeRequest.getUser();
        UserRole oldRole = targetUser.getRole();
        UserRole newRole = upgradeRequest.getRequestedRole();
        String note = request != null ? request.getNote() : null;

        targetUser.setRole(newRole);
        userRepository.save(targetUser);

        upgradeRequest.setStatus(RoleRequestStatus.APPROVED);
        upgradeRequest.setReviewedBy(operator);
        upgradeRequest.setReviewedAt(LocalDateTime.now());
        upgradeRequest.setReviewNote(note);
        roleUpgradeRequestRepository.save(upgradeRequest);

        roleChangeLogRepository.save(RoleChangeLog.builder()
                .operator(operator)
                .targetUser(targetUser)
                .oldRole(oldRole)
                .newRole(newRole)
                .reason(StringUtils.hasText(note) ? note : "Role upgrade request approved")
                .createdAt(LocalDateTime.now())
                .build());

        notificationService.notifyRoleRequestApproved(targetUser, newRole);
        return RoleManagementMapper.toResponse(upgradeRequest);
    }

    @Override
    @Transactional
    public RoleUpgradeRequestResponse reject(Long requestId, RoleRequestRejectRequest request) {
        RoleUpgradeRequest upgradeRequest = getPendingRequest(requestId);
        User operator = requireOperator(upgradeRequest);

        upgradeRequest.setStatus(RoleRequestStatus.REJECTED);
        upgradeRequest.setReviewedBy(operator);
        upgradeRequest.setReviewedAt(LocalDateTime.now());
        upgradeRequest.setRejectionReason(request.getRejectionReason());
        roleUpgradeRequestRepository.save(upgradeRequest);

        notificationService.notifyRoleRequestRejected(upgradeRequest.getUser(), request.getRejectionReason());
        return RoleManagementMapper.toResponse(upgradeRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleChangeLogResponse> listChangeLogs(Long targetUserId, Pageable pageable) {
        Page<RoleChangeLog> page = targetUserId != null
                ? roleChangeLogRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId, pageable)
                : roleChangeLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(page, RoleManagementMapper.toLogResponseList(page.getContent()));
    }

    @Override
    @Transactional
    public void purgeExpiredRequests() {
        int pendingDays = appProperties.getSync().getRoleRequestPendingRetentionDays();
        int reviewedDays = appProperties.getSync().getRoleRequestReviewedRetentionDays();

        int deletedPending = roleUpgradeRequestRepository
                .deletePendingCreatedBefore(LocalDateTime.now().minusDays(pendingDays));
        int deletedReviewed = roleUpgradeRequestRepository
                .deleteReviewedBefore(LocalDateTime.now().minusDays(reviewedDays));

        if (deletedPending > 0) {
            log.info("[ROLE_REQUEST_PURGE] Deleted {} pending request(s) older than {} days", deletedPending, pendingDays);
        }
        if (deletedReviewed > 0) {
            log.info("[ROLE_REQUEST_PURGE] Deleted {} reviewed request(s) older than {} days", deletedReviewed, reviewedDays);
        }
    }

    /**
     * Lấy đơn đang PENDING theo id kèm khóa pessimistic, throw nếu không tồn tại hoặc đã được xử lý rồi.
     * Khóa để ngăn 2 admin cùng duyệt/từ chối 1 đơn đồng thời (double-approve race condition).
     */
    private RoleUpgradeRequest getPendingRequest(Long requestId) {
        RoleUpgradeRequest upgradeRequest = roleUpgradeRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("RoleUpgradeRequest", requestId));
        if (upgradeRequest.getStatus() != RoleRequestStatus.PENDING) {
            throw new BadRequestException("This request has already been reviewed");
        }
        return upgradeRequest;
    }

    /** Lấy Admin/Super Admin đang thao tác, chặn tự duyệt/từ chối đơn của chính mình. */
    private User requireOperator(RoleUpgradeRequest upgradeRequest) {
        Long operatorId = requireUserId();
        if (operatorId.equals(upgradeRequest.getUser().getId())) {
            throw new BadRequestException("Cannot review your own role request");
        }
        return userRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", operatorId));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
