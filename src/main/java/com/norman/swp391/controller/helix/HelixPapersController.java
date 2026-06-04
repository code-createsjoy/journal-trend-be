package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixPaper;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API bài báo cho frontend Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class HelixPapersController {

    private final HelixApiService helixApiService;

    /**
     * Xử lý API list.
     */
    @GetMapping
    public List<HelixPaper> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String excludeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long topicId) {
        return helixApiService.listPapers(category, excludeId, limit, q, topicId);
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public HelixPaper getById(@PathVariable String id) {
        try {
            return helixApiService.getPaper(id);
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }
}


