package com.norman.swp391.service.impl;

import com.norman.swp391.entity.Journal;
import com.norman.swp391.repository.JournalRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Ghi journal trong transaction riêng để tránh entity lỗi (null id) trong session cha.
 */
@Component
@RequiredArgsConstructor
class JournalPersistenceHelperImpl {

    private final JournalRepository journalRepository;
    private final EntityManager entityManager;

    /**
     * Tìm journal đã tồn tại (theo ISSN hoặc tên) trước, nếu chưa có thì tạo mới.
     * Chạy trong transaction riêng (REQUIRES_NEW); nếu bị race (2 request cùng tạo 1 journal
     * mới, vi phạm unique constraint) thì bắt lỗi, clear session rồi đọc lại bản ghi vừa được tạo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Journal saveIfAbsent(String trimmed, String issn, String domain) {
        Optional<Journal> existing = findExisting(trimmed, issn);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return journalRepository.saveAndFlush(buildJournal(trimmed, issn, domain));
        } catch (RuntimeException ex) {
            if (!isConstraintViolation(ex)) {
                throw ex;
            }
            entityManager.clear();
            return findExisting(trimmed, issn).orElse(null);
        }
    }

    /** Tìm journal đã có theo ISSN trước (chính xác hơn), fallback theo tên (chuẩn hóa/không phân biệt hoa thường). */
    private Optional<Journal> findExisting(String trimmed, String issn) {
        if (StringUtils.hasText(issn)) {
            Optional<Journal> byIssn = journalRepository.findByIssnIgnoreCase(issn.trim());
            if (byIssn.isPresent()) {
                return byIssn;
            }
        }
        return journalRepository
                .findFirstByNameNormalized(trimmed)
                .or(() -> journalRepository.findByNameIgnoreCase(trimmed));
    }

    /** Dựng entity Journal mới với giá trị mặc định (impactFactor=0, active=true). */
    private static Journal buildJournal(String trimmed, String issn, String domain) {
        return Journal.builder()
                .name(trimmed)
                .issn(StringUtils.hasText(issn) ? issn.trim() : null)
                .domain(StringUtils.hasText(domain) ? domain.trim() : "General")
                .impactFactor(BigDecimal.ZERO)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** Kiểm tra exception có phải do vi phạm unique constraint không (kể cả lỗi lồng trong cause chain). */
    private static boolean isConstraintViolation(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("UNIQUE KEY") || message.contains("2627"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
