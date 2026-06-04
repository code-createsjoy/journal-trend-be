package com.norman.swp391.dto.response.admin;

import com.norman.swp391.entity.enums.PaperReviewStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaperReviewResponse {
    private Long id;
    private String title;
    private String doi;
    private String journal;
    private LocalDate publicationDate;
    private int citationCount;
    private PaperReviewStatus reviewStatus;
    private LocalDateTime reviewFlaggedAt;
    private String conflictTitle;
    private String conflictAbstract;
    private String conflictSource;
}
