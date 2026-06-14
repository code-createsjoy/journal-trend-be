package com.norman.swp391.service.impl;

import com.norman.swp391.entity.Paper;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.service.PaperMetadataRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperMetadataRepairServiceImpl implements PaperMetadataRepairService {

    private final PaperRepository paperRepository;
    private final OpenAlexClient openAlexClient;

    @Override
    @Transactional
    public int repairFromOpenAlex(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        var papers = paperRepository.findNeedingMetadataRepair(PageRequest.of(0, size));
        int repaired = 0;
        for (Paper paper : papers) {
        String idOrDoi = StringUtils.hasText(paper.getSourceIdentifier()) && "OPENALEX".equalsIgnoreCase(paper.getSourceType())
                ? paper.getSourceIdentifier()
                : paper.getDoi();
            if (!StringUtils.hasText(idOrDoi)) {
                continue;
            }
            try {
                var opt = openAlexClient.fetchWorkById(idOrDoi);
                if (opt.isEmpty()) {
                    continue;
                }
                ExternalPaperMetadata meta = opt.get();
                if (StringUtils.hasText(meta.title())) {
                    paper.setTitle(meta.title());
                }
                if (StringUtils.hasText(meta.abstractText())) {
                    paper.setAbstractText(meta.abstractText());
                }
                if (StringUtils.hasText(meta.journal())) {
                    paper.setJournal(meta.journal());
                }
                paperRepository.save(paper);
                repaired++;
            } catch (Exception ex) {
                log.warn("Repair failed for paper {}: {}", paper.getId(), ex.getMessage());
            }
        }
        return repaired;
    }
}
