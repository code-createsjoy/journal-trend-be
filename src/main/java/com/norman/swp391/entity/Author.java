package com.norman.swp391.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Index;

@Entity
@Table(name = "authors", indexes = {
    @Index(name = "idx_author_source", columnList = "source_type, source_identifier"),
    @Index(name = "idx_author_name_affil", columnList = "name, affiliation")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String name;

    @Nationalized
    @Column(length = 500)
    private String affiliation;

    @Column(name = "citation_count", nullable = false)
    private int citationCount;

    @Column(name = "h_index")
    private Integer hIndex;

    @Nationalized
    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Nationalized
    @Column(name = "source_identifier", length = 100)
    private String sourceIdentifier;
}
