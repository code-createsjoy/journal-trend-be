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
