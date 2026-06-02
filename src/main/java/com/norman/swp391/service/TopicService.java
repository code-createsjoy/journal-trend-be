package com.norman.swp391.service;

import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.dto.response.topic.TopicTrendResponse;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import java.util.List;

/**
 * Dịch vụ chủ đề và biểu đồ xu hướng.
 */
public interface TopicService {

/**
 * Danh sách: listAll.
 */
    List<TopicResponse> listAll();

/**
 * Lấy dữ liệu: getById.
 */
    TopicResponse getById(Long id);

/**
 * Lấy dữ liệu: getTrendingTopics.
 */
    List<TrendingTopicResponse> getTrendingTopics();

/**
 * Lấy dữ liệu: getTopicTrendChart.
 */
    List<TopicTrendResponse> getTopicTrendChart(Long topicId, int months);
}
