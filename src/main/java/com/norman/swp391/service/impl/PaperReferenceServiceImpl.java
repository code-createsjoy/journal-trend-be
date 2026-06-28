package com.norman.swp391.service.impl;

import com.norman.swp391.dto.helix.HelixDtos.HelixCitationNode;
import com.norman.swp391.dto.helix.HelixDtos.HelixReferenceNode;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperReference;
import com.norman.swp391.entity.ReferenceMetadata;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.repository.PaperReferenceRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.ReferenceMetadataRepository;
import com.norman.swp391.service.PaperReferenceService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation cho PaperReferenceService.
 * <p>
 * Luồng:
 * 1. Lookup paper_references cho paperId.
 * 2. Nếu chưa có → fetch referenced_works từ OpenAlex, lưu vào paper_references.
 * 3. Batch lookup reference_metadata cho các OpenAlex IDs.
 * 4. Cache miss → batch fetch từ OpenAlex, lưu vào reference_metadata.
 * 5. Cross-reference với papers table để xác định localPaperId / existsLocally.
 * 6. Trả về List<HelixReferenceNode>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperReferenceServiceImpl implements PaperReferenceService {

    private final PaperRepository paperRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final ReferenceMetadataRepository referenceMetadataRepository;
    private final OpenAlexClient openAlexClient;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate getRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Override
    public List<HelixReferenceNode> getReferences(Long paperId, int limit) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found: " + paperId));

        // 1. Lookup existing references
        List<PaperReference> refs = paperReferenceRepository.findByPaperId(paperId);

        // 2. Nếu chưa có → lazy fetch từ OpenAlex
        if (refs.isEmpty() && StringUtils.hasText(paper.getSourceIdentifier())) {
            refs = fetchAndSaveReferences(paper);
        }

        if (refs.isEmpty()) {
            return List.of();
        }

        // Apply limit
        List<PaperReference> limitedRefs = refs.size() > limit ? refs.subList(0, limit) : refs;
        List<String> openAlexIds = limitedRefs.stream()
                .map(PaperReference::getReferencedOpenAlexId)
                .toList();

        // 3. Batch lookup cached metadata — chỉ lấy entries còn fresh (trong 7 ngày)
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(7);
        Map<String, ReferenceMetadata> cachedMap = new HashMap<>();
        List<ReferenceMetadata> cachedList =
                referenceMetadataRepository.findByOpenAlexIdInAndFetchedAtAfter(openAlexIds, staleThreshold);
        for (ReferenceMetadata rm : cachedList) {
            cachedMap.put(rm.getOpenAlexId(), rm);
        }

        // 4. Identify cache misses (chưa có hoặc đã stale) và batch fetch
        List<String> missingIds = openAlexIds.stream()
                .filter(id -> !cachedMap.containsKey(id))
                .distinct()
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            log.info("[REF] Fetching metadata for {} uncached references from OpenAlex", missingIds.size());
            try {
                List<ExternalPaperMetadata> fetched = openAlexClient.fetchWorksByIds(missingIds);
                Map<String, ExternalPaperMetadata> fetchedMap = new HashMap<>();
                for (ExternalPaperMetadata meta : fetched) {
                    if (StringUtils.hasText(meta.sourceIdentifier())) {
                        fetchedMap.put(meta.sourceIdentifier(), meta);
                    }
                }

                // Load existing DB rows (may be stale or concurrent inserts)
                List<ReferenceMetadata> existingMeta = referenceMetadataRepository.findByOpenAlexIdIn(missingIds);
                Map<String, ReferenceMetadata> existingMetaMap = existingMeta.stream()
                        .collect(Collectors.toMap(ReferenceMetadata::getOpenAlexId, rm -> rm, (a, b) -> a));

                List<ReferenceMetadata> toSave = new ArrayList<>();
                LocalDateTime now = LocalDateTime.now();
                for (String missingId : missingIds) {
                    ExternalPaperMetadata meta = fetchedMap.get(missingId);
                    ReferenceMetadata existing = existingMetaMap.get(missingId);
                    if (existing != null) {
                        // Entry exists but may be stale — update in place with fresh data
                        existing.setTitle(meta != null ? meta.title() : existing.getTitle());
                        existing.setPublicationYear(meta != null && meta.publicationDate() != null
                                ? meta.publicationDate().getYear() : existing.getPublicationYear());
                        existing.setDoi(meta != null ? meta.doi() : existing.getDoi());
                        existing.setCitationCount(meta != null ? meta.citationCount() : existing.getCitationCount());
                        existing.setFetchedAt(now);
                        toSave.add(existing);
                        cachedMap.put(missingId, existing);
                    } else {
                        // Truly new entry — insert
                        ReferenceMetadata rm = ReferenceMetadata.builder()
                                .openAlexId(missingId)
                                .title(meta != null ? meta.title() : null)
                                .publicationYear(meta != null && meta.publicationDate() != null
                                        ? meta.publicationDate().getYear() : null)
                                .doi(meta != null ? meta.doi() : null)
                                .citationCount(meta != null ? meta.citationCount() : null)
                                .fetchedAt(now)
                                .build();
                        toSave.add(rm);
                        cachedMap.put(missingId, rm);
                    }
                }
                if (!toSave.isEmpty()) {
                    try {
                        getRequiresNewTemplate().executeWithoutResult(status -> {
                            referenceMetadataRepository.saveAll(toSave);
                        });
                    } catch (Exception ex) {
                        log.warn("[REF] Unique constraint violation or error saving reference metadata in REQUIRES_NEW, reloading: {}", ex.getMessage());
                        // reload whatever was saved
                        List<ReferenceMetadata> reloaded = referenceMetadataRepository.findByOpenAlexIdIn(missingIds);
                        for (ReferenceMetadata rm : reloaded) {
                            cachedMap.put(rm.getOpenAlexId(), rm);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[REF] Failed to fetch/save reference metadata from OpenAlex: {}", ex.getMessage());
            }
        }

        // 5. Cross-reference with local papers (by sourceIdentifier)
        Map<String, Long> localPaperMap = new HashMap<>();
        List<Paper> localPapers = paperRepository.findBySourceTypeAndSourceIdentifierIn("OPENALEX", openAlexIds);
        for (Paper p : localPapers) {
            if (StringUtils.hasText(p.getSourceIdentifier())) {
                localPaperMap.put(p.getSourceIdentifier(), p.getId());
            }
        }

        // Update localPaperId in cached metadata if newly matched
        for (ReferenceMetadata rm : cachedMap.values()) {
            if (rm == null) continue;
            Long localId = localPaperMap.get(rm.getOpenAlexId());
            if (localId != null && rm.getLocalPaperId() == null) {
                rm.setLocalPaperId(localId);
                try {
                    getRequiresNewTemplate().executeWithoutResult(status -> {
                        referenceMetadataRepository.save(rm);
                    });
                } catch (Exception ex) {
                    // Ignore concurrent update errors
                }
            }
        }

        // 6. Build response
        List<HelixReferenceNode> result = new ArrayList<>();
        for (String openAlexId : openAlexIds) {
            ReferenceMetadata rm = cachedMap.get(openAlexId);
            Long localId = localPaperMap.get(openAlexId);
            result.add(new HelixReferenceNode(
                    openAlexId,
                    rm != null ? rm.getTitle() : null,
                    rm != null ? rm.getPublicationYear() : null,
                    rm != null ? rm.getDoi() : null,
                    rm != null ? rm.getCitationCount() : null,
                    localId != null ? localId.toString() : null,
                    localId != null
            ));
        }
        return result;
    }

    /**
     * Fetch referenced_works từ OpenAlex và lưu vào paper_references.
     */
    private List<PaperReference> fetchAndSaveReferences(Paper paper) {
        try {
            List<String> refIds = openAlexClient.extractReferencedWorkIds(paper.getSourceIdentifier());
            if (refIds.isEmpty()) {
                return List.of();
            }

            // Deduplicate incoming IDs
            List<String> uniqueRefIds = refIds.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();

            log.info("[REF] Paper {} has {} unique references from OpenAlex", paper.getId(), uniqueRefIds.size());

            // Double check inside transaction to see if any references were inserted concurrently
            List<PaperReference> existing = paperReferenceRepository.findByPaperId(paper.getId());
            Set<String> existingOpenAlexIds = existing.stream()
                    .map(PaperReference::getReferencedOpenAlexId)
                    .collect(Collectors.toSet());

            List<PaperReference> refsToSave = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (String refId : uniqueRefIds) {
                if (!existingOpenAlexIds.contains(refId)) {
                    refsToSave.add(PaperReference.builder()
                            .paperId(paper.getId())
                            .referencedOpenAlexId(refId)
                            .fetchedAt(now)
                            .build());
                }
            }

            if (!refsToSave.isEmpty()) {
                try {
                    List<PaperReference> saved = getRequiresNewTemplate().execute(status -> {
                        paperReferenceRepository.saveAll(refsToSave);
                        List<PaperReference> combined = new ArrayList<>(existing);
                        combined.addAll(refsToSave);
                        return combined;
                    });
                    return saved != null ? saved : existing;
                } catch (Exception ex) {
                    log.warn("[REF] Conflict/error saving references for paper {} in REQUIRES_NEW, fetching existing: {}", paper.getId(), ex.getMessage());
                    return paperReferenceRepository.findByPaperId(paper.getId());
                }
            }
            return existing;
        } catch (Exception ex) {
            log.warn("[REF] Failed to fetch/save references for paper {}: {}", paper.getId(), ex.getMessage());
            return paperReferenceRepository.findByPaperId(paper.getId());
        }
    }

    @Override
    public List<HelixCitationNode> getCitations(Long paperId, String sort, Integer yearFrom, Integer yearTo, int limit) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found: " + paperId));

        if (!StringUtils.hasText(paper.getSourceIdentifier())) {
            return List.of();
        }

        // Map sort parameter to OpenAlex sort format
        String openAlexSort;
        if ("recent".equalsIgnoreCase(sort)) {
            openAlexSort = "publication_date:desc";
        } else {
            // Default: citation count descending
            openAlexSort = "cited_by_count:desc";
        }

        try {
            List<ExternalPaperMetadata> citingWorks = openAlexClient.fetchCitingWorks(
                    paper.getSourceIdentifier(), openAlexSort, yearFrom, yearTo, limit);

            if (citingWorks.isEmpty()) {
                return List.of();
            }

            // Cross-reference with local papers
            List<String> citingIds = citingWorks.stream()
                    .map(ExternalPaperMetadata::sourceIdentifier)
                    .filter(StringUtils::hasText)
                    .toList();

            Map<String, Long> localPaperMap = new HashMap<>();
            if (!citingIds.isEmpty()) {
                List<Paper> localPapers = paperRepository.findBySourceTypeAndSourceIdentifierIn("OPENALEX", citingIds);
                for (Paper p : localPapers) {
                    if (StringUtils.hasText(p.getSourceIdentifier())) {
                        localPaperMap.put(p.getSourceIdentifier(), p.getId());
                    }
                }
            }

            // Build response
            List<HelixCitationNode> result = new ArrayList<>();
            for (ExternalPaperMetadata meta : citingWorks) {
                Long localId = localPaperMap.get(meta.sourceIdentifier());
                result.add(new HelixCitationNode(
                        meta.sourceIdentifier(),
                        meta.title(),
                        meta.publicationDate() != null ? meta.publicationDate().getYear() : null,
                        meta.doi(),
                        meta.citationCount(),
                        localId != null ? localId.toString() : null,
                        localId != null
                ));
            }
            return result;
        } catch (Exception ex) {
            log.warn("[CIT] Failed to fetch citations for paper {}: {}", paper.getId(), ex.getMessage());
            return List.of();
        }
    }
}
