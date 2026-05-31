package com.norman.swp391.persistence.entity;

import com.research.trend.persistence.entity.enums.PaperStatus;
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

@Entity
@Table(name = "papers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "abstract_text", columnDefinition = "NVARCHAR(MAX)")
    private String abstractText;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "citation_count", nullable = false)
    private int citationCount;

    @Column(length = 255, unique = true)
    private String doi;

    @Column(name = "open_alex_id", length = 64)
    private String openAlexId;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "open_access", nullable = false)
    private boolean openAccess;

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
}
