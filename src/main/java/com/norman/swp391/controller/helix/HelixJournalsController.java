package com.norman.swp391.controller.helix;

import com.norman.swp391.mapper.JournalMapper;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.dto.helix.HelixDtos.HelixJournal;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tạp chí cho Helix.
 */
@Hidden
@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
public class HelixJournalsController {

    private final JournalRepository journalRepository;

    /**
     * Xử lý API search.
     */
    @GetMapping
    public List<HelixJournal> search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "24") int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return journalRepository.search(q, PageRequest.of(0, size)).getContent().stream()
                .map(JournalMapper::toResponse)
                .map(j -> new HelixJournal(
                        String.valueOf(j.getId()),
                        j.getName(),
                        j.getPublisher(),
                        j.getIssn(),
                        j.getDomain(),
                        j.getImpactFactor() != null ? j.getImpactFactor() : 0))
                .toList();
    }

    /**
     * Xử lý API getById.
     */
    @GetMapping("/{id}")
    public HelixJournal getById(@PathVariable Long id) {
        var journal = journalRepository.findById(id).orElse(null);
        if (journal == null) {
            return null;
        }
        var j = JournalMapper.toResponse(journal);

        return new HelixJournal(
                String.valueOf(j.getId()),
                j.getName(),
                j.getPublisher(),
                j.getIssn(),
                j.getDomain(),
                j.getImpactFactor() != null ? j.getImpactFactor() : 0);
    }
}


