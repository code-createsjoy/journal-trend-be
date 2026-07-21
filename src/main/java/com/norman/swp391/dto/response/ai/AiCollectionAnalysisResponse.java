package com.norman.swp391.dto.response.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCollectionAnalysisResponse {
    private Long collectionId;
    private String collectionName;
    /** Total ACTIVE papers in the collection. */
    private int paperCount;
    /** How many of those papers were actually sent to the AI (capped by app.sync.max-papers-for-collection-analysis). */
    private int analyzedPaperCount;

    /** What the collection is about overall, in a few sentences. */
    private String overallSummary;

    /** Main topic clusters found across the papers' abstracts/keywords. */
    private List<TopicCluster> topicClusters;

    /** Narrative on which topics are rising/declining, based on year + trendScore + citations. */
    private String trendOverTime;

    /** Common threads shared across most papers in the collection. */
    private String commonalities;

    /** Papers that stand out from the rest of the collection. */
    private List<OutlierPaper> outliers;

    /** Most-cited / foundational papers within the collection. */
    private List<CorePaper> corePapers;

    /** Under-covered sub-topics relative to the rest of the collection (optional). */
    private List<String> researchGaps;

    /** Notable author/journal collaboration patterns (optional). */
    private String collaborationHighlights;

    private String recommendation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicCluster {
        private String name;
        private String description;
        /** Title lấy thẳng từ DB (không qua AI) — luôn khớp đúng paper thật, không thể bị AI gõ sai/hallucinate. */
        private List<PaperRef> papers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaperRef {
        private Long paperId;
        private String title;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OutlierPaper {
        private Long paperId;
        private String title;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CorePaper {
        private Long paperId;
        private String title;
        private int citationCount;
        private String reason;
    }
}
