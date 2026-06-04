package com.norman.swp391.service;

import com.norman.swp391.dto.request.admin.UpdateApiSourceRequest;
import com.norman.swp391.dto.response.admin.ApiSourceResponse;
import java.util.List;

/**
 * Quản lý cấu hình nguồn API đồng bộ.
 */
public interface ApiSourceService {

/**
 * Danh sách: listAll.
 */
    List<ApiSourceResponse> listAll();

/**
 * Cập nhật: update.
 */
    ApiSourceResponse update(String name, UpdateApiSourceRequest request);

/**
 * Xử lý nghiệp vụ: isEnabled.
 */
    boolean isEnabled(String name);

/**
 * Xử lý nghiệp vụ: recordSyncResult.
 */
    void recordSyncResult(String name, boolean success);
}
