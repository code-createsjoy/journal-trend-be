package com.norman.swp391.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.ai.AiTopTrendsAnalysisRequest;
import com.norman.swp391.dto.request.ai.AiTrendAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiTopTrendsAnalysisResponse;
import com.norman.swp391.dto.response.ai.AiTrendAnalysisResponse;
import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.entity.enums.AiAnalysisType;
import com.norman.swp391.exception.AiQuotaExhaustedException;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.service.AiAnalysisHistoryService;
import com.norman.swp391.service.AiAnalysisService;
import com.norman.swp391.service.KeywordService;
import com.norman.swp391.service.KeywordTrendService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final KeywordService keywordService;
    private final KeywordTrendService keywordTrendService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AiAnalysisHistoryService aiAnalysisHistoryService;
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
        aiAnalysisHistoryService.saveHistory(AiAnalysisType.SINGLE_KEYWORD, List.of(response.getKeyword()),
                response.getVerdict(), response);
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
        aiAnalysisHistoryService.saveHistory(AiAnalysisType.TOP_TRENDS, response.getAnalyzedKeywords(),
                response.getOverallVerdict(), response);
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
}
