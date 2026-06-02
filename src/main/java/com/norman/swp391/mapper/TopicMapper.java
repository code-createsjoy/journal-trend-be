package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.topic.TopicResponse;
import com.norman.swp391.dto.response.topic.TopicTrendResponse;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.TopicTrend;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Mapper TopicMapper.
 */
@UtilityClass
public class TopicMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static TopicResponse toResponse(Topic topic) {
        if (topic == null) {
            return null;
        }
        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponseList.
 */
    public static List<TopicResponse> toResponseList(List<Topic> topics) {
        return topics.stream().map(TopicMapper::toResponse).toList();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toTrendResponse.
 */
    public static TopicTrendResponse toTrendResponse(TopicTrend trend) {
        if (trend == null) {
            return null;
        }
        Topic topic = trend.getTopic();
        return TopicTrendResponse.builder()
                .id(trend.getId())
                .topicId(topic != null ? topic.getId() : null)
                .topicName(topic != null ? topic.getName() : null)
                .year(trend.getYear())
                .month(trend.getMonth())
                .paperCount(trend.getPaperCount())
                .trendScore(trend.getTrendScore())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toTrendResponseList.
 */
    public static List<TopicTrendResponse> toTrendResponseList(List<TopicTrend> trends) {
        return trends.stream().map(TopicMapper::toTrendResponse).toList();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toTrendingResponse.
 */
    public static TrendingTopicResponse toTrendingResponse(Topic topic, TopicTrend trend, int rank) {
        if (topic == null) {
            return null;
        }
        return TrendingTopicResponse.builder()
                .topicId(topic.getId())
                .topicName(topic.getName())
                .description(topic.getDescription())
                .paperCount(trend != null ? trend.getPaperCount() : 0)
                .trendScore(trend != null ? trend.getTrendScore() : null)
                .rank(rank)
                .build();
    }
}


