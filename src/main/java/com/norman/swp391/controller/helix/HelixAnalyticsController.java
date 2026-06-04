package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixAnalyticsSnapshot;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API analytics cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class HelixAnalyticsController {

    private final HelixApiService helixApiService;

    @GetMapping("/snapshot")
    /**
     * API endpoint snapshot.
     */
    public HelixAnalyticsSnapshot snapshot() {
        return helixApiService.analyticsSnapshot();
    }
}


