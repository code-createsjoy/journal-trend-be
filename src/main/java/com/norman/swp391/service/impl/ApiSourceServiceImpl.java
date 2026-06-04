package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.admin.UpdateApiSourceRequest;
import com.norman.swp391.dto.response.admin.ApiSourceResponse;
import com.norman.swp391.entity.ApiSourceConfig;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.repository.ApiSourceConfigRepository;
import com.norman.swp391.service.ApiSourceService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai quản lý nguồn API.
 */
@Service
@RequiredArgsConstructor
public class ApiSourceServiceImpl implements ApiSourceService {

    private final ApiSourceConfigRepository apiSourceConfigRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listAll.
 */
    public List<ApiSourceResponse> listAll() {
        return apiSourceConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
/**
 * Cập nhật: update.
 */
    public ApiSourceResponse update(String name, UpdateApiSourceRequest request) {
        ApiSourceConfig config = apiSourceConfigRepository
                .findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException("ApiSource", name));
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getSyncSchedule() != null) {
            config.setSyncSchedule(request.getSyncSchedule());
        }
        config.setUpdatedAt(LocalDateTime.now());
        return toResponse(apiSourceConfigRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Xử lý nghiệp vụ: isEnabled.
 */
    public boolean isEnabled(String name) {
        return apiSourceConfigRepository
                .findByNameIgnoreCase(name)
                .map(ApiSourceConfig::isEnabled)
                .orElse(true);
    }

    @Override
    @Transactional
/**
 * Xử lý nghiệp vụ: recordSyncResult.
 */
    public void recordSyncResult(String name, boolean success) {
        apiSourceConfigRepository.findByNameIgnoreCase(name).ifPresent(config -> {
            config.setLastSyncAt(LocalDateTime.now());
            double prev = config.getSuccessRate() != null ? config.getSuccessRate() : 100.0;
            config.setSuccessRate(success ? Math.min(100.0, prev * 0.9 + 10.0) : Math.max(0.0, prev * 0.9));
            config.setUpdatedAt(LocalDateTime.now());
            apiSourceConfigRepository.save(config);
        });
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    private ApiSourceResponse toResponse(ApiSourceConfig config) {
        return ApiSourceResponse.builder()
                .name(config.getName())
                .baseUrl(config.getBaseUrl())
                .enabled(config.isEnabled())
                .syncSchedule(config.getSyncSchedule())
                .lastSyncAt(config.getLastSyncAt() != null ? config.getLastSyncAt().toString() : null)
                .successRate(config.getSuccessRate())
                .build();
    }
}
