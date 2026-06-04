package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.paper.PaperDetailResponse;
import com.norman.swp391.dto.response.paper.PaperResponse;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperTopic;
import com.norman.swp391.entity.Topic;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Mapper chuyển đổi thực thể Paper sang DTO.
 */
@UtilityClass
public class PaperMapper {

    /**
     * Chuyển bài báo sang DTO tóm tắt.
     */
    public static PaperResponse toResponse(Paper paper) {
        if (paper == null) {
            return null;
        }
        return PaperResponse.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .publicationDate(paper.getPublicationDate())
                .citationCount(paper.getCitationCount())
                .doi(paper.getDoi())
                .sourceUrl(paper.getSourceUrl())
                .pdfUrl(paper.getPdfUrl())
                .openAccess(paper.isOpenAccess())
                .journal(paper.getJournal())
                .journalId(paper.getJournalRef() != null ? paper.getJournalRef().getId() : null)
                .primarySource(paper.getPrimarySource())
                .status(paper.getStatus())
                .createdAt(paper.getCreatedAt())
                .build();
    }

    /**
     * Chuyển danh sách bài báo sang DTO tóm tắt.
     */
    public static List<PaperResponse> toResponseList(List<Paper> papers) {
        return papers.stream().map(PaperMapper::toResponse).toList();
    }

    /**
     * Chuyển bài báo sang DTO chi tiết kèm chủ đề và tác giả.
     */
    public static PaperDetailResponse toDetailResponse(
            Paper paper, List<Topic> topics, List<Author> authors) {
        if (paper == null) {
            return null;
        }
        List<TopicResponse> topicResponses =
                topics == null
                        ? Collections.emptyList()
                        : topics.stream().map(TopicMapper::toResponse).toList();
        List<AuthorResponse> authorResponses =
                authors == null
                        ? Collections.emptyList()
                        : authors.stream().map(AuthorMapper::toResponse).toList();

        return PaperDetailResponse.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .publicationDate(paper.getPublicationDate())
                .citationCount(paper.getCitationCount())
                .doi(paper.getDoi())
                .sourceUrl(paper.getSourceUrl())
                .pdfUrl(paper.getPdfUrl())
                .openAccess(paper.isOpenAccess())
                .journal(paper.getJournal())
                .journalId(paper.getJournalRef() != null ? paper.getJournalRef().getId() : null)
                .primarySource(paper.getPrimarySource())
                .status(paper.getStatus())
                .createdAt(paper.getCreatedAt())
                .topics(topicResponses)
                .authors(authorResponses)
                .build();
    }

    /**
     * Dựng DTO chi tiết từ quan hệ PaperTopic và PaperAuthor.
     */
    public static PaperDetailResponse toDetailResponseFromRelations(
            Paper paper, List<PaperTopic> paperTopics, List<PaperAuthor> paperAuthors) {
        List<Topic> topics =
                paperTopics == null
                        ? Collections.emptyList()
                        : paperTopics.stream().map(PaperTopic::getTopic).toList();
        List<Author> authors =
                paperAuthors == null
                        ? Collections.emptyList()
                        : paperAuthors.stream().map(PaperAuthor::getAuthor).toList();
        return toDetailResponse(paper, topics, authors);
    }
}
