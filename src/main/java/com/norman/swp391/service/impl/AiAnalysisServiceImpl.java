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
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.exception.BadRequestException;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final KeywordService keywordService;
    private final KeywordTrendService keywordTrendService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    @Qualifier("geminiRestClient")
    private final RestClient geminiRestClient;

    @Override
    public AiTrendAnalysisResponse analyzeTrend(AiTrendAnalysisRequest request) {
        String apiKey = appProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        KeywordResponse keyword = keywordService.getById(request.getKeywordId());
        List<KeywordTrendResponse> trendData =
                keywordService.getKeywordTrendChart(request.getKeywordId(), request.getMonths());

        if (trendData.isEmpty()) {
            throw new BadRequestException("No trend data available for keyword: " + keyword.getTerm());
        }

        String prompt = buildPrompt(keyword.getTerm(), trendData, request.getChartImageBase64() != null);
        String geminiResponseText = callGemini(prompt, request.getChartImageBase64(), request.getChartImageMimeType());

        return parseGeminiResponse(geminiResponseText, keyword.getTerm());
    }

    private String buildPrompt(String term, List<KeywordTrendResponse> data, boolean hasImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia phân tích xu hướng nghiên cứu học thuật. ");
        sb.append("Hãy phân tích dữ liệu xu hướng bài báo khoa học dưới đây và đưa ra nhận định chuyên sâu.\n\n");
        sb.append("Từ khóa nghiên cứu: **").append(term).append("**\n\n");
        sb.append("Dữ liệu số lượng bài báo theo tháng (").append(data.size()).append(" tháng gần nhất):\n");

        for (KeywordTrendResponse d : data) {
            sb.append(String.format("  - %d/%d: %d bài báo", d.getMonth(), d.getYear(), d.getPaperCount()));
            if (d.getDeltaPercent() != null) {
                sb.append(String.format(" (thay đổi: %+.1f%%)", d.getDeltaPercent().doubleValue()));
            }
            sb.append("\n");
        }

        if (hasImage) {
            sb.append("\nBiểu đồ xu hướng cũng được đính kèm để tham khảo trực quan.\n");
        }

        sb.append("""

                Hãy phân tích và trả lời CHÍNH XÁC theo định dạng JSON sau (không thêm markdown, không thêm text ngoài JSON):
                {
                  "verdict": "GROWING hoặc STABLE hoặc DECLINING",
                  "feasibilityScore": <số nguyên 0-100>,
                  "analysis": "<phân tích tổng quan chi tiết bằng tiếng Việt>",
                  "keyInsights": ["<điểm nhận xét 1>", "<điểm nhận xét 2>", "<điểm nhận xét 3>"],
                  "recommendation": "<khuyến nghị cho nhà nghiên cứu bằng tiếng Việt>"
                }

                Giải thích feasibilityScore:
                - 80-100: Lĩnh vực đang bùng nổ, rất khả thi để nghiên cứu
                - 60-79: Lĩnh vực phát triển tốt, khả thi
                - 40-59: Ổn định, cần cân nhắc kỹ
                - 20-39: Xu hướng đi xuống, ít khả thi
                - 0-19: Lĩnh vực đang suy giảm mạnh
                """);

        return sb.toString();
    }

    private String callGemini(String prompt, String imageBase64, String imageMimeType) {
        AppProperties.Gemini cfg = appProperties.getGemini();
        String url = cfg.getBaseUrl() + "/models/" + cfg.getModel() + ":generateContent?key=" + cfg.getApiKey();

        List<Map<String, Object>> parts = new ArrayList<>();
        if (imageBase64 != null && !imageBase64.isBlank()) {
            parts.add(Map.of("inlineData", Map.of("mimeType", imageMimeType, "data", imageBase64)));
        }
        parts.add(Map.of("text", prompt));

        Map<String, Object> content = Map.of("parts", parts);
        Map<String, Object> genConfig = Map.of(
                "temperature", 0.4,
                "maxOutputTokens", cfg.getMaxOutputTokens(),
                "responseMimeType", "application/json");
        Map<String, Object> body = Map.of("contents", List.of(content), "generationConfig", genConfig);

        try {
            String raw = geminiRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiTopTrendsAnalysisResponse analyzeTopTrends(AiTopTrendsAnalysisRequest request) {
        String apiKey = appProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
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

        String prompt = buildTopTrendsPrompt(combinedData, request.getChartImageBase64() != null);
        String geminiResponseText = callGemini(prompt, request.getChartImageBase64(), request.getChartImageMimeType());
        return parseTopTrendsGeminiResponse(geminiResponseText, new ArrayList<>(combinedData.keySet()));
    }

    private String buildTopTrendsPrompt(Map<String, List<KeywordTrendResponse>> combinedData, boolean hasImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia phân tích dữ liệu nghiên cứu khoa học hàng đầu. ");
        sb.append("Dưới đây là dữ liệu xu hướng công bố bài báo của nhóm các từ khóa công nghệ đang được quan tâm nhất.\n\n");
        sb.append("Hãy thực hiện phân tích đối sánh chuyên sâu giữa các xu hướng này.\n\n");
        sb.append("DỮ LIỆU SỐ LƯỢNG BÀI BÁO THEO THÁNG:\n");

        combinedData.forEach((term, trends) -> {
            sb.append(String.format("\n* Từ khóa: **%s**\n", term));
            for (KeywordTrendResponse d : trends) {
                sb.append(String.format("  - %d/%d: %d bài báo", d.getMonth(), d.getYear(), d.getPaperCount()));
                if (d.getDeltaPercent() != null) {
                    sb.append(String.format(" (tỷ lệ tăng: %+.1f%%)", d.getDeltaPercent().doubleValue()));
                }
                sb.append("\n");
            }
        });

        if (hasImage) {
            sb.append("\nHình ảnh đính kèm là biểu đồ đường (line chart) hiển thị lịch sử của các từ khóa này để tham khảo trực quan.\n");
        }

        sb.append("""

                Hãy phân tích so sánh và trả lời CHÍNH XÁC theo định dạng JSON sau (không thêm markdown, không thêm text ngoài JSON):
                {
                  "overallVerdict": "GROWING hoặc STABLE hoặc MIXED",
                  "topGrowingKeywords": ["<Từ khóa 1 tăng mạnh nhất>", "<Từ khóa 2 tăng mạnh nhất>"],
                  "analysis": "<Phân tích so sánh chi tiết các đường xu hướng, nêu rõ từ khóa nào đang tăng tốc, từ khóa nào đang bão hòa bằng tiếng Việt>",
                  "keyInsights": [
                    "<Nhận xét quan trọng 1>",
                    "<Nhận xét quan trọng 2>",
                    "<Nhận xét quan trọng 3>"
                  ],
                  "recommendation": "<Khuyên nhà nghiên cứu nên lựa chọn từ khóa nào để bắt đầu nghiên cứu mới có tỷ lệ đón đầu xu hướng cao nhất bằng tiếng Việt>"
                }
                """);

        return sb.toString();
    }

    private AiTopTrendsAnalysisResponse parseTopTrendsGeminiResponse(String json, List<String> analyzedKeywords) {
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
            log.error("Failed to parse Gemini top-trends response: {}", json);
            return AiTopTrendsAnalysisResponse.builder()
                    .overallVerdict("MIXED")
                    .analyzedKeywords(analyzedKeywords)
                    .topGrowingKeywords(List.of())
                    .analysis(json)
                    .keyInsights(List.of())
                    .recommendation("Không thể giải mã dữ liệu AI, vui lòng tham khảo nội dung phân tích thô.")
                    .build();
        }
    }

    private AiTrendAnalysisResponse parseGeminiResponse(String json, String keyword) {
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
            log.error("Failed to parse Gemini response: {}", json);
            return AiTrendAnalysisResponse.builder()
                    .keyword(keyword)
                    .verdict("STABLE")
                    .feasibilityScore(50)
                    .analysis(json)
                    .keyInsights(List.of())
                    .recommendation("Không thể phân tích tự động, vui lòng xem phần analysis.")
                    .build();
        }
    }
}
