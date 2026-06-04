package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.collection.AddPaperToCollectionRequest;
import com.norman.swp391.dto.request.collection.CollectionRequest;
import com.norman.swp391.dto.response.collection.CollectionResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.entity.CollectionPaper;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperCollection;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.CollectionMapper;
import com.norman.swp391.mapper.PaperMapper;
import com.norman.swp391.repository.CollectionPaperRepository;
import com.norman.swp391.repository.PaperCollectionRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Triển khai dịch vụ bộ sưu tập.
 */
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {

    private final PaperCollectionRepository collectionRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final PaperRepository paperRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listForCurrentUser.
 */
    public List<CollectionResponse> listForCurrentUser() {
        Long userId = requireUserId();
        return collectionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(c -> CollectionMapper.toResponse(
                        c, collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(c.getId()).size()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getById.
 */
    public CollectionResponse getById(Long id) {
        PaperCollection collection = getOwnedCollection(id);
        int count = collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(id).size();
        return CollectionMapper.toResponse(collection, count);
    }

    @Override
    @Transactional
/**
 * Tạo hoặc lưu: create.
 */
    public CollectionResponse create(CollectionRequest request) {
        Long userId = requireUserId();
        validateUniqueName(userId, request.getName(), null);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        PaperCollection collection = PaperCollection.builder()
                .user(user)
                .name(request.getName().trim())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();
        collection = collectionRepository.save(collection);
        return CollectionMapper.toResponse(collection, 0);
    }

    @Override
    @Transactional
/**
 * Cập nhật: update.
 */
    public CollectionResponse update(Long id, CollectionRequest request) {
        Long userId = requireUserId();
        PaperCollection collection = getOwnedCollection(id);
        validateUniqueName(userId, request.getName(), id);
        collection.setName(request.getName().trim());
        collection.setDescription(request.getDescription());
        collection = collectionRepository.save(collection);
        int count = collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(id).size();
        return CollectionMapper.toResponse(collection, count);
    }

    @Override
    @Transactional
/**
 * Xóa: delete.
 */
    public void delete(Long id) {
        PaperCollection collection = getOwnedCollection(id);
        collectionPaperRepository.deleteAll(collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(id));
        collectionRepository.delete(collection);
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: addPaper.
 */
    public CollectionResponse addPaper(Long collectionId, AddPaperToCollectionRequest request) {
        PaperCollection collection = getOwnedCollection(collectionId);
        Paper paper = paperRepository
                .findById(request.getPaperId())
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE && p.getReviewStatus() == PaperReviewStatus.NONE)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", request.getPaperId()));
        if (!collectionPaperRepository.existsByCollectionIdAndPaperId(collectionId, paper.getId())) {
            Long userId = requireUserId();
            int max = appProperties.getSync().getMaxBookmarkPapersPerUser();
            long saved = collectionPaperRepository.countDistinctPapersByUserId(userId);
            if (saved >= max) {
                throw new BadRequestException(
                        "You have reached the maximum of " + max + " saved papers (bookmarks)");
            }
            collectionPaperRepository.save(CollectionPaper.builder()
                    .collection(collection)
                    .paper(paper)
                    .savedAt(LocalDateTime.now())
                    .build());
        }
        int count = collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(collectionId).size();
        return CollectionMapper.toResponse(collection, count);
    }

    @Override
    @Transactional
/**
 * Xóa: removePaper.
 */
    public void removePaper(Long collectionId, Long paperId) {
        getOwnedCollection(collectionId);
        collectionPaperRepository
                .findByCollectionIdAndPaperId(collectionId, paperId)
                .ifPresent(collectionPaperRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listPapers.
 */
    public List<PaperResponse> listPapers(Long collectionId) {
        getOwnedCollection(collectionId);
        return collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(collectionId).stream()
                .map(CollectionPaper::getPaper)
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE)
                .map(PaperMapper::toResponse)
                .toList();
    }

/**
 * Lấy dữ liệu: getOwnedCollection.
 */
    private PaperCollection getOwnedCollection(Long id) {
        Long userId = requireUserId();
        return collectionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
    }

/**
 * Xác thực: validateUniqueName.
 */
    private void validateUniqueName(Long userId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Collection name is required");
        }
        String trimmed = name.trim();
        boolean exists = collectionRepository.existsByUserIdAndNameIgnoreCase(userId, trimmed);
        if (exists && excludeId == null) {
            throw new BadRequestException("Collection name already exists");
        }
        if (excludeId != null) {
            collectionRepository.findByIdAndUserId(excludeId, userId).ifPresent(existing -> {
                if (!existing.getName().equalsIgnoreCase(trimmed)
                        && collectionRepository.existsByUserIdAndNameIgnoreCase(userId, trimmed)) {
                    throw new BadRequestException("Collection name already exists");
                }
            });
        }
    }

/**
 * Xử lý nghiệp vụ: requireUserId.
 */
    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userId;
    }
}
