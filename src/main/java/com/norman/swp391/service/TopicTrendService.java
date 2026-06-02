package com.norman.swp391.service;

import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.entity.Topic;
import java.util.List;

/**
 * Dịch vụ tính toán và truy vấn xu hướng chủ đề.
 */
public interface TopicTrendService {

/**
 * Xử lý nghiệp vụ: recalculateAll.
 */
    void recalculateAll();

    /**
     * Tính lại topic_trends cho các tháng lịch sử (theo ngày xuất bản bài) — cần cho rule 3 tháng liên tiếp và minh họa.
     */
    void backfillHistoricalMonths(int monthsBack);

/**
 * Tìm kiếm: findTrendingTopics.
 */
    List<Topic> findTrendingTopics();

    /**
     * Trả về DTO các chủ đề xu hướng.
     */
    List<TrendingTopicResponse> findTrendingTopicResponses();

    /**
     * Lấy top chủ đề theo % tăng trưởng trong tháng hiện tại (không áp rule 3 tháng).
     */
    List<TrendingTopicResponse> findTopByTrendScore(int limit);

    /**
     * % tăng trưởng trong tháng hiện tại của một topic (dùng cho paper/topic detail, KPI, biểu đồ).
     */
    TrendingTopicResponse getCurrentMonthTrend(Long topicId);
}
