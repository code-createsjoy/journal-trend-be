package com.norman.swp391.service;

import com.norman.swp391.dto.request.collection.AddPaperToCollectionRequest;
import com.norman.swp391.dto.request.collection.CollectionRequest;
import com.norman.swp391.dto.response.collection.CollectionResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import java.util.List;

/**
 * Dịch vụ bộ sưu tập bài báo của người dùng.
 */
public interface CollectionService {

/**
 * Danh sách: listForCurrentUser.
 */
    List<CollectionResponse> listForCurrentUser();

/**
 * Lấy dữ liệu: getById.
 */
    CollectionResponse getById(Long id);

/**
 * Tạo hoặc lưu: create.
 */
    CollectionResponse create(CollectionRequest request);

/**
 * Cập nhật: update.
 */
    CollectionResponse update(Long id, CollectionRequest request);

/**
 * Xóa: delete.
 */
    void delete(Long id);

/**
 * Xử lý nghiệp vụ: addPaper.
 */
    CollectionResponse addPaper(Long collectionId, AddPaperToCollectionRequest request);

/**
 * Xóa: removePaper.
 */
    void removePaper(Long collectionId, Long paperId);

/**
 * Danh sách: listPapers.
 */
    List<PaperResponse> listPapers(Long collectionId);
}
