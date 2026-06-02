package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.entity.Author;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Mapper AuthorMapper.
 */
@UtilityClass
public class AuthorMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static AuthorResponse toResponse(Author author) {
        if (author == null) {
            return null;
        }
        return AuthorResponse.builder()
                .id(author.getId())
                .name(author.getName())
                .affiliation(author.getAffiliation())
                .citationCount(author.getCitationCount())
                .openAlexId(author.getOpenAlexId())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<AuthorResponse> toResponseList(List<Author> authors) {
        return authors.stream().map(AuthorMapper::toResponse).toList();
    }
}


