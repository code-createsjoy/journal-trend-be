package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Index;

@Entity
@Table(name = "papers", indexes = {
    @Index(name = "idx_paper_source", columnList = "source_type, source_identifier"),
    @Index(name = "idx_paper_doi", columnList = "doi"),
    @Index(name = "idx_paper_source_identifier", columnList = "source_identifier")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "abstract_text", columnDefinition = "NVARCHAR(MAX)")
    private String abstractText;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "citation_count", nullable = false)
    private int citationCount;

    @Nationalized
    @Column(length = 255, unique = true)
    private String doi;

    @Nationalized
    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Nationalized
    @Column(name = "source_identifier", length = 100)
    private String sourceIdentifier;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "open_access", nullable = false)
    private boolean openAccess;

    @Nationalized
    @Column(length = 500)
    private String journal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journalRef;

    @Column(name = "primary_source", length = 50)
    private String primarySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaperStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private PaperReviewStatus reviewStatus = PaperReviewStatus.NONE;

    @Column(name = "review_flagged_at")
    private LocalDateTime reviewFlaggedAt;

    @Column(name = "conflict_title", length = 1000, columnDefinition = "VARCHAR(1000)")
    private String conflictTitle;

    @Column(name = "conflict_abstract", columnDefinition = "VARCHAR(MAX)")
    private String conflictAbstract;

    @Column(name = "conflict_source", length = 50)
    private String conflictSource;
}
