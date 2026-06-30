package com.norman.swp391.dto.response.author;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO tác giả.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorResponse {

    private Long id;
    private String name;
    private String affiliation;
    private int citationCount;
    private String sourceType;
    private String sourceIdentifier;

    private Integer papers;
    private Integer hIndex;
    private String authorPosition; // "first", "middle", "last" — only set in paper detail context
}


