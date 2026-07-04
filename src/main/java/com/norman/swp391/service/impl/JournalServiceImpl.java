package com.norman.swp391.service.impl;

import com.norman.swp391.service.JournalService;
import com.norman.swp391.entity.Journal;
import com.norman.swp391.repository.JournalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Impl JournalServiceImpl.
 */
@Slf4j
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

    /**
     * Tính Impact Factor proxy = tổng citation / số bài ACTIVE của từng tạp chí.
     * Dùng 1 query GROUP BY để lấy thống kê cho toàn bộ tạp chí cùng lúc (tránh N+1),
     * sau đó chỉ ghi lại những tạp chí có giá trị thay đổi.
     */
    @Override
    @Transactional
    public void calculateAndUpdateJournalImpactFactors() {
        Map<Long, Object[]> statsByJournalId = journalRepository.aggregateCitationStatsByJournal().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        List<Journal> journals = journalRepository.findAll();
        List<Journal> toSave = new ArrayList<>();

        for (Journal journal : journals) {
            Object[] stats = statsByJournalId.get(journal.getId());
            BigDecimal impactFactor = BigDecimal.ZERO;
            if (stats != null) {
                long totalCitations = ((Number) stats[1]).longValue();
                long paperCount = ((Number) stats[2]).longValue();
                if (paperCount > 0) {
                    impactFactor = BigDecimal.valueOf((double) totalCitations / paperCount)
                            .setScale(3, RoundingMode.HALF_UP);
                }
            }
            if (journal.getImpactFactor() == null || impactFactor.compareTo(journal.getImpactFactor()) != 0) {
                journal.setImpactFactor(impactFactor);
                toSave.add(journal);
            }
        }

        if (!toSave.isEmpty()) {
            journalRepository.saveAll(toSave);
        }
        log.info("[IMPACT_FACTOR] Recalculated proxy impact factor for {} journal(s), {} updated",
                journals.size(), toSave.size());
    }
}


