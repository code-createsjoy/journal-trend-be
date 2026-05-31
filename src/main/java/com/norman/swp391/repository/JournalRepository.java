package com.norman.swp391.repository;

import com.norman.swp391.entity.Journal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Kho truy cập tạp chí.
 */
public interface JournalRepository extends JpaRepository<Journal, Long> {

/**
 * Tìm kiếm: findByNameIgnoreCase.
 */
    Optional<Journal> findByNameIgnoreCase(String name);

    @Query(value = "SELECT TOP 1 * FROM journals WHERE LOWER(name) = LOWER(:name)", nativeQuery = true)
    Optional<Journal> findFirstByNameNormalized(@Param("name") String name);

/**
 * Tìm kiếm: findByIssnIgnoreCase.
 */
    Optional<Journal> findByIssnIgnoreCase(String issn);

    @Query("SELECT j FROM Journal j WHERE :q IS NULL OR LOWER(j.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Journal> search(@Param("q") String q, Pageable pageable);
}
