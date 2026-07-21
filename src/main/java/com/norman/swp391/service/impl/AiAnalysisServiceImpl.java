package com.norman.swp391.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.ai.AiCollectionAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiCollectionAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.entity.CollectionPaper;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperAuthor;
import com.norman.swp391.entity.PaperCollection;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.enums.AiAnalysisType;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.AiQuotaExhaustedException;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.exception.UnauthorizedException;
import com.norman.swp391.repository.CollectionPaperRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperCollectionRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.AiAnalysisHistoryService;
import com.norman.swp391.service.AiAnalysisService;
import com.norman.swp391.service.KeywordService;
import com.norman.swp391.service.KeywordTrendService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final KeywordService keywordService;
    private final KeywordTrendService keywordTrendService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AiAnalysisHistoryService aiAnalysisHistoryService;
    private final PaperCollectionRepository paperCollectionRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    @Qualifier("groqRestClient")
    private final RestClient groqRestClient;

    /**
     * Phân tích xu hướng của 1 keyword cụ thể bằng AI (Groq) — trả về nhận định
     * tăng/ổn định/giảm, điểm khả thi (feasibilityScore) và gợi ý cho nhà nghiên
     * cứu.
     */
    @Override
    public AiTrendAnalysisResponse analyzeTrend(AiTrendAnalysisRequest request) {
        String apiKey = appProperties.getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        KeywordResponse keyword = keywordService.getById(request.getKeywordId());
        List<KeywordTrendResponse> trendData = keywordService.getKeywordTrendChart(request.getKeywordId(),
                request.getMonths());

        if (trendData.isEmpty()) {
            throw new BadRequestException("No trend data available for keyword: " + keyword.getTerm());
        }

        String prompt = buildPrompt(keyword.getTerm(), trendData);
        String aiResponseText = callGroq(prompt);

        AiTrendAnalysisResponse response = parseAiResponse(aiResponseText, keyword.getTerm());
        safeSaveHistory(AiAnalysisType.SINGLE_KEYWORD, List.of(response.getKeyword()), response.getVerdict(),
                response);
        return response;
    }

    /**
     * Ghép dữ liệu trend của 1 keyword thành prompt gửi cho AI —
     * yêu cầu AI trả lời đúng theo JSON schema đã định sẵn
     * (verdict/feasibilityScore/...).
     */
    private String buildPrompt(String term, List<KeywordTrendResponse> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert analyst specializing in academic research trends. ");
        sb.append("Analyze the scientific publication trend data below and provide an in-depth assessment.\n\n");
        sb.append("Research keyword: **").append(term).append("**\n\n");
        sb.append("Monthly paper count data (last ").append(data.size()).append(" months):\n");

        for (KeywordTrendResponse d : data) {
            sb.append(String.format("  - %d/%d: %d papers", d.getMonth(), d.getYear(), d.getPaperCount()));
            if (d.getDeltaPercent() != null) {
                sb.append(String.format(" (change: %+.1f%%)", d.getDeltaPercent().doubleValue()));
            }
            sb.append("\n");
        }

        sb.append(
                """

                        Analyze the data and respond EXACTLY in the following JSON format (no markdown, no text outside the JSON):
                        {
                          "verdict": "GROWING or STABLE or DECLINING",
                          "feasibilityScore": <integer 0-100>,
                          "analysis": "<detailed overall analysis, written in English>",
                          "keyInsights": ["<insight 1, written in English>", "<insight 2, written in English>", "<insight 3, written in English>"],
                          "recommendation": "<recommendation for the researcher, written in English>"
                        }

                        feasibilityScore guide:
                        - 80-100: Booming field, highly feasible for research
                        - 60-79: Growing well, feasible
                        - 40-59: Stable, needs careful consideration
                        - 20-39: Declining trend, less feasible
                        - 0-19: Field is declining sharply
                        """);

        return sb.toString();
    }

    /**
     * Gọi Groq chat-completion API với prompt đã build, trả về chuỗi nội dung
     * (JSON thô) mà AI trả lời. Dùng chung cho cả 2 luồng phân tích (1 keyword và
     * nhiều keyword).
     * Bắt riêng lỗi 429 (hết quota Groq) để báo message thân thiện thay vì lỗi 500
     * chung chung.
     */
    private String callGroq(String prompt) {
        AppProperties.Groq cfg = appProperties.getGroq();
        String url = cfg.getBaseUrl() + "/chat/completions";

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = Map.of(
                "model", cfg.getModel(),
                "messages", List.of(message),
                "temperature", 0.4,
                "max_tokens", cfg.getMaxOutputTokens(),
                "response_format", Map.of("type", "json_object"));

        try {
            String raw = groqRestClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Groq API quota exceeded: {}", e.getMessage());
            throw new AiQuotaExhaustedException(
                    "The AI system has temporarily reached its usage quota, please try again in a few minutes.", e);
        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * So sánh xu hướng của NHIỀU keyword cùng lúc bằng AI — nếu request không chỉ
     * định
     * keywordIds cụ thể thì tự lấy top 10 keyword đang trending để phân tích.
     * Trả về keyword nào đang tăng mạnh nhất, đáng theo đuổi nhất trong nhóm.
     */
    @Override
    public AiTopTrendsAnalysisResponse analyzeTopTrends(AiTopTrendsAnalysisRequest request) {
        String apiKey = appProperties.getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        List<Long> targetIds = request.getKeywordIds();
        if (targetIds == null || targetIds.isEmpty()) {
            targetIds = keywordTrendService.findTrendingKeywords(null, null).stream()
                    .filter(k -> k != null && k.getKeywordId() != null)
                    .limit(10)
                    .map(k -> k.getKeywordId())
                    .toList();
        }

        if (targetIds.isEmpty()) {
            throw new BadRequestException("No trending keywords available for analysis");
        }

        Map<String, List<KeywordTrendResponse>> combinedData = new LinkedHashMap<>();
        for (Long keywordId : targetIds) {
            KeywordResponse kw = keywordService.getById(keywordId);
            List<KeywordTrendResponse> trendData = keywordService.getKeywordTrendChart(keywordId, request.getMonths());
            if (!trendData.isEmpty()) {
                combinedData.put(kw.getTerm(), trendData);
            }
        }

        if (combinedData.isEmpty()) {
            throw new BadRequestException("No historical trend data found for the requested keywords");
        }

        String prompt = buildTopTrendsPrompt(combinedData);
        String aiResponseText = callGroq(prompt);
        AiTopTrendsAnalysisResponse response = parseTopTrendsAiResponse(aiResponseText,
                new ArrayList<>(combinedData.keySet()));
        safeSaveHistory(AiAnalysisType.TOP_TRENDS, response.getAnalyzedKeywords(), response.getOverallVerdict(),
                response);
        return response;
    }

    /**
     * Ghép dữ liệu trend của NHIỀU keyword thành 1 prompt duy nhất, yêu cầu AI
     * so sánh chéo giữa chúng và chỉ ra keyword nào đang tăng mạnh nhất
     * (topGrowingKeywords).
     */
    private String buildTopTrendsPrompt(Map<String, List<KeywordTrendResponse>> combinedData) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a leading expert in scientific research data analysis. ");
        sb.append("Below is publication trend data for the group of most-watched technology keywords.\n\n");
        sb.append("Perform an in-depth comparative analysis between these trends.\n\n");
        sb.append("MONTHLY PAPER COUNT DATA:\n");

        combinedData.forEach((term, trends) -> {
            sb.append(String.format("\n* Keyword: **%s**\n", term));
            for (KeywordTrendResponse d : trends) {
                sb.append(String.format("  - %d/%d: %d papers", d.getMonth(), d.getYear(), d.getPaperCount()));
                if (d.getDeltaPercent() != null) {
                    sb.append(String.format(" (growth rate: %+.1f%%)", d.getDeltaPercent().doubleValue()));
                }
                sb.append("\n");
            }
        });

        sb.append(
                """

                        Perform a comparative analysis and respond EXACTLY in the following JSON format (no markdown, no text outside the JSON):
                        {
                          "overallVerdict": "GROWING or STABLE or MIXED",
                          "topGrowingKeywords": ["<keyword 1 growing fastest>", "<keyword 2 growing fastest>"],
                          "analysis": "<detailed comparative analysis of the trend lines, indicating which keywords are accelerating and which are saturating, written in English>",
                          "keyInsights": [
                            "<key insight 1, written in English>",
                            "<key insight 2, written in English>",
                            "<key insight 3, written in English>"
                          ],
                          "recommendation": "<advise the researcher which keyword to pick for a new research direction with the highest chance of riding the trend, written in English>"
                        }
                        """);

        return sb.toString();
    }

    /**
     * Parse chuỗi JSON mà AI trả về (cho luồng so sánh nhiều keyword) thành DTO.
     * Nếu JSON không hợp lệ/parse lỗi → không throw exception, mà trả về 1 response
     * "an toàn" (verdict mặc định MIXED, analysis = JSON thô) để user vẫn xem được
     * nội dung.
     */
    private AiTopTrendsAnalysisResponse parseTopTrendsAiResponse(String json, List<String> analyzedKeywords) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> growingKeywords = new ArrayList<>();
            JsonNode growingNode = node.path("topGrowingKeywords");
            if (growingNode.isArray()) {
                growingNode.forEach(n -> growingKeywords.add(n.asText()));
            }

            List<String> insights = new ArrayList<>();
            JsonNode insightsNode = node.path("keyInsights");
            if (insightsNode.isArray()) {
                insightsNode.forEach(n -> insights.add(n.asText()));
            }

            String rawVerdict = node.path("overallVerdict").asText("MIXED").toUpperCase().trim();
            String verdict = List.of("GROWING", "STABLE", "MIXED").contains(rawVerdict) ? rawVerdict : "MIXED";

            return AiTopTrendsAnalysisResponse.builder()
                    .overallVerdict(verdict)
                    .analyzedKeywords(analyzedKeywords)
                    .topGrowingKeywords(growingKeywords)
                    .analysis(node.path("analysis").asText())
                    .keyInsights(insights)
                    .recommendation(node.path("recommendation").asText())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI top-trends response: {}", json);
            return AiTopTrendsAnalysisResponse.builder()
                    .overallVerdict("MIXED")
                    .analyzedKeywords(analyzedKeywords)
                    .topGrowingKeywords(List.of())
                    .analysis(json)
                    .keyInsights(List.of())
                    .recommendation("Unable to parse the AI response, please refer to the raw analysis content.")
                    .build();
        }
    }

    /**
     * Parse chuỗi JSON mà AI trả về (cho luồng phân tích 1 keyword) thành DTO.
     * Nếu JSON không hợp lệ/parse lỗi → không throw exception, mà trả về 1 response
     * "an toàn" (verdict mặc định STABLE, feasibilityScore = 50, analysis = JSON
     * thô).
     */
    private AiTrendAnalysisResponse parseAiResponse(String json, String keyword) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> insights = new ArrayList<>();
            JsonNode insightsNode = node.path("keyInsights");
            if (insightsNode.isArray()) {
                insightsNode.forEach(n -> insights.add(n.asText()));
            }

            String rawVerdict = node.path("verdict").asText("STABLE").toUpperCase().trim();
            String verdict = List.of("GROWING", "STABLE", "DECLINING").contains(rawVerdict) ? rawVerdict : "STABLE";

            return AiTrendAnalysisResponse.builder()
                    .keyword(keyword)
                    .verdict(verdict)
                    .feasibilityScore(node.path("feasibilityScore").asInt(50))
                    .analysis(node.path("analysis").asText())
                    .keyInsights(insights)
                    .recommendation(node.path("recommendation").asText())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", json);
            return AiTrendAnalysisResponse.builder()
                    .keyword(keyword)
                    .verdict("STABLE")
                    .feasibilityScore(50)
                    .analysis(json)
                    .keyInsights(List.of())
                    .recommendation("Automatic analysis failed, please see the analysis section.")
                    .build();
        }
    }

    /**
     * Phân tích AI paper trong 1 collection của user hiện tại, dựa trên metadata
     * (title/abstract/year/citations/keywords/authors) — không có full text. Nếu
     * request.paperIds có giá trị, chỉ phân tích đúng các paper đó (phải thuộc
     * collection, số lượng không vượt quá cap); nếu không, tự lấy các paper lưu
     * gần nhất tối đa cap. Trả về topic clusters, xu hướng theo thời gian, điểm
     * chung/khác biệt, paper trọng tâm, research gap và collaboration
     * highlights.
     */
    @Override
    @Transactional(readOnly = true)
    public AiCollectionAnalysisResponse analyzeCollection(Long collectionId, AiCollectionAnalysisRequest request) {
        String apiKey = appProperties.getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        PaperCollection collection = getOwnedCollection(collectionId);

        List<Paper> papers = collectionPaperRepository.findByCollectionIdOrderBySavedAtDesc(collectionId).stream()
                .map(CollectionPaper::getPaper)
                .filter(p -> p.getStatus() == PaperStatus.ACTIVE)
                .toList();

        if (papers.isEmpty()) {
            throw new BadRequestException("Collection has no papers to analyze");
        }

        int maxPapers = appProperties.getSync().getMaxPapersForCollectionAnalysis();
        List<Long> requestedIds = request != null ? request.getPaperIds() : null;
        List<Paper> analyzed;
        if (requestedIds != null && !requestedIds.isEmpty()) {
            if (requestedIds.size() > maxPapers) {
                throw new BadRequestException(
                        "You selected " + requestedIds.size() + " papers, but at most " + maxPapers
                                + " can be analyzed at once");
            }
            Set<Long> requestedSet = new LinkedHashSet<>(requestedIds);
            analyzed = papers.stream().filter(p -> requestedSet.contains(p.getId())).toList();
            if (analyzed.isEmpty()) {
                throw new BadRequestException("None of the selected papers belong to this collection");
            }
        } else {
            analyzed = papers.size() > maxPapers ? papers.subList(0, maxPapers) : papers;
        }
        List<Long> paperIds = analyzed.stream().map(Paper::getId).toList();
        // keySet() đóng vai trò "tập id hợp lệ" để lọc paperId AI bịa; value là title thật lấy từ
        // DB, không qua AI, để FE hiển thị được ngay trong topicClusters mà không cần tự tra cứu.
        Map<Long, String> paperTitlesById = analyzed.stream()
                .collect(Collectors.toMap(Paper::getId, Paper::getTitle, (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<String>> keywordsByPaperId = paperKeywordRepository.findByPaperIdInWithKeyword(paperIds)
                .stream()
                .collect(Collectors.groupingBy(pk -> pk.getPaper().getId(), LinkedHashMap::new,
                        Collectors.mapping(pk -> pk.getKeyword().getTerm(), Collectors.toList())));
        Map<Long, List<String>> authorsByPaperId = paperAuthorRepository.findByPaperIdInWithAuthor(paperIds).stream()
                .collect(Collectors.groupingBy(pa -> pa.getPaper().getId(), LinkedHashMap::new,
                        Collectors.mapping(pa -> pa.getAuthor().getName(), Collectors.toList())));

        String prompt = buildCollectionPrompt(collection.getName(), analyzed, keywordsByPaperId, authorsByPaperId);
        String aiResponseText = callGroq(prompt);

        AiCollectionAnalysisResponse response = parseCollectionAiResponse(aiResponseText, collection, papers.size(),
                analyzed.size(), paperTitlesById);
        safeSaveHistory(AiAnalysisType.COLLECTION_ANALYSIS, buildHistoryTargetKeywords(collection, response),
                truncate(response.getOverallSummary(), 50), response);
        return response;
    }

    /**
     * targetKeywords cho lịch sử collection analysis: [0] = tên collection kèm số bài đã phân
     * tích (tín hiệu chắc chắn phân biệt được 2 lần chạy khác nhau, vì số bài luôn khác khi user
     * đổi lựa chọn) + tên các topic cluster AI trích xuất được (gợi ý nội dung, dễ đọc hơn nhưng
     * không đảm bảo phân biệt tuyệt đối — corpus hiện thiên nhiều về AI/CS nên cluster name giữa
     * các lần chạy có thể trùng nhau).
     */
    private List<String> buildHistoryTargetKeywords(PaperCollection collection, AiCollectionAnalysisResponse response) {
        List<String> targetKeywords = new ArrayList<>();
        targetKeywords.add(collection.getName() + " (" + response.getAnalyzedPaperCount() + " papers)");
        if (response.getTopicClusters() != null) {
            for (AiCollectionAnalysisResponse.TopicCluster cluster : response.getTopicClusters()) {
                if (cluster.getName() != null && !cluster.getName().isBlank()) {
                    targetKeywords.add(cluster.getName());
                }
            }
        }
        return targetKeywords;
    }

    /** overall_verdict is capped at 50 chars in the DB — never pass free-form AI text there untruncated. */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }

    /**
     * Bọc aiAnalysisHistoryService.saveHistory() bằng try-catch riêng ở đây, ngoài
     * @Transactional(REQUIRES_NEW) đã có trong AiAnalysisHistoryServiceImpl. Chỉ REQUIRES_NEW là
     * chưa đủ: khi INSERT lịch sử thất bại (VD vi phạm CHECK constraint/độ dài cột), transaction
     * MỚI mà saveHistory tự tạo cũng bị Hibernate đánh dấu rollback-only nội bộ — dù exception gốc
     * đã bị bắt bên trong saveHistory, khi method đó return bình thường, Spring vẫn cố commit
     * transaction của chính nó và ném UnexpectedRollbackException ra ngoài lời gọi. Nếu không bắt
     * ở đây, exception này vẫn lan tới caller (analyzeTrend/analyzeTopTrends/analyzeCollection) dù
     * saveHistory được thiết kế là non-fatal. Đã verify bằng test
     * AiHistoryRollbackBugTest — REQUIRES_NEW không tự đủ, bắt buộc phải có lớp try-catch này.
     */
    private void safeSaveHistory(AiAnalysisType type, List<String> targetKeywords, String overallVerdict,
            Object rawResponse) {
        try {
            aiAnalysisHistoryService.saveHistory(type, targetKeywords, overallVerdict, rawResponse);
        } catch (Exception ex) {
            log.warn("[AI_HISTORY] saveHistory call failed, ignoring (non-fatal): {}", ex.getMessage());
        }
    }

    /**
     * Ghép metadata (title/year/journal/citations/keywords/authors/abstract rút
     * gọn) của các paper trong collection thành 1 prompt, yêu cầu AI trả lời đúng
     * JSON schema của AiCollectionAnalysisResponse.
     */
    private String buildCollectionPrompt(String collectionName, List<Paper> papers,
            Map<Long, List<String>> keywordsByPaperId, Map<Long, List<String>> authorsByPaperId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert research analyst. Analyze the following collection of academic papers ");
        sb.append("using ONLY the metadata below (title, abstract, year, journal, citations, keywords, authors). ");
        sb.append("No full text is available, so do not invent details about methodology or results.\n\n");
        sb.append("Collection: **").append(collectionName).append("** (").append(papers.size()).append(" papers)\n\n");

        for (Paper p : papers) {
            sb.append(String.format("- [id=%d] \"%s\" (%s, %s), citations=%d%n",
                    p.getId(),
                    p.getTitle(),
                    p.getPublicationDate() != null ? p.getPublicationDate().getYear() : "n/a",
                    p.getJournal() != null ? p.getJournal() : "n/a",
                    p.getCitationCount()));
            List<String> keywords = keywordsByPaperId.getOrDefault(p.getId(), Collections.emptyList());
            if (!keywords.isEmpty()) {
                sb.append("  keywords: ").append(String.join(", ", keywords)).append("\n");
            }
            List<String> authors = authorsByPaperId.getOrDefault(p.getId(), Collections.emptyList());
            if (!authors.isEmpty()) {
                sb.append("  authors: ").append(String.join(", ", authors)).append("\n");
            }
            String abstractText = p.getAbstractText();
            if (abstractText != null && !abstractText.isBlank()) {
                sb.append("  abstract: ")
                        .append(abstractText.length() > 500 ? abstractText.substring(0, 500) + "..." : abstractText)
                        .append("\n");
            }
        }

        sb.append(
                """

                        Analyze the collection and respond EXACTLY in the following JSON format (no markdown, no text outside the JSON). Paper ids referenced in the output MUST come from the [id=...] values above:
                        {
                          "overallSummary": "<2-4 sentences: what this collection is about overall, written in English>",
                          "topicClusters": [
                            {"name": "<short topic name>", "description": "<1-2 sentences>", "paperIds": [<id>, <id>]}
                          ],
                          "trendOverTime": "<narrative on which topics/years are rising or declining, based on year and citations, written in English>",
                          "commonalities": "<what most papers in the collection share in common, written in English>",
                          "outliers": [
                            {"paperId": <id>, "title": "<title>", "reason": "<why it stands out from the rest>"}
                          ],
                          "corePapers": [
                            {"paperId": <id>, "title": "<title>", "citationCount": <int>, "reason": "<why it is foundational/central to the collection>"}
                          ],
                          "researchGaps": ["<under-covered sub-topic 1>", "<under-covered sub-topic 2>"],
                          "collaborationHighlights": "<notable author or journal collaboration patterns, or empty string if none stand out>",
                          "recommendation": "<actionable recommendation for a researcher looking at this collection, written in English>"
                        }
                        """);

        return sb.toString();
    }

    /**
     * Parse chuỗi JSON mà AI trả về (cho luồng phân tích collection) thành DTO.
     * Nếu JSON không hợp lệ/parse lỗi → không throw exception, mà trả về 1
     * response "an toàn" (analysis rỗng, overallSummary = JSON thô) để user vẫn
     * xem được nội dung. paperTitlesById (keySet = id hợp lệ) lọc bỏ paperId mà AI
     * bịa ra, đồng thời cung cấp title THẬT lấy từ DB — không dùng title do AI tự
     * viết ra, vì AI có thể gõ sai/paraphrase title dù id đúng.
     */
    private AiCollectionAnalysisResponse parseCollectionAiResponse(String json, PaperCollection collection,
            int paperCount, int analyzedCount, Map<Long, String> paperTitlesById) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<AiCollectionAnalysisResponse.TopicCluster> topicClusters = new ArrayList<>();
            node.path("topicClusters").forEach(n -> {
                List<AiCollectionAnalysisResponse.PaperRef> refs = new ArrayList<>();
                n.path("paperIds").forEach(idNode -> {
                    long id = idNode.asLong();
                    String title = paperTitlesById.get(id);
                    if (title != null) {
                        refs.add(AiCollectionAnalysisResponse.PaperRef.builder().paperId(id).title(title).build());
                    }
                });
                // Bỏ hẳn cluster nếu mọi paperId của nó đều là AI bịa (bị lọc hết) — một cluster
                // không còn bài thật nào thì không nên xuất hiện trong response lẫn trong
                // targetKeywords của lịch sử (xem buildHistoryTargetKeywords).
                if (!refs.isEmpty()) {
                    topicClusters.add(AiCollectionAnalysisResponse.TopicCluster.builder()
                            .name(n.path("name").asText())
                            .description(n.path("description").asText())
                            .papers(refs)
                            .build());
                }
            });

            List<AiCollectionAnalysisResponse.OutlierPaper> outliers = new ArrayList<>();
            node.path("outliers").forEach(n -> {
                long id = n.path("paperId").asLong();
                String title = paperTitlesById.get(id);
                if (title != null) {
                    outliers.add(AiCollectionAnalysisResponse.OutlierPaper.builder()
                            .paperId(id)
                            .title(title)
                            .reason(n.path("reason").asText())
                            .build());
                }
            });

            List<AiCollectionAnalysisResponse.CorePaper> corePapers = new ArrayList<>();
            node.path("corePapers").forEach(n -> {
                long id = n.path("paperId").asLong();
                String title = paperTitlesById.get(id);
                if (title != null) {
                    corePapers.add(AiCollectionAnalysisResponse.CorePaper.builder()
                            .paperId(id)
                            .title(title)
                            .citationCount(n.path("citationCount").asInt(0))
                            .reason(n.path("reason").asText())
                            .build());
                }
            });

            List<String> researchGaps = new ArrayList<>();
            node.path("researchGaps").forEach(n -> researchGaps.add(n.asText()));

            return AiCollectionAnalysisResponse.builder()
                    .collectionId(collection.getId())
                    .collectionName(collection.getName())
                    .paperCount(paperCount)
                    .analyzedPaperCount(analyzedCount)
                    .overallSummary(node.path("overallSummary").asText())
                    .topicClusters(topicClusters)
                    .trendOverTime(node.path("trendOverTime").asText())
                    .commonalities(node.path("commonalities").asText())
                    .outliers(outliers)
                    .corePapers(corePapers)
                    .researchGaps(researchGaps)
                    .collaborationHighlights(node.path("collaborationHighlights").asText())
                    .recommendation(node.path("recommendation").asText())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI collection-analysis response: {}", json);
            return AiCollectionAnalysisResponse.builder()
                    .collectionId(collection.getId())
                    .collectionName(collection.getName())
                    .paperCount(paperCount)
                    .analyzedPaperCount(analyzedCount)
                    .overallSummary(json)
                    .topicClusters(List.of())
                    .trendOverTime("")
                    .commonalities("")
                    .outliers(List.of())
                    .corePapers(List.of())
                    .researchGaps(List.of())
                    .collaborationHighlights("")
                    .recommendation("Unable to parse the AI response, please refer to the overallSummary field.")
                    .build();
        }
    }

    private PaperCollection getOwnedCollection(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return paperCollectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
    }
}
