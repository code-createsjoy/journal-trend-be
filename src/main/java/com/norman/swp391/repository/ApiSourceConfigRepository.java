package com.norman.swp391.repository;

import com.norman.swp391.entity.ApiSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Kho ApiSourceConfig.
 */
public interface ApiSourceConfigRepository extends JpaRepository<ApiSourceConfig, Long> {
    /**
     * Tìm kiếm: findByNameIgnoreCase.
     */
    Optional<ApiSourceConfig> findByNameIgnoreCase(String name);
}


