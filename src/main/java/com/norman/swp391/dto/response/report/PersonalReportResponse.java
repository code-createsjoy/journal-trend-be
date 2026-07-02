package com.norman.swp391.dto.response.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalReportResponse {
    private TrendsSection trends;
    private List<RecommendedPaper> recommendations;
    private LandscapeSection landscape;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendsSection {
        private List<KeywordTrendPoint> lineChart;
        private List<JournalVolumePoint> barChart;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedPaper {
        private Long id;
        private String title;
        private List<String> authors;
        private String journal;
        private Integer year;
        private Integer citations;
        private String doi;
        private String recommendationReason;
        private String matchType; // e.g. "KEYWORD_OVERLAP", "FOLLOWED_AUTHOR", "TOP_CITED"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LandscapeSection {
        private List<AuthorInfluencePoint> bubbleChart;
        private List<KeywordCoOccurrencePoint> tagCloud;
        private List<ResearchGapPoint> researchGaps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordTrendPoint {
        private String term;
        private Integer year;
        private Integer month;
        private Long paperCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalVolumePoint {
        private String journalName;
        private Long paperCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorInfluencePoint {
        private Long authorId;
        private String authorName;
        private Long paperCount;
        private String mainDomain;
        private Integer matchingKeywordCount;
        private Integer hIndex;
        private Integer citationCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordCoOccurrencePoint {
        private String term;
        private Long coOccurrenceCount;
        private Double growthRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResearchGapPoint {
        private String term;
        private Long paperCount;
    }
}
