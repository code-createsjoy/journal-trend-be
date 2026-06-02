package com.norman.swp391.dto.response.paper;

import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.entity.enums.PaperStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết bài báo.
 */
@Data
@Builder
public class PaperDetailResponse {

    private Long id;
    private String title;
    private String abstractText;
    private LocalDate publicationDate;
    private int citationCount;
    private String doi;
    private String sourceUrl;
    private String pdfUrl;
    private boolean openAccess;
    private String journal;
    private Long journalId;
    private String primarySource;
    private PaperStatus status;
    private LocalDateTime createdAt;
    private List<TopicResponse> topics;
    private List<AuthorResponse> authors;
}


