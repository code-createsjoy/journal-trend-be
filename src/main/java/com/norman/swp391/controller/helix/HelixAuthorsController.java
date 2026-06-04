package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixAuthor;
import com.norman.swp391.dto.helix.HelixDtos.HelixAuthorProfile;
import com.norman.swp391.dto.helix.HelixDtos.HelixPaper;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API tác giả cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class HelixAuthorsController {

    private final HelixApiService helixApiService;

    /**
     * Xử lý API listFeatured.
     */
    @GetMapping("/featured")
    public List<HelixAuthor> listFeatured(@RequestParam(required = false, defaultValue = "24") int limit) {
        return helixApiService.listFeaturedAuthors(limit);
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public HelixAuthorProfile getById(@PathVariable String id) {
        return helixApiService.getAuthorProfile(id);
    }

    /**
     * Xử lý API listPapers.
     */
    @GetMapping("/{id}/papers")
    public List<HelixPaper> listPapers(@PathVariable String id, @RequestParam(required = false) Integer limit) {
        return helixApiService.listAuthorPapers(id, limit);
    }
}


