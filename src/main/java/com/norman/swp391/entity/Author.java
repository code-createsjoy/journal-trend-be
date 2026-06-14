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

@Entity
@Table(name = "authors")
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

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_identifier", length = 100)
    private String sourceIdentifier;
}
