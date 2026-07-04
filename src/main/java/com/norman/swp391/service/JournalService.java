package com.norman.swp391.service;

import com.norman.swp391.entity.Journal;
import java.util.Optional;

/**
 * Dịch vụ tạp chí.
 */
public interface JournalService {

/**
 * Tìm kiếm: findOrCreate.
 */
    Journal findOrCreate(String name, String issn, String domain);

/**
 * Tìm kiếm: findById.
 */
    Optional<Journal> findById(Long id);

/**
 * Tính lại Impact Factor proxy (tổng trích dẫn / số bài ACTIVE) cho mọi tạp chí
 * và cập nhật hàng loạt. Chạy định kỳ sau mỗi lần sync thành công.
 */
    void calculateAndUpdateJournalImpactFactors();
}
