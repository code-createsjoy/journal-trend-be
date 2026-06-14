package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse;
import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse.KeywordMonthSample;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.repository.PublicationTrendRepository;
import com.norman.swp391.repository.KeywordRepository;
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
    private final PaperKeywordRepository paperKeywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final KeywordRepository keywordRepository;

    @Override
    @Transactional(readOnly = true)
    public TrendDemoStatsResponse getStats() {
        int minPapers = appProperties.getSync().getMinKeywordPapers();
        int threshold = appProperties.getSync().getTrendingThresholdPercent();
        int consecutive = appProperties.getSync().getTrendingConsecutiveMonths();
        YearMonth now = YearMonth.now();

        long activePapers = paperRepository.countByStatus(PaperStatus.ACTIVE);
        long papersWithKeywords = paperRepository.countActiveWithAtLeastOneKeyword();
        long totalKeywords = paperKeywordRepository.countDistinctKeywords();
        long keywordsWithMin = paperKeywordRepository
                .findKeywordIdsWithAtLeastPapers(minPapers, PaperStatus.ACTIVE, PaperReviewStatus.NONE)
                .size();

        long trendRowsMonth = publicationTrendRepository.countByYearAndMonth(now.getYear(), now.getMonthValue());
        BigDecimal thr = BigDecimal.valueOf(threshold);
        long scoreGe15 = publicationTrendRepository.countByYearAndMonthAndTrendScoreGreaterThanEqual(
                now.getYear(), now.getMonthValue(), thr);

        List<KeywordMonthSample> samples = loadTopKeywordsByPaperCount(10, now);

        long officialCount = countOfficialTrendingKeywords(minPapers, threshold, consecutive, now);
        long monthlyTopCount = publicationTrendRepository
                .findTopByYearMonth(now.getYear(), now.getMonthValue(), PageRequest.of(0, 10))
                .size();

        return TrendDemoStatsResponse.builder()
                .activePapers(activePapers)
                .papersWithKeywords(papersWithKeywords)
                .totalKeywords(totalKeywords)
                .keywordsWithMinPapers(keywordsWithMin)
                .keywordTrendRowsCurrentMonth(trendRowsMonth)
                .keywordTrendRowsWithScoreGe15(scoreGe15)
                .officialTrendingKeywords(officialCount)
                .topKeywordsCurrentMonth(monthlyTopCount)
                .minKeywordPapers(minPapers)
                .trendingThresholdPercent(threshold)
                .consecutiveMonthsRequired(consecutive)
                .trendBackfillMonths(appProperties.getSync().getTrendBackfillMonths())
                .formulaMom(
                        "% MoM(keyword, month T) = (papers published in T − month T−1) / month T−1 × 100")
                .formulaOfficialTrending(String.format(
                        "Official keyword trending (BR): ≥%d papers/keyword and %d consecutive months (through current month) each score ≥ %d%%",
                        minPapers, consecutive, threshold))
                .topKeywordsByPaperCount(samples)
                .build();
    }

    private List<KeywordMonthSample> loadTopKeywordsByPaperCount(int limit, YearMonth now) {
        return paperKeywordRepository.findTopKeywordsByPaperCount(
                        PaperStatus.ACTIVE, PaperReviewStatus.NONE, PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    Long keywordId = (Long) row[0];
                    String term = (String) row[1];
                    long count = ((Number) row[2]).longValue();
                    var opt = publicationTrendRepository.findByKeywordIdAndYearAndMonth(
                            keywordId, now.getYear(), now.getMonthValue());
                    return KeywordMonthSample.builder()
                            .keywordId(keywordId)
                            .term(term)
                            .totalPapers(count)
                            .currentMonthTrendScore(
                                     opt.map(tr -> tr.getDeltaPercent().doubleValue()).orElse(0.0))
                            .build();
                })
                .toList();
    }

    private long countOfficialTrendingKeywords(
            int minPapers, int threshold, int consecutive, YearMonth end) {
        BigDecimal thr = BigDecimal.valueOf(threshold);
        List<Long> candidateIds = paperKeywordRepository.findKeywordIdsWithAtLeastPapers(
                minPapers, PaperStatus.ACTIVE, PaperReviewStatus.NONE);
        return candidateIds.stream()
                .filter(id -> isConsecutiveTrending(id, end, consecutive, thr))
                .count();
    }

    private boolean isConsecutiveTrending(
            Long keywordId, YearMonth endMonth, int monthsRequired, BigDecimal threshold) {
        YearMonth cursor = endMonth.minusMonths(monthsRequired - 1L);
        for (int i = 0; i < monthsRequired; i++) {
            var opt = publicationTrendRepository.findByKeywordIdAndYearAndMonth(
                    keywordId, cursor.getYear(), cursor.getMonthValue());
            if (opt.isEmpty() || opt.get().getDeltaPercent().compareTo(threshold) < 0) {
                return false;
            }
            cursor = cursor.plusMonths(1);
        }
        return true;
    }
}
