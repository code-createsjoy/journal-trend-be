package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.helix.HelixDtos.*;
import com.norman.swp391.service.helix.HelixApiService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API bộ sưu tập cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class HelixCollectionsController {

    private final HelixApiService helixApiService;

    /**
     * Xử lý API list.
     */
    @GetMapping
    public List<HelixCollection> list() {
        return helixApiService.listCollections();
    }

    /**
     * Xử lý API save.
     */
    @PostMapping("/save")
    public List<HelixCollection> save(@Valid @RequestBody HelixSavePaperRequest request) {
        return helixApiService.savePaperToCollections(request);
    }

    /**
     * Xử lý API remove.
     */
    @PostMapping("/remove")
    public HelixCollection remove(@Valid @RequestBody HelixRemovePaperRequest request) {
        return helixApiService.removePaperFromCollection(request);
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public HelixCollection getById(@PathVariable String id) {
        return helixApiService.getCollection(id);
    }

    /**
     * Xử lý API create.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HelixCollection create(@Valid @RequestBody HelixCollectionNameRequest request) {
        return helixApiService.createCollection(request.name());
    }

    /**
     * Xử lý API update.
     */
    @PutMapping("/{id}")
    public HelixCollection update(@PathVariable String id, @Valid @RequestBody HelixCollectionNameRequest request) {
        return helixApiService.updateCollection(id, request.name());
    }

    /**
     * Xử lý API delete.
     */
    @DeleteMapping("/{id}")
    public HelixIdResponse delete(@PathVariable String id) {
        helixApiService.deleteCollection(id);
        
        return new HelixIdResponse(id);
    }
}


