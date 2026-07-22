package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.admin.PaperReviewOverrideRequest;
import com.norman.swp391.dto.request.admin.UpdateAiCollectionAnalysisLimitRequest;
import com.norman.swp391.dto.response.ai.AiCollectionAnalysisLimitResponse;
import com.norman.swp391.dto.request.role.RoleRequestApproveRequest;
import com.norman.swp391.dto.request.role.RoleRequestRejectRequest;
import com.norman.swp391.dto.response.admin.PaperReviewResponse;
import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.dto.response.role.RoleChangeLogResponse;
import com.norman.swp391.dto.response.role.RoleUpgradeRequestResponse;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.RoleRequestStatus;
import com.norman.swp391.service.PaperReviewService;
import com.norman.swp391.service.RoleManagementService;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse;
import com.norman.swp391.service.KeywordTrendService;
import com.norman.swp391.service.TrendDemoStatsService;
import com.norman.swp391.dto.response.admin.SystemStatsResponse;
import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.service.AdminService;
import com.norman.swp391.service.AiCollectionAnalysisSettingService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
/**
 * REST AdminController.
 */
public class AdminController {

    private final AdminService adminService;
    private final PaperReviewService paperReviewService;
    private final KeywordTrendService keywordTrendService;
    private final TrendDemoStatsService trendDemoStatsService;
    private final RoleManagementService roleManagementService;
    private final AiCollectionAnalysisSettingService aiCollectionAnalysisSettingService;

    /**
     * Xử lý API triggerSync.
     */
    @PostMapping("/sync")
    public ApiResponse<SyncLogResponse> triggerSync() {
        return ApiResponse.ok("Sync started", adminService.triggerSync());
    }

    /**
     * Xử lý API listSyncLogs.
     */
    @GetMapping("/sync-logs")
    public ApiResponse<PageResponse<SyncLogResponse>> listSyncLogs(
            @RequestParam(required = false) SyncStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(adminService.listSyncLogs(status, from, to, pageable));
    }

    /**
     * Xử lý API searchUsers.
     */
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserAdminResponse>> searchUsers(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(adminService.searchUsers(q, pageable));
    }

    /**
     * Xử lý API lockUser.
     */
    @PostMapping("/users/{id}/lock")
    public ApiResponse<UserAdminResponse> lockUser(@PathVariable Long id) {
        return ApiResponse.ok(adminService.lockUser(id));
    }

    /**
     * Xử lý API unlockUser.
     */
    @PostMapping("/users/{id}/unlock")
    public ApiResponse<UserAdminResponse> unlockUser(@PathVariable Long id) {
        return ApiResponse.ok(adminService.unlockUser(id));
    }

    /**
     * Xử lý API softDeletePaper.
     */
    @DeleteMapping("/papers/{id}")
    public ApiResponse<Void> softDeletePaper(@PathVariable Long id) {
        adminService.softDeletePaper(id);
        return ApiResponse.okMessage("Paper soft-deleted");
    }

    /**
     * Xử lý API getStats.
     */
    @GetMapping("/stats")
    public ApiResponse<SystemStatsResponse> getStats() {
        return ApiResponse.ok(adminService.getSystemStats());
    }

    /** BR-105: danh sách bài theo trạng thái kiểm duyệt. */
    @GetMapping("/papers/review")
    public ApiResponse<PageResponse<PaperReviewResponse>> listPapersForReview(
            @RequestParam PaperReviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paperReviewService.listByReviewStatus(status, from, to, pageable));
    }

    /** BR-104: chấp nhận metadata hiện tại. */
    @PostMapping("/papers/{id}/review/accept")
    public ApiResponse<PaperReviewResponse> acceptPaperReview(
            @PathVariable Long id, @RequestParam(required = false) String note) {
        return ApiResponse.ok(paperReviewService.accept(id, note));
    }

    /** BR-104: ghi đè metadata từ nguồn conflict. */
    @PostMapping("/papers/{id}/review/override")
    public ApiResponse<PaperReviewResponse> overridePaperReview(
            @PathVariable Long id, @RequestBody(required = false) PaperReviewOverrideRequest body) {
        return ApiResponse.ok(paperReviewService.override(id, body));
    }

    /** Tính lại publication_trends cho N tháng trước tới nay — dùng khi seed dữ liệu lịch sử. */
    @PostMapping("/trends/backfill")
    public ApiResponse<String> backfillTrends(@RequestParam(defaultValue = "12") int months) {
        keywordTrendService.backfillHistoricalMonths(months);
        return ApiResponse.ok("Backfilled keyword trends for last " + months + " months");
    }

    /** Tính lại trend của tháng hiện tại ngay lập tức (không cần đợi cron). */
    @PostMapping("/trends/recalculate")
    public ApiResponse<String> recalculateTrends() {
        keywordTrendService.recalculateAll();
        return ApiResponse.ok("Recalculated current month keyword trends");
    }

    /** Số liệu minh bạch cho báo cáo / thuyết trình. */
    @GetMapping("/trends/demo-stats")
    public ApiResponse<TrendDemoStatsResponse> trendDemoStats() {
        return ApiResponse.ok(trendDemoStatsService.getStats());
    }

    /** Danh sách đơn xin đổi role (mặc định PENDING). */
    @GetMapping("/role-requests")
    public ApiResponse<PageResponse<RoleUpgradeRequestResponse>> listRoleRequests(
            @RequestParam(required = false, defaultValue = "PENDING") RoleRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(roleManagementService.listRequests(status, pageable));
    }

    /** Duyệt đơn xin đổi role: đổi role cho user, ghi nhật ký, gửi notification. */
    @PostMapping("/role-requests/{requestId}/approve")
    public ApiResponse<RoleUpgradeRequestResponse> approveRoleRequest(
            @PathVariable Long requestId, @RequestBody(required = false) RoleRequestApproveRequest request) {
        return ApiResponse.ok("Role request approved", roleManagementService.approve(requestId, request));
    }

    /** Từ chối đơn xin đổi role, kèm lý do chọn từ danh sách cố định (dropdown). */
    @PostMapping("/role-requests/{requestId}/reject")
    public ApiResponse<RoleUpgradeRequestResponse> rejectRoleRequest(
            @PathVariable Long requestId, @Valid @RequestBody RoleRequestRejectRequest request) {
        return ApiResponse.ok("Role request rejected", roleManagementService.reject(requestId, request));
    }

    /** Nhật ký các lần đổi role thành công, phục vụ kiểm toán. Có thể lọc theo targetUserId. */
    @GetMapping("/role-logs")
    public ApiResponse<PageResponse<RoleChangeLogResponse>> listRoleLogs(
            @RequestParam(required = false) Long targetUserId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(roleManagementService.listChangeLogs(targetUserId, pageable));
    }

    /** Đổi cap số paper/lượt phân tích AI collection (1-100), có hiệu lực ngay không cần redeploy. */
    @PutMapping("/settings/ai-collection-analysis-limit")
    public ApiResponse<AiCollectionAnalysisLimitResponse> updateAiCollectionAnalysisLimit(
            @Valid @RequestBody UpdateAiCollectionAnalysisLimitRequest request) {
        int updated = aiCollectionAnalysisSettingService.updateMaxPapers(request.getMaxPapers());
        return ApiResponse.ok("AI collection analysis limit updated",
                AiCollectionAnalysisLimitResponse.builder().maxPapers(updated).build());
    }
}


