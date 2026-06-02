package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.dto.response.topic.TopicTrendResponse;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.TopicTrend;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.TopicMapper;
import com.norman.swp391.repository.TopicRepository;
import com.norman.swp391.repository.TopicTrendRepository;
import com.norman.swp391.service.TopicService;
import com.norman.swp391.service.TopicTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Triển khai dịch vụ chủ đề.
 */
@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final TopicTrendRepository topicTrendRepository;
    private final TopicTrendService topicTrendService;

    @Override
    @Transactional(readOnly = true)
/**
 * Danh sách: listAll.
 */
    public List<TopicResponse> listAll() {
        return TopicMapper.toResponseList(topicRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getById.
 */
    public TopicResponse getById(Long id) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Topic", id));
        return TopicMapper.toResponse(topic);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getTrendingTopics.
 */
    public List<TrendingTopicResponse> getTrendingTopics() {
        return topicTrendService.findTrendingTopicResponses();
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Lấy dữ liệu: getTopicTrendChart.
 */
    public List<TopicTrendResponse> getTopicTrendChart(Long topicId, int months) {
        topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
        List<TopicTrend> trends = topicTrendRepository.findByTopicIdOrderByYearDescMonthDesc(topicId);
        int limit = Math.max(months, 1);
        List<TopicTrend> limited = trends.stream()
                .limit(limit)
                .sorted(Comparator.comparing(TopicTrend::getYear).thenComparing(TopicTrend::getMonth))
                .toList();
        return TopicMapper.toTrendResponseList(limited);
    }
}
