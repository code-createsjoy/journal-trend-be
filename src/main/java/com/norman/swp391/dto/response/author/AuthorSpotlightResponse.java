package com.norman.swp391.dto.response.author;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 3 tác giả nổi bật: nhiều paper nhất, citation cao nhất, h-index cao nhất.
 * Field có thể null nếu chưa có dữ liệu phù hợp (VD chưa author nào được enrich hIndex).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorSpotlightResponse {

    private AuthorResponse mostPapers;
    private AuthorResponse mostCitations;
    private AuthorResponse mostHIndex;
}
