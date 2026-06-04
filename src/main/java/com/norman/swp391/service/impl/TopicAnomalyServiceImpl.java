package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.admin.TopicAnomalyResponse;
import com.norman.swp391.entity.TopicTrend;
import com.norman.swp391.repository.TopicTrendRepository;
import com.norman.swp391.service.TopicAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicAnomalyServiceImpl implements TopicAnomalyService {

    private final TopicTrendRepository topicTrendRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TopicAnomalyResponse> listCurrentAnomalies(int limit) {
        LocalDate now = LocalDate.now();
        int size = Math.max(1, Math.min(limit, 50));
        return topicTrendRepository
                .findByYearAndMonthAndAnomalyFlagTrueOrderByTrendScoreDesc(
                        now.getYear(), now.getMonthValue(), PageRequest.of(0, size))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TopicAnomalyResponse toResponse(TopicTrend trend) {
        return TopicAnomalyResponse.builder()
                .topicId(trend.getTopic().getId())
                .topicName(trend.getTopic().getName())
                .trendScore(trend.getTrendScore())
                .paperCount(trend.getPaperCount())
                .year(trend.getYear())
                .month(trend.getMonth())
                .detectedAt(trend.getAnomalyDetectedAt())
                .build();
    }
}
