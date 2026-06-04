package com.norman.swp391.dto.response.journal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO tạp chí.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalResponse {
    Long id;
    String name;
    String publisher;
    String issn;
    String domain;
    Double impactFactor;
}


