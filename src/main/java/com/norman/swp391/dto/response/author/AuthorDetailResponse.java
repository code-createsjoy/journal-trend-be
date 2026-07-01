package com.norman.swp391.dto.response.author;
 
import com.norman.swp391.dto.response.paper.PaperResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorDetailResponse {
 
    private Long id;
    private String name;
    private String affiliation;
    private int citationCount;
    private Integer hIndex;
    private long totalPapers;
    private List<String> topKeywords;
    private List<PaperResponse> popularPapers;
}
