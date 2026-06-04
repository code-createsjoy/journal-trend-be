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
    public ApiResponse<PageResponse<PaperResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Long authorId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paperService.search(q, topicId, authorId, pageable));
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<PaperDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(paperService.getById(id));
    }
}


