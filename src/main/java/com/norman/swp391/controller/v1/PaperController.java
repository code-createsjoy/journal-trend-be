package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * API tìm kiếm và xem chi tiết bài báo (v1).
 */
@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    /**
     * Xử lý API search.
     */
    @GetMapping
    public ApiResponse<PageResponse<PaperDetailResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minCitations,
            @RequestParam(required = false) Long journalId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paperService.search(q, searchType, topicId, authorId, fromYear, toYear, category, minCitations, journalId, pageable));
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<PaperDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(paperService.getById(id));
    }

    /**
     * Xử lý API getByIds.
     */
    @GetMapping("/bulk")
    public ApiResponse<java.util.List<PaperDetailResponse>> getByIds(@RequestParam java.util.List<Long> ids) {
        return ApiResponse.ok(paperService.getByIds(ids));
    }

    /**
     * Lấy bài báo theo domain của keyword (topic drill-down).
     */
    @GetMapping("/by-domain")
    public ApiResponse<java.util.List<PaperResponse>> getByDomain(
            @RequestParam String domain) {
        return ApiResponse.ok(paperService.searchByDomain(domain));
    }

    /**
     * Lấy danh sách các năm xuất bản có trong database.
     */
    @GetMapping("/years")
    public ApiResponse<java.util.List<Integer>> getAvailableYears() {
        return ApiResponse.ok(paperService.getAvailableYears());
    }
}


