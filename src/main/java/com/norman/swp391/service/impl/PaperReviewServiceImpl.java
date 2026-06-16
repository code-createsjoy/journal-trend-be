package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.admin.PaperReviewOverrideRequest;
import com.norman.swp391.dto.response.admin.PaperReviewResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperReviewAudit;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperReviewAction;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperReviewAuditRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.PaperReviewService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaperReviewServiceImpl implements PaperReviewService {

    private final PaperRepository paperRepository;
    private final AppProperties appProperties;
    private final PaperReviewAuditRepository paperReviewAuditRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void applyIncomingMetadata(Paper paper, ExternalPaperMetadata incoming, String source) {
        if (paper.getReviewStatus() == PaperReviewStatus.EXPIRED) {
            return;
        }
        if (hasMetadataConflict(paper, incoming)) {
            paper.setReviewStatus(PaperReviewStatus.PENDING_REVIEW);
            paper.setReviewFlaggedAt(LocalDateTime.now());
            paper.setConflictTitle(truncateText(incoming.title(), 1000));
            paper.setConflictAbstract(incoming.abstractText());
            paper.setConflictSource(truncateText(source, 50));
            enrichEmptyFieldsOnly(paper, incoming);
        } else {
            enrichEmptyFieldsOnly(paper, incoming);
            if (paper.getReviewStatus() == PaperReviewStatus.PENDING_REVIEW) {
                // Giữ pending cho đến khi admin resolve
            }
        }
        if (incoming.citationCount() != null) {
            paper.setCitationCount(incoming.citationCount());
        }
        paperRepository.save(paper);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaperReviewResponse> listByReviewStatus(
            PaperReviewStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Page<Paper> page;
        if (from != null && to != null) {
            page = paperRepository.findByReviewStatusAndReviewFlaggedAtBetween(status, from, to, pageable);
        } else {
            page = paperRepository.findByReviewStatus(status, pageable);
        }
        return PageResponse.from(page, page.getContent().stream().map(this::toResponse).toList());
    }

    @Override
    @Transactional
    public PaperReviewResponse accept(Long paperId, String note) {
        Paper paper = getPendingPaper(paperId);
        paper.setReviewStatus(PaperReviewStatus.NONE);
        paper.setConflictTitle(null);
        paper.setConflictAbstract(null);
        paper.setConflictSource(null);
        paperRepository.save(paper);
        audit(paper, PaperReviewAction.ACCEPT, note);
        return toResponse(paper);
    }

    @Override
    @Transactional
    public PaperReviewResponse override(Long paperId, PaperReviewOverrideRequest request) {
        Paper paper = getPendingPaper(paperId);
        if (request != null) {
            if (StringUtils.hasText(request.getTitle())) {
                paper.setTitle(request.getTitle().trim());
            } else if (StringUtils.hasText(paper.getConflictTitle())) {
                paper.setTitle(paper.getConflictTitle().trim());
            }
            if (request.getAbstractText() != null) {
                paper.setAbstractText(request.getAbstractText());
            } else if (StringUtils.hasText(paper.getConflictAbstract())) {
                paper.setAbstractText(paper.getConflictAbstract());
            }
        }
        paper.setReviewStatus(PaperReviewStatus.NONE);
        paper.setConflictTitle(null);
        paper.setConflictAbstract(null);
        paper.setConflictSource(null);
        paperRepository.save(paper);
        audit(paper, PaperReviewAction.OVERRIDE, request != null ? request.getNote() : null);
        return toResponse(paper);
    }

    @Override
    @Transactional
    public void expireStalePendingReviews() {
        int slaDays = Math.max(1, appProperties.getSync().getPendingReviewExpiryDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(slaDays);
        for (Paper paper : paperRepository.findByReviewStatusAndReviewFlaggedAtBefore(
                PaperReviewStatus.PENDING_REVIEW, cutoff)) {
            paper.setReviewStatus(PaperReviewStatus.EXPIRED);
            paperRepository.save(paper);
            audit(paper, PaperReviewAction.EXPIRED, "Auto-expired after " + slaDays + " days (BR-97)");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countByReviewStatus(PaperReviewStatus status) {
        return paperRepository.countByReviewStatus(status);
    }

    private Paper getPendingPaper(Long paperId) {
        Paper paper = paperRepository
                .findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", paperId));
        if (paper.getReviewStatus() != PaperReviewStatus.PENDING_REVIEW) {
            throw new BadRequestException("Paper is not in PENDING_REVIEW status");
        }
        return paper;
    }

    private void audit(Paper paper, PaperReviewAction action, String note) {
        Long adminId = SecurityUtils.getCurrentUserId();
        User admin = adminId != null ? userRepository.findById(adminId).orElse(null) : null;
        paperReviewAuditRepository.save(PaperReviewAudit.builder()
                .paper(paper)
                .admin(admin)
                .action(action)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void enrichEmptyFieldsOnly(Paper paper, ExternalPaperMetadata incoming) {
        if (!StringUtils.hasText(paper.getAbstractText()) && StringUtils.hasText(incoming.abstractText())) {
            paper.setAbstractText(incoming.abstractText());
        }
        if (!StringUtils.hasText(paper.getDoi()) && StringUtils.hasText(incoming.doi())) {
            paper.setDoi(incoming.doi());
        }
        if (paper.getPublicationDate() == null && incoming.publicationDate() != null) {
            paper.setPublicationDate(incoming.publicationDate());
        }
        if (!StringUtils.hasText(paper.getSourceUrl()) && StringUtils.hasText(incoming.landingPageUrl())) {
            paper.setSourceUrl(incoming.landingPageUrl());
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private boolean hasMetadataConflict(Paper paper, ExternalPaperMetadata incoming) {
        if (StringUtils.hasText(paper.getTitle()) && StringUtils.hasText(incoming.title())) {
            String existing = paper.getTitle().trim().toLowerCase();
            String incomingTitle = incoming.title().trim().toLowerCase();
            if (!existing.equals(incomingTitle) && !isSimilarTitle(existing, incomingTitle)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSimilarTitle(String a, String b) {
        if (a.contains(b) || b.contains(a)) {
            return true;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return true;
        }
        int distance = levenshtein(a, b);
        return ((double) distance / maxLen) < 0.25;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private PaperReviewResponse toResponse(Paper paper) {
        return PaperReviewResponse.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .doi(paper.getDoi())
                .journal(paper.getJournal())
                .publicationDate(paper.getPublicationDate())
                .citationCount(paper.getCitationCount())
                .reviewStatus(paper.getReviewStatus())
                .reviewFlaggedAt(paper.getReviewFlaggedAt())
                .conflictTitle(paper.getConflictTitle())
                .conflictAbstract(paper.getConflictAbstract())
                .conflictSource(paper.getConflictSource())
                .build();
    }
}
