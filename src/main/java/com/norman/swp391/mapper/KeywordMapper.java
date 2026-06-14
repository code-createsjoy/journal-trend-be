package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PublicationTrend;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KeywordMapper {

    public static KeywordResponse toResponse(Keyword keyword) {
        if (keyword == null) {
            return null;
        }
        return KeywordResponse.builder()
                .keywordId(keyword.getKeywordId())
                .term(keyword.getTerm())
                .domain(keyword.getDomain())
                .paperCount(keyword.getPaperCount())
                .trendScore(keyword.getTrendScore())
                .createdAt(keyword.getCreatedAt())
                .build();
    }

    public static List<KeywordResponse> toResponseList(List<Keyword> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream().map(KeywordMapper::toResponse).toList();
    }

    public static KeywordTrendResponse toTrendResponse(PublicationTrend trend) {
        if (trend == null) {
            return null;
        }
        Keyword keyword = trend.getKeyword();
        return KeywordTrendResponse.builder()
                .trendId(trend.getTrendId())
                .keywordId(keyword != null ? keyword.getKeywordId() : null)
                .term(keyword != null ? keyword.getTerm() : null)
                .year(trend.getYear())
                .month(trend.getMonth())
                .paperCount(trend.getPaperCount())
                .deltaPercent(trend.getDeltaPercent())
                .build();
    }

    public static List<KeywordTrendResponse> toTrendResponseList(List<PublicationTrend> trends) {
        if (trends == null) {
            return List.of();
        }
        return trends.stream().map(KeywordMapper::toTrendResponse).toList();
    }

    public static TrendingKeywordResponse toTrendingResponse(Keyword keyword, PublicationTrend trend, int rank) {
        if (keyword == null) {
            return null;
        }
        return TrendingKeywordResponse.builder()
                .keywordId(keyword.getKeywordId())
                .term(keyword.getTerm())
                .domain(keyword.getDomain())
                .paperCount(trend != null ? trend.getPaperCount() : keyword.getPaperCount())
                .trendScore(trend != null ? trend.getDeltaPercent() : keyword.getTrendScore())
                .rank(rank)
                .build();
    }
}
