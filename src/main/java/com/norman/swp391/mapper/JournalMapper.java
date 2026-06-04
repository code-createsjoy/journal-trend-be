package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.journal.JournalResponse;
import com.norman.swp391.entity.Journal;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Mapper JournalMapper.
 */
@UtilityClass
public class JournalMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static JournalResponse toResponse(Journal journal) {
        if (journal == null) {
            return null;
        }
        return JournalResponse.builder()
                .id(journal.getId())
                .name(journal.getName())
                .publisher(journal.getPublisher())
                .issn(journal.getIssn())
                .domain(journal.getDomain())
                .impactFactor(journal.getImpactFactor() != null ? journal.getImpactFactor().doubleValue() : 0.0)
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<JournalResponse> toResponseList(List<Journal> journals) {
        return journals.stream().map(JournalMapper::toResponse).toList();
    }
}


