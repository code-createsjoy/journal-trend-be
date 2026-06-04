package com.norman.swp391.integration.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Metadata bài báo lấy từ API bên ngoài (OpenAlex, Semantic Scholar).
 */
public record ExternalPaperMetadata(
        String title,
        String abstractText,
        String doi,
        LocalDate publicationDate,
        Integer citationCount,
        List<String> topics,
        List<String> authors,
        String pdfUrl,
        String landingPageUrl,
        Boolean openAccess,
        String journal,
        String openAlexId,
        List<ExternalAuthorInfo> authorDetails) {

    public ExternalPaperMetadata {
        topics = topics == null ? List.of() : List.copyOf(topics);
        authors = authors == null ? List.of() : List.copyOf(authors);
        authorDetails = authorDetails == null ? List.of() : List.copyOf(authorDetails);
    }

    /**
     * Constructor tương thích khi chưa có chi tiết tác giả.
     */
    public ExternalPaperMetadata(
            String title,
            String abstractText,
            String doi,
            LocalDate publicationDate,
            Integer citationCount,
            List<String> topics,
            List<String> authors,
            String pdfUrl,
            String landingPageUrl,
            Boolean openAccess,
            String journal,
            String openAlexId) {
        this(
                title,
                abstractText,
                doi,
                publicationDate,
                citationCount,
                topics,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                openAlexId,
                List.of());
    }
}
