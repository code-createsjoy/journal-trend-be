package com.norman.swp391.controller.v1;

import com.norman.swp391.dto.common.ApiResponse;
import com.norman.swp391.dto.request.collection.AddPaperToCollectionRequest;
import com.norman.swp391.dto.request.collection.CollectionRequest;
import com.norman.swp391.dto.response.collection.CollectionResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.service.CollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API bộ sưu tập bài báo (v1).
 */
@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    /**
     * Xử lý API list.
     */
    @GetMapping
    public ApiResponse<List<CollectionResponse>> list() {
        return ApiResponse.ok(collectionService.listForCurrentUser());
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public ApiResponse<CollectionResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(collectionService.getById(id));
    }

    /**
     * Xử lý API create.
     */
    @PostMapping
    public ApiResponse<CollectionResponse> create(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.ok("Collection created", collectionService.create(request));
    }

    /**
     * Xử lý API update.
     */
    @PutMapping("/{id}")
    public ApiResponse<CollectionResponse> update(
            @PathVariable Long id, @Valid @RequestBody CollectionRequest request) {
        return ApiResponse.ok(collectionService.update(id, request));
    }

    /**
     * Xử lý API delete.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        collectionService.delete(id);
        return ApiResponse.okMessage("Collection deleted");
    }

    /**
     * Xử lý API addPaper.
     */
    @PostMapping("/{id}/papers")
    public ApiResponse<CollectionResponse> addPaper(
            @PathVariable Long id, @Valid @RequestBody AddPaperToCollectionRequest request) {
        return ApiResponse.ok(collectionService.addPaper(id, request));
    }

    /**
     * Xử lý API removePaper.
     */
    @DeleteMapping("/{id}/papers/{paperId}")
    public ApiResponse<Void> removePaper(@PathVariable Long id, @PathVariable Long paperId) {
        collectionService.removePaper(id, paperId);
        return ApiResponse.okMessage("Paper removed from collection");
    }

    /**
     * Xử lý API listPapers.
     */
    @GetMapping("/{id}/papers")
    public ApiResponse<List<PaperResponse>> listPapers(@PathVariable Long id) {
        return ApiResponse.ok(collectionService.listPapers(id));
    }
}


