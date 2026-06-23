package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.response.author.AuthorDetailResponse;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.service.AuthorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tác giả (v1).
 */
@RestController
@RequestMapping("/api/v1/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    /**
     * Xử lý API getFeatured.
     */
    @GetMapping("/featured")
    public ApiResponse<PageResponse<AuthorResponse>> getFeatured(@PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(authorService.getFeatured(pageable));
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<AuthorResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(authorService.getById(id));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<AuthorDetailResponse> getAuthorDetail(@PathVariable Long id) {
        return ApiResponse.ok(authorService.getAuthorDetail(id));
    }

    /**
     * Xử lý API getPapers.
     */
    @GetMapping("/{id}/papers")
    public ApiResponse<PageResponse<PaperResponse>> getPapers(
            @PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(authorService.getPapersByAuthor(id, pageable));
    }
}


