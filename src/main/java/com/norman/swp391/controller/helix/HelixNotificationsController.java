package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixNotification;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API thông báo cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class HelixNotificationsController {

    private final HelixApiService helixApiService;

    /**
     * Xử lý API list.
     */
    @GetMapping
    public List<HelixNotification> list() {
        return helixApiService.listNotifications();
    }

    /**
     * Xử lý API markAllAsRead.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        helixApiService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }

    /**
     * Xử lý API markAsRead.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        helixApiService.markNotificationRead(Long.parseLong(id));
        return ResponseEntity.noContent().build();
    }
}


