package com.norman.swp391.controller.helix;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.helix.HelixDtos.HelixAdminOverview;
import com.norman.swp391.dto.helix.HelixDtos.HelixApiSource;
import com.norman.swp391.dto.helix.HelixDtos.HelixSyncResult;
import com.norman.swp391.dto.helix.HelixDtos.HelixUpdateApiSourceRequest;
import com.norman.swp391.dto.request.admin.UpdateApiSourceRequest;
import com.norman.swp391.dto.response.admin.ApiSourceResponse;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse;
import com.norman.swp391.service.*;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
/**
 * REST HelixAdminController.
 */
public class HelixAdminController {

    private final HelixApiService helixApiService;
    private final ApiSourceService apiSourceService;
    private final KeywordTrendService keywordTrendService;
    private final PaperReviewService paperReviewService;
    private final TrendDemoStatsService trendDemoStatsService;
    private final AppProperties appProperties;
    private final PaperMetadataRepairService paperMetadataRepairService;
    private final PaperSyncService paperSyncService;

    /**
     * Xử lý API overview.
     */
    @GetMapping("/overview")
    public HelixAdminOverview overview() {
        return helixApiService.adminOverview();
    }

    /**
     * Xử lý API triggerSync.
     */
    @PostMapping("/sync")
    public HelixSyncResult triggerSync() {
        return helixApiService.triggerAdminSync();
    }

    /**
     * Xử lý API syncStatus.
     */
    @GetMapping("/sync/status")
    public HelixSyncResult syncStatus() {
        return helixApiService.latestSyncStatus();
    }

    /**
     * Xử lý API resetStaleSync.
     */
    @PostMapping("/sync/reset-stale")
    public HelixSyncResult resetStaleSync() {
        return helixApiService.resetStaleSync();
    }

    /**
     * Tính lại trend topic (không cần sync OpenAlex).
     */
    @PostMapping("/trends/recalculate")
    public HelixSyncResult recalculateTrends() {
        keywordTrendService.recalculateAll();
        int months = appProperties.getSync().getTrendBackfillMonths();
        if (months > 0) {
            keywordTrendService.backfillHistoricalMonths(months);
        }
        return helixApiService.latestSyncStatus();
    }

    @PostMapping("/trends/backfill")
    public HelixSyncResult backfillTrends(@RequestParam(defaultValue = "12") int months) {
        keywordTrendService.backfillHistoricalMonths(months);
        return helixApiService.latestSyncStatus();
    }

    @GetMapping("/trends/demo-stats")
    public TrendDemoStatsResponse trendDemoStats() {
        return trendDemoStatsService.getStats();
    }

    @PostMapping("/papers/repair-metadata")
    public HelixSyncResult repairMetadata(@RequestParam(defaultValue = "50") int limit) {
        int repaired = paperMetadataRepairService.repairFromOpenAlex(limit);
        return new HelixSyncResult(repaired, "SUCCESS", "Repaired " + repaired + " papers from OpenAlex");
    }

    @PostMapping("/authors/enrich-stats")
    public HelixSyncResult enrichAuthorStats(@RequestParam(defaultValue = "10000") int limit) {
        int enriched = paperSyncService.enrichAuthorStats(limit);
        return new HelixSyncResult(enriched, "SUCCESS", "Enriched " + enriched + " authors with hIndex + citationCount");
    }

    @PostMapping("/papers/review/expire-stale")
    public HelixSyncResult expireStaleReviews() {
        paperReviewService.expireStalePendingReviews();
        return helixApiService.latestSyncStatus();
    }

    /**
     * Xử lý API listSources.
     */
    @GetMapping("/sources")
    public List<HelixApiSource> listSources() {
        return apiSourceService.listAll().stream().map(this::toHelix).toList();
    }

    /**
     * Xử lý API updateSource.
     */
    @PatchMapping("/sources/{name}")
    public HelixApiSource updateSource(@PathVariable String name, @RequestBody HelixUpdateApiSourceRequest body) {
        ApiSourceResponse updated = apiSourceService.update(
                name,
                UpdateApiSourceRequest.builder()
                        .enabled(body.enabled())
                        .syncSchedule(body.syncSchedule())
                        .build());
        return toHelix(updated);
    }

/**
 * Ánh xạ sang DTO/phản hồi: toHelix.
 */
    private HelixApiSource toHelix(ApiSourceResponse s) {
        return new HelixApiSource(
                s.getName(), s.getBaseUrl(), s.isEnabled(), s.getSyncSchedule(), s.getLastSyncAt(), s.getSuccessRate());
    }
}


