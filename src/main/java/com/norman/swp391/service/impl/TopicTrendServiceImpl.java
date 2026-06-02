package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.entity.Topic;
import com.norman.swp391.entity.TopicTrend;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.TopicMapper;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperTopicRepository;
import com.norman.swp391.repository.TopicRepository;
import com.norman.swp391.repository.TopicTrendRepository;
import com.norman.swp391.service.TopicTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Impl TopicTrendServiceImpl.
 */
@Service
@RequiredArgsConstructor
public class TopicTrendServiceImpl implements TopicTrendService {

    private final TopicRepository topicRepository;
    private final TopicTrendRepository topicTrendRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final PaperRepository paperRepository;
    private final AppProperties appProperties;

    /**
     * Thực hiện recalculateAll.
     */
    @Override
    @Transactional
    public void recalculateAll() {
        recalculateMonth(YearMonth.now(), true);
    }

    @Override
    @Transactional
    public void backfillHistoricalMonths(int monthsBack) {
        int months = Math.max(0, Math.min(monthsBack, 36));
        if (months == 0) {
            return;
        }
        YearMonth cursor = YearMonth.now().minusMonths(months);
        YearMonth end = YearMonth.now();
        while (!cursor.isAfter(end)) {
            recalculateMonth(cursor, cursor.equals(end));
            cursor = cursor.plusMonths(1);
        }
    }

    /**
     * Tính trend cho một tháng: so sánh số bài xuất bản trong tháng đó vs tháng trước (MoM).
     * Tháng hiện tại có thể fallback YoY nếu cả hai tháng gần nhất đều 0 bài.
     */
    private void recalculateMonth(YearMonth target, boolean allowYearOverYearFallback) {
        int year = target.getYear();
        int month = target.getMonthValue();
        YearMonth previous = target.minusMonths(1);

        Map<Long, Integer> currentCounts = toCountMap(paperTopicRepository.countPapersByTopicForMonth(
                year, month, PaperStatus.ACTIVE, PaperReviewStatus.NONE));
        Map<Long, Integer> previousCounts = toCountMap(paperTopicRepository.countPapersByTopicForMonth(
                previous.getYear(), previous.getMonthValue(), PaperStatus.ACTIVE, PaperReviewStatus.NONE));

        Map<Long, Integer> latestYearCounts = Map.of();
        Map<Long, Integer> priorYearCounts = Map.of();
        if (allowYearOverYearFallback) {
            Integer maxPubYear = paperRepository.findMaxPublicationYear(PaperStatus.ACTIVE);
            if (maxPubYear != null && maxPubYear > 0) {
                latestYearCounts = toCountMap(paperTopicRepository.countPapersByTopicForYear(
                        maxPubYear, PaperStatus.ACTIVE, PaperReviewStatus.NONE));
                priorYearCounts = toCountMap(paperTopicRepository.countPapersByTopicForYear(
                        maxPubYear - 1, PaperStatus.ACTIVE, PaperReviewStatus.NONE));
            }
        }

        java.util.Set<Long> topicIds = new java.util.HashSet<>(currentCounts.keySet());
        topicIds.addAll(previousCounts.keySet());
        if (allowYearOverYearFallback) {
            topicIds.addAll(latestYearCounts.keySet());
            topicIds.addAll(priorYearCounts.keySet());
        }
        topicIds.addAll(paperTopicRepository.findTopicIdsWithAtLeastPapers(
                appProperties.getSync().getMinTopicPapers(), PaperStatus.ACTIVE, PaperReviewStatus.NONE));
        Map<Long, Integer> totalPaperCounts = toCountMap(paperTopicRepository.countAllPapersByTopic(
                PaperStatus.ACTIVE, PaperReviewStatus.NONE));

        for (Long topicId : topicIds) {
            Topic topic = topicRepository.getReferenceById(topicId);
            int currentCount = currentCounts.getOrDefault(topicId, 0);
            int prevCount = previousCounts.getOrDefault(topicId, 0);
            if (allowYearOverYearFallback && currentCount == 0 && prevCount == 0 && !latestYearCounts.isEmpty()) {
                currentCount = latestYearCounts.getOrDefault(topicId, 0);
                prevCount = priorYearCounts.getOrDefault(topicId, 0);
            }
            BigDecimal score = calculateTrendScore(currentCount, prevCount);
            int totalPapers = totalPaperCounts.getOrDefault(topicId, 0);

            TopicTrend trend = topicTrendRepository
                    .findByTopicIdAndYearAndMonth(topicId, year, month)
                    .orElse(TopicTrend.builder().topic(topic).year(year).month(month).build());
            trend.setPaperCount(totalPapers);
            trend.setTrendScore(score);
            applyAnomalyFlag(trend, score);
            topicTrendRepository.save(trend);
        }
    }

    /**
     * Thực hiện findTrendingTopics.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Topic> findTrendingTopics() {
        int consecutiveMonths = appProperties.getSync().getTrendingConsecutiveMonths();
        BigDecimal threshold = BigDecimal.valueOf(appProperties.getSync().getTrendingThresholdPercent());
        int minPapers = appProperties.getSync().getMinTopicPapers();

        List<Topic> trending = new ArrayList<>();
        YearMonth end = YearMonth.now();

        for (Topic topic : topicRepository.findAll()) {
            if (paperTopicRepository.countByTopicId(topic.getId(), PaperStatus.ACTIVE, PaperReviewStatus.NONE)
                    < minPapers) {
                continue;
            }
            if (isConsecutiveTrending(topic.getId(), end, consecutiveMonths, threshold, minPapers)) {
                trending.add(topic);
            }
        }
        return trending;
    }

    /**
     * Thực hiện findTrendingTopicResponses.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TrendingTopicResponse> findTrendingTopicResponses() {
        List<Topic> topics = findTrendingTopics();
        List<TrendingTopicResponse> responses = new ArrayList<>();
        YearMonth current = YearMonth.now();
        int rank = 1;
        for (Topic topic : topics) {
            Optional<TopicTrend> latest = topicTrendRepository.findByTopicIdAndYearAndMonth(
                    topic.getId(), current.getYear(), current.getMonthValue());
            responses.add(TopicMapper.toTrendingResponse(topic, latest.orElse(null), rank++));
        }
        return responses;
    }

    /**
     * Thực hiện findTopByTrendScore.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TrendingTopicResponse> findTopByTrendScore(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        YearMonth current = YearMonth.now();
        List<TopicTrend> trends = topicTrendRepository.findTopByYearMonth(
                current.getYear(), current.getMonthValue(), PageRequest.of(0, size));

        if (trends.isEmpty()) {
            List<TrendingTopicResponse> responses = new ArrayList<>();
            int rank = 1;
            List<Topic> byPapers = topicRepository.findAll().stream()
                    .sorted((a, b) -> Long.compare(
                            paperTopicRepository.countByTopicId(
                                    b.getId(), PaperStatus.ACTIVE, PaperReviewStatus.NONE),
                            paperTopicRepository.countByTopicId(
                                    a.getId(), PaperStatus.ACTIVE, PaperReviewStatus.NONE)))
                    .limit(size)
                    .toList();
            for (Topic topic : byPapers) {
                responses.add(TopicMapper.toTrendingResponse(topic, null, rank++));
            }
            return responses;
        }

        List<TrendingTopicResponse> responses = new ArrayList<>();
        int rank = 1;
        for (TopicTrend trend : trends) {
            Topic topic = trend.getTopic();
            if (topic != null) {
                responses.add(TopicMapper.toTrendingResponse(topic, trend, rank++));
            }
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public TrendingTopicResponse getCurrentMonthTrend(Long topicId) {
        Topic topic = topicRepository
                .findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));
        YearMonth current = YearMonth.now();
        Optional<TopicTrend> trend = topicTrendRepository.findByTopicIdAndYearAndMonth(
                topicId, current.getYear(), current.getMonthValue());
        if (trend.isPresent()) {
            return TopicMapper.toTrendingResponse(topic, trend.get(), 0);
        }
        int totalPapers = (int) paperTopicRepository.countByTopicId(topicId, PaperStatus.ACTIVE, PaperReviewStatus.NONE);
        return TrendingTopicResponse.builder()
                .topicId(topic.getId())
                .topicName(topic.getName())
                .description(topic.getDescription())
                .paperCount(totalPapers)
                .trendScore(BigDecimal.ZERO)
                .rank(0)
                .build();
    }

    /**
     * Thực hiện isConsecutiveTrending.
     */
    private boolean isConsecutiveTrending(
            Long topicId, YearMonth endMonth, int monthsRequired, BigDecimal threshold, int minPapers) {
        YearMonth cursor = endMonth.minusMonths(monthsRequired - 1L);
        for (int i = 0; i < monthsRequired; i++) {
            Optional<TopicTrend> trend = topicTrendRepository.findByTopicIdAndYearAndMonth(
                    topicId, cursor.getYear(), cursor.getMonthValue());
            if (trend.isEmpty()
                    || trend.get().getTrendScore().compareTo(threshold) < 0
                    || trend.get().getPaperCount() < minPapers) {
                return false;
            }
            cursor = cursor.plusMonths(1);
        }
        return true;
    }

    /**
     * Chuyển kết quả query (topicId, count) thành map.
     */
    private Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            Long topicId = (Long) row[0];
            Long count = (Long) row[1];
            map.put(topicId, count.intValue());
        }
        return map;
    }

    /**
     * Tính % thay đổi số bài giữa hai kỳ (làm tròn 2 chữ số).
     */
    private void applyAnomalyFlag(TopicTrend trend, BigDecimal score) {
        int threshold = appProperties.getSync().getAnomalyThresholdPercent();
        boolean anomaly = score != null && score.doubleValue() >= threshold;
        trend.setAnomalyFlag(anomaly);
        if (anomaly) {
            trend.setAnomalyDetectedAt(LocalDateTime.now());
        } else {
            trend.setAnomalyDetectedAt(null);
        }
    }

    private BigDecimal calculateTrendScore(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        double percent = ((double) (current - previous) / previous) * 100.0;
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP);
    }
}


