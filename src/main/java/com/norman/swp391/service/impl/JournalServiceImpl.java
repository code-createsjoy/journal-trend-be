package com.norman.swp391.service.impl;

import com.norman.swp391.service.JournalService;
import com.norman.swp391.entity.Journal;
import com.norman.swp391.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Impl JournalServiceImpl.
 */
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private final JournalRepository journalRepository;
    private final JournalPersistenceHelperImpl journalPersistenceHelperImpl;

    @Override
    /**
     * Tìm hoặc tạo tạp chí.
     */
    public Journal findOrCreate(String name, String issn, String domain) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.trim();
        if (StringUtils.hasText(issn)) {
            var byIssn = journalRepository.findByIssnIgnoreCase(issn.trim());
            if (byIssn.isPresent()) {
                return byIssn.get();
            }
        }
        return journalRepository
                .findFirstByNameNormalized(trimmed)
                .or(() -> journalRepository.findByNameIgnoreCase(trimmed))
                .orElseGet(() -> journalPersistenceHelperImpl.saveIfAbsent(trimmed, issn, domain));
    }

    /**
     * Thực hiện findById.
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Journal> findById(Long id) {
        return journalRepository.findById(id);
    }
}


