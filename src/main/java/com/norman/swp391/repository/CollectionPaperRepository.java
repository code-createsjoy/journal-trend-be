package com.norman.swp391.repository;

import com.norman.swp391.entity.CollectionPaper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập liên kết bộ sưu tập–bài báo.
 */
public interface CollectionPaperRepository extends JpaRepository<CollectionPaper, Long> {
    /**
     * Tìm kiếm: findByCollectionIdOrderBySavedAtDesc.
     */
    List<CollectionPaper> findByCollectionIdOrderBySavedAtDesc(Long collectionId);

    /** Đếm số paper trong 1 collection bằng COUNT thay vì load hết rồi .size(). */
    long countByCollectionId(Long collectionId);

    /** Đếm số paper theo nhiều collection cùng lúc (batch) — tránh N+1 khi hiển thị danh sách collection. */
    @Query("""
        SELECT cp.collection.id, COUNT(cp)
        FROM CollectionPaper cp
        WHERE cp.collection.id IN :collectionIds
        GROUP BY cp.collection.id
        """)
    List<Object[]> countByCollectionIdIn(@Param("collectionIds") java.util.Collection<Long> collectionIds);

    /** Lấy toàn bộ liên kết paper của nhiều collection cùng lúc (batch) — tránh N+1. */
    @Query("""
        SELECT cp FROM CollectionPaper cp JOIN FETCH cp.paper
        WHERE cp.collection.id IN :collectionIds
        ORDER BY cp.savedAt DESC
        """)
    List<CollectionPaper> findByCollectionIdInOrderBySavedAtDesc(@Param("collectionIds") java.util.Collection<Long> collectionIds);

    /**
     * Tìm liên kết theo collection và paper.
     */
    Optional<CollectionPaper> findByCollectionIdAndPaperId(Long collectionId, Long paperId);

    /**
     * Kiểm tra bài đã có trong collection.
     */
    boolean existsByCollectionIdAndPaperId(Long collectionId, Long paperId);

    /** BR-57: tổng số bài khác nhau user đã lưu qua mọi collection. */
    @Query("""
        SELECT COUNT(DISTINCT cp.paper.id) FROM CollectionPaper cp
        WHERE cp.collection.user.id = :userId
        """)
    long countDistinctPapersByUserId(@Param("userId") Long userId);

    @Query("SELECT cp.paper.id FROM CollectionPaper cp WHERE cp.collection.user.id = :userId")
    List<Long> findPaperIdsByUserId(@Param("userId") Long userId);

    /**
     * Top bài được lưu nhiều nhất (paperId, count).
     */
    @Query("""
        SELECT cp.paper.id, COUNT(cp) FROM CollectionPaper cp
        GROUP BY cp.paper.id
        ORDER BY COUNT(cp) DESC
        """)
    /**
     * Tìm kiếm: findMostSavedPaperIds.
     */
    List<Object[]> findMostSavedPaperIds(Pageable pageable);
}
