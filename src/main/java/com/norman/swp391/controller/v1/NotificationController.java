package com.norman.swp391.controller.v1;

import java.util.List;
import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.notification.NotificationResponse;
import com.norman.swp391.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * API thông báo người dùng (v1).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Xử lý API list.
     */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(notificationService.listForCurrentUser(pageable));
    }

    /**
     * Xử lý API markAsRead.
     */
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ApiResponse.okMessage("Notification marked as read");
    }

    /**
     * Xử lý API markAllAsRead.
     */
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.okMessage("All notifications marked as read");
    }

    /**
     * Xử lý API delete.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ApiResponse.okMessage("Notification deleted");
    }

    /**
     * Xử lý API deleteMultiple.
     */
    @DeleteMapping("/bulk")
    public ApiResponse<Void> deleteMultiple(@RequestParam("ids") List<Long> ids) {
        notificationService.deleteMultiple(ids);
        return ApiResponse.okMessage("Selected notifications deleted");
    }

    /**
     * Xử lý API deleteAll.
     */
    @DeleteMapping("/all")
    public ApiResponse<Void> deleteAll() {
        notificationService.deleteAll();
        return ApiResponse.okMessage("All notifications deleted");
    }

    /**
     * Xử lý API deleteAllRead.
     */
    @DeleteMapping("/all-read")
    public ApiResponse<Void> deleteAllRead() {
        notificationService.deleteAllRead();
        return ApiResponse.okMessage("All read notifications deleted");
    }

    /**
     * Xử lý API bulk-read.
     */
    @PatchMapping("/bulk-read")
    public ApiResponse<Void> markMultipleAsRead(@RequestParam("ids") List<Long> ids) {
        notificationService.markMultipleAsRead(ids);
        return ApiResponse.okMessage("Selected notifications marked as read");
    }
}


