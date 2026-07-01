package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.PaperAuthor;
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
                .hIndex(author.getHIndex())
                .sourceType(author.getSourceType())
                .sourceIdentifier(author.getSourceIdentifier())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<AuthorResponse> toResponseList(List<Author> authors) {
        return authors.stream().map(AuthorMapper::toResponse).toList();
    }

    public static AuthorResponse toResponseWithPosition(PaperAuthor paperAuthor) {
        if (paperAuthor == null) return null;
        AuthorResponse r = toResponse(paperAuthor.getAuthor());
        if (r != null) r.setAuthorPosition(paperAuthor.getAuthorPosition());
        return r;
    }
}


