package com.norman.swp391.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.dto.response.ai.AiAnalysisHistoryDetailResponse;
import com.norman.swp391.dto.response.ai.AiAnalysisHistorySummaryResponse;
import com.norman.swp391.entity.AiAnalysisHistory;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapper AiAnalysisHistoryMapper.
 */
@Slf4j
@UtilityClass
public class AiAnalysisHistoryMapper {

    public static AiAnalysisHistorySummaryResponse toSummary(AiAnalysisHistory history, ObjectMapper objectMapper) {
        if (history == null) {
            return null;
        }
        return AiAnalysisHistorySummaryResponse.builder()
                .id(history.getId())
                .analysisType(history.getAnalysisType())
                .targetKeywords(parseKeywords(history.getTargetKeywords(), objectMapper))
                .overallVerdict(history.getOverallVerdict())
                .createdAt(history.getCreatedAt())
                .build();
    }

    public static List<AiAnalysisHistorySummaryResponse> toSummaryList(
            List<AiAnalysisHistory> histories, ObjectMapper objectMapper) {
        return histories.stream().map(h -> toSummary(h, objectMapper)).toList();
    }

    public static AiAnalysisHistoryDetailResponse toDetail(AiAnalysisHistory history, ObjectMapper objectMapper) {
        if (history == null) {
            return null;
        }
        Object result = null;
        try {
            result = objectMapper.readValue(history.getResponseJson(), Object.class);
        } catch (Exception ex) {
            log.warn("[AI_HISTORY] Failed to parse stored response_json for history id={}: {}",
                    history.getId(), ex.getMessage());
        }
        return AiAnalysisHistoryDetailResponse.builder()
                .id(history.getId())
                .analysisType(history.getAnalysisType())
                .targetKeywords(parseKeywords(history.getTargetKeywords(), objectMapper))
                .overallVerdict(history.getOverallVerdict())
                .createdAt(history.getCreatedAt())
                .result(result)
                .build();
    }

    private static List<String> parseKeywords(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("[AI_HISTORY] Failed to parse stored target_keywords: {}", ex.getMessage());
            return List.of();
        }
    }
}
