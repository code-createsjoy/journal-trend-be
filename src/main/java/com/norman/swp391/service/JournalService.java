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
}
