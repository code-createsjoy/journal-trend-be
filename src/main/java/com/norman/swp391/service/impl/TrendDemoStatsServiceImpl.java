package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse.TopicMonthSample;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperTopicRepository;
import com.norman.swp391.repository.TopicTrendRepository;
import com.norman.swp391.service.TrendDemoStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrendDemoStatsServiceImpl implements TrendDemoStatsService {

    private final AppProperties appProperties;
    private final PaperRepository paperRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final TopicTrendRepository topicTrendRepository;

    @Override
    @Transactional(readOnly = true)
    public TrendDemoStatsResponse getStats() {
        int minPapers = appProperties.getSync().getMinTopicPapers();
        int threshold = appProperties.getSync().getTrendingThresholdPercent();
        int consecutive = appProperties.getSync().getTrendingConsecutiveMonths();
        YearMonth now = YearMonth.now();

        long activePapers = paperRepository.countByStatus(PaperStatus.ACTIVE);
        long papersWithTopics = paperRepository.countActiveWithAtLeastOneTopic();
        long totalTopics = paperTopicRepository.countDistinctTopics();
        long topicsWithMin = paperTopicRepository
                .findTopicIdsWithAtLeastPapers(minPapers, PaperStatus.ACTIVE, PaperReviewStatus.NONE)
                .size();

        long trendRowsMonth = topicTrendRepository.countByYearAndMonth(now.getYear(), now.getMonthValue());
        BigDecimal thr = BigDecimal.valueOf(threshold);
        long scoreGe15 = topicTrendRepository.countByYearAndMonthAndTrendScoreGreaterThanEqual(
                now.getYear(), now.getMonthValue(), thr);

        List<TopicMonthSample> samples = loadTopTopicsByPaperCount(10, now);

        long officialCount = countOfficialTrendingTopics(minPapers, threshold, consecutive, now);
        long monthlyTopCount = topicTrendRepository
                .findTopByYearMonth(now.getYear(), now.getMonthValue(), PageRequest.of(0, 10))
                .size();

        return TrendDemoStatsResponse.builder()
                .activePapers(activePapers)
                .papersWithTopics(papersWithTopics)
                .totalTopics(totalTopics)
                .topicsWithMinPapers(topicsWithMin)
                .topicTrendRowsCurrentMonth(trendRowsMonth)
                .topicTrendRowsWithScoreGe15(scoreGe15)
                .officialTrendingTopics(officialCount)
                .topMonthlyTopics(monthlyTopCount)
                .minTopicPapers(minPapers)
                .trendingThresholdPercent(threshold)
                .consecutiveMonthsRequired(consecutive)
                .trendBackfillMonths(appProperties.getSync().getTrendBackfillMonths())
                .formulaMom(
                        "% MoM(topic, month T) = (papers published in T − month T−1) / month T−1 × 100")
                .formulaOfficialTrending(String.format(
                        "Official topic trending (BR): ≥%d papers/topic and %d consecutive months (through current month) each score ≥ %d%%",
                        minPapers, consecutive, threshold))
                .topTopicsByPaperCount(samples)
                .build();
    }

    private List<TopicMonthSample> loadTopTopicsByPaperCount(int limit, YearMonth now) {
        return paperTopicRepository.findTopTopicsByPaperCount(
                        PaperStatus.ACTIVE, PaperReviewStatus.NONE, PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    Long topicId = (Long) row[0];
                    String name = (String) row[1];
                    long count = ((Number) row[2]).longValue();
                    var opt = topicTrendRepository.findByTopicIdAndYearAndMonth(
                            topicId, now.getYear(), now.getMonthValue());
                    return TopicMonthSample.builder()
                            .topicId(topicId)
                            .topicName(name)
                            .totalPapers(count)
                            .currentMonthTrendScore(
                                    opt.map(tr -> tr.getTrendScore().doubleValue()).orElse(0.0))
                            .build();
                })
                .toList();
    }

    private long countOfficialTrendingTopics(
            int minPapers, int threshold, int consecutive, YearMonth end) {
        BigDecimal thr = BigDecimal.valueOf(threshold);
        List<Long> candidateIds = paperTopicRepository.findTopicIdsWithAtLeastPapers(
                minPapers, PaperStatus.ACTIVE, PaperReviewStatus.NONE);
        return candidateIds.stream()
                .filter(id -> isConsecutiveTrending(id, end, consecutive, thr, minPapers))
                .count();
    }

    private boolean isConsecutiveTrending(
            Long topicId, YearMonth endMonth, int monthsRequired, BigDecimal threshold, int minPapers) {
        YearMonth cursor = endMonth.minusMonths(monthsRequired - 1L);
        for (int i = 0; i < monthsRequired; i++) {
            var opt = topicTrendRepository.findByTopicIdAndYearAndMonth(
                    topicId, cursor.getYear(), cursor.getMonthValue());
            if (opt.isEmpty()
                    || opt.get().getTrendScore().compareTo(threshold) < 0
                    || opt.get().getPaperCount() < minPapers) {
                return false;
            }
            cursor = cursor.plusMonths(1);
        }
        return true;
    }
}
