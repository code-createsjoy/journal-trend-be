package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Kho PaperCollection.
 */
public interface PaperCollectionRepository extends JpaRepository<PaperCollection, Long> {
    /**
     * Tìm kiếm: findByUserIdOrderByCreatedAtDesc.
     */
    List<PaperCollection> findByUserIdOrderByCreatedAtDesc(Long userId);
    /**
     * Collection owned.
     */
    Optional<PaperCollection> findByIdAndUserId(Long id, Long userId);
    /**
     * Trùng tên collection.
     */
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    /**
     * Đếm collection.
     */
    long countByUserId(Long userId);
}


