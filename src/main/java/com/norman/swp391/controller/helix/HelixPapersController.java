package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.HelixCitationNode;
import com.norman.swp391.dto.helix.HelixDtos.HelixPaper;
import com.norman.swp391.dto.helix.HelixDtos.HelixPaperGraph;
import com.norman.swp391.dto.helix.HelixDtos.HelixReferenceNode;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.service.PaperReferenceService;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final PaperReferenceService paperReferenceService;

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
    public ResponseEntity<HelixPaper> getById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(helixApiService.getPaper(id));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lấy danh sách referenced works (References Graph) cho paper.
     * Lazy fetch từ OpenAlex nếu chưa có trong cache.
     */
    @GetMapping("/{id}/references")
    public List<HelixReferenceNode> getReferences(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        return paperReferenceService.getReferences(id, limit);
    }

    /**
     * Lấy danh sách citing works (Citation Graph) cho paper.
     * Real-time query từ OpenAlex với sort và filter.
     *
     * @param sort     "citations" (default) hoặc "recent"
     * @param yearFrom Năm bắt đầu (optional)
     * @param yearTo   Năm kết thúc (optional)
     * @param limit    Số lượng tối đa (default 20, max 100)
     */
    @GetMapping("/{id}/citations")
    public List<HelixCitationNode> getCitations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "citations") String sort,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(defaultValue = "20") int limit) {
        return paperReferenceService.getCitations(id, sort, yearFrom, yearTo, Math.min(limit, 100));
    }

    /**
     * Gộp References + Citations vào 1 response cho paper detail page.
     * Cả 2 tối đa 50 items.
     *
     * @param refLimit  Số references tối đa (default 50, max 50)
     * @param citLimit  Số citations tối đa (default 50, max 50)
     * @param sort      Sort citations: "citations" (default) hoặc "recent"
     * @param yearFrom  Lọc citations từ năm (optional)
     * @param yearTo    Lọc citations đến năm (optional)
     */
    @GetMapping("/{id}/graph")
    public HelixPaperGraph getPaperGraph(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int refLimit,
            @RequestParam(defaultValue = "50") int citLimit,
            @RequestParam(defaultValue = "citations") String sort,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        return paperReferenceService.getPaperGraph(id, Math.min(refLimit, 50), Math.min(citLimit, 50), sort, yearFrom, yearTo);
    }
}
