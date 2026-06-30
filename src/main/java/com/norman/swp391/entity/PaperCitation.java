package com.norman.swp391.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lưu quan hệ citation — papers đang cite paper này (inbound links).
 * Cache 1 ngày, sau đó re-fetch từ OpenAlex.
 */
@Entity
@Table(name = "paper_citations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"paper_id", "citing_openalex_id"}),
        indexes = {
                @Index(name = "idx_pcit_paper_id", columnList = "paper_id"),
                @Index(name = "idx_pcit_citing_id", columnList = "citing_openalex_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "citing_openalex_id", nullable = false, length = 50)
    private String citingOpenAlexId;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;
}
