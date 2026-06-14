package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixPaper;
import com.norman.swp391.dto.helix.HelixDtos.HelixTopicDetail;
import com.norman.swp391.dto.helix.HelixDtos.HelixTopicTrend;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API chủ đề/từ khóa cho Helix (duy trì tương thích với giao diện Helix).
 */
@Hidden
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class HelixKeywordsController {

    private final HelixApiService helixApiService;

    /**
     * Xử lý API trending.
     */
    @GetMapping("/trending")
    public List<HelixTopicTrend> trending(@RequestParam(defaultValue = "10") int limit) {
        return helixApiService.listTrendingTopics(limit);
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public HelixTopicDetail getById(@PathVariable String id) {
        return helixApiService.getTopicDetail(id);
    }

    /**
     * Xử lý API papers.
     */
    @GetMapping("/{id}/papers")
    public List<HelixPaper> papers(@PathVariable String id, @RequestParam(defaultValue = "50") int limit) {
        return helixApiService.listPapersByTopic(id, limit);
    }
}
