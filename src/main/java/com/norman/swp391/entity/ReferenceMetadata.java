package com.norman.swp391.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

/**
 * Cache nhẹ cho metadata của referenced works (chỉ title, year, doi, citationCount).
 * Tránh phải fetch lại từ OpenAlex mỗi lần user xem references graph.
 */
@Entity
@Table(name = "reference_metadata", indexes = {
        @Index(name = "idx_rm_openalex_id", columnList = "openalex_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferenceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "openalex_id", nullable = false, unique = true, length = 50)
    private String openAlexId;

    @Nationalized
    @Column(length = 1000)
    private String title;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(length = 255)
    private String doi;

    @Column(name = "citation_count")
    private Integer citationCount;

    /** ID của paper trong DB local, NULL nếu paper chưa được sync vào hệ thống. */
    @Column(name = "local_paper_id")
    private Long localPaperId;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
