package com.norman.swp391.integration.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Metadata bài báo lấy từ API bên ngoài (OpenAlex).
 */
public record ExternalPaperMetadata(
        String title,
        String abstractText,
        String doi,
        LocalDate publicationDate,
        Integer citationCount,
        List<ExternalKeywordInfo> keywords,
        List<String> authors,
        String pdfUrl,
        String landingPageUrl,
        Boolean openAccess,
        String journal,
        String sourceType,
        String sourceIdentifier,
        List<ExternalAuthorInfo> authorDetails,
        List<String> referencedWorkIds) {

    public ExternalPaperMetadata {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        authors = authors == null ? List.of() : List.copyOf(authors);
        authorDetails = authorDetails == null ? List.of() : List.copyOf(authorDetails);
        referencedWorkIds = referencedWorkIds == null ? List.of() : List.copyOf(referencedWorkIds);
    }

    /**
     * Constructor tương thích khi chưa có chi tiết tác giả và referenced works.
     */
    public ExternalPaperMetadata(
            String title,
            String abstractText,
            String doi,
            LocalDate publicationDate,
            Integer citationCount,
            List<ExternalKeywordInfo> keywords,
            List<String> authors,
            String pdfUrl,
            String landingPageUrl,
            Boolean openAccess,
            String journal,
            String sourceType,
            String sourceIdentifier) {
        this(
                title,
                abstractText,
                doi,
                publicationDate,
                citationCount,
                keywords,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                sourceType,
                sourceIdentifier,
                List.of(),
                List.of());
    }

    /**
     * Constructor tương thích khi có authorDetails nhưng chưa có referenced works.
     */
    public ExternalPaperMetadata(
            String title,
            String abstractText,
            String doi,
            LocalDate publicationDate,
            Integer citationCount,
            List<ExternalKeywordInfo> keywords,
            List<String> authors,
            String pdfUrl,
            String landingPageUrl,
            Boolean openAccess,
            String journal,
            String sourceType,
            String sourceIdentifier,
            List<ExternalAuthorInfo> authorDetails) {
        this(
                title,
                abstractText,
                doi,
                publicationDate,
                citationCount,
                keywords,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                sourceType,
                sourceIdentifier,
                authorDetails,
                List.of());
    }
}
