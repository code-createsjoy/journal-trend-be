package com.norman.swp391.dto.response.paper;

import com.norman.swp391.entity.enums.PaperStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tóm tắt bài báo.
 */
@Data
@Builder
public class PaperResponse {

    private Long id;
    private String title;
    private String abstractText;
    private LocalDate publicationDate;
    private int citationCount;
    private String doi;
    private String sourceUrl;
    private String pdfUrl;
    private boolean openAccess;
    private String journal;
    private Long journalId;
    private String primarySource;
    private PaperStatus status;
    private LocalDateTime createdAt;
}


