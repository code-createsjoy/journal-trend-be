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
 * Lưu quan hệ reference giữa paper và các works được trích dẫn (OpenAlex IDs).
 */
@Entity
@Table(name = "paper_references",
        uniqueConstraints = @UniqueConstraint(columnNames = {"paper_id", "referenced_openalex_id"}),
        indexes = {
                @Index(name = "idx_pr_paper_id", columnList = "paper_id"),
                @Index(name = "idx_pr_ref_openalex_id", columnList = "referenced_openalex_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "referenced_openalex_id", nullable = false, length = 50)
    private String referencedOpenAlexId;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;
}
