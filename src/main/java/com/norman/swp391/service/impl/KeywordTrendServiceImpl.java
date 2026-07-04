package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.dto.response.keyword.TrendingTopicResponse;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PublicationTrend;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.KeywordMapper;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.PaperKeywordRepository;
import com.norman.swp391.repository.PublicationTrendRepository;
import com.norman.swp391.service.KeywordTrendService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KeywordTrendServiceImpl implements KeywordTrendService {

    private final KeywordRepository keywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void recalculateAll() {
        recalculateMonth(YearMonth.now().minusMonths(1));
        recalculateMonth(YearMonth.now());
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
            recalculateMonth(cursor);
            cursor = cursor.plusMonths(1);
        }
    }

    private void recalculateMonth(YearMonth target) {
        int year = target.getYear();
        int month = target.getMonthValue();
        YearMonth previous = target.minusMonths(1);

        Map<Long, Integer> currentCounts = toCountMap(paperKeywordRepository.countPapersByKeywordForMonth(
                year, month, PaperStatus.ACTIVE, PaperReviewStatus.NONE));
        Map<Long, Integer> previousCounts = toCountMap(paperKeywordRepository.countPapersByKeywordForMonth(
                previous.getYear(), previous.getMonthValue(), PaperStatus.ACTIVE, PaperReviewStatus.NONE));

        List<Keyword> allKeywords = keywordRepository.findAll();
        Map<Long, Keyword> keywordMap = allKeywords.stream()
                .collect(Collectors.toMap(Keyword::getKeywordId, k -> k));

        Set<Long> keywordIds = new HashSet<>(keywordMap.keySet());

        Map<Long, Integer> totalPaperCounts = toCountMap(paperKeywordRepository.countAllPapersByKeyword(
                PaperStatus.ACTIVE, PaperReviewStatus.NONE));

        List<PublicationTrend> existingTrendsList = publicationTrendRepository.findByYearAndMonth(year, month);
        Map<Long, PublicationTrend> existingTrendsMap = existingTrendsList.stream()
                .collect(Collectors.toMap(
                        t -> t.getKeyword().getKeywordId(),
                        t -> t,
                        (existing, replacement) -> existing
                ));

        List<PublicationTrend> trendsToSave = new ArrayList<>();
        List<Keyword> keywordsToSave = new ArrayList<>();

        for (Long keywordId : keywordIds) {
            Keyword keyword = keywordMap.get(keywordId);
            if (keyword == null) {
                continue;
            }
            int currentCount = currentCounts.getOrDefault(keywordId, 0);
            int prevCount = previousCounts.getOrDefault(keywordId, 0);
            BigDecimal score = calculateTrendScore(currentCount, prevCount);
            int totalPapers = totalPaperCounts.getOrDefault(keywordId, 0);

            PublicationTrend trend = existingTrendsMap.get(keywordId);
            if (trend == null) {
                trend = PublicationTrend.builder()
                        .keyword(keyword)
                        .year(year)
                        .month(month)
                        .build();
            }
            trend.setPaperCount(currentCount);
            trend.setDeltaPercent(score);
            trend.setCreatedAt(LocalDateTime.now());
            trendsToSave.add(trend);

            if (target.equals(YearMonth.now().minusMonths(1))) {
                keyword.setTrendScore(score);
                keyword.setPaperCount(totalPapers);
                keywordsToSave.add(keyword);
            }
        }

        if (!trendsToSave.isEmpty()) {
            publicationTrendRepository.saveAll(trendsToSave);
        }
        if (!keywordsToSave.isEmpty()) {
            keywordRepository.saveAll(keywordsToSave);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Keyword> findTrendingKeywords(Integer year, Integer month) {
        int consecutiveMonths = appProperties.getSync().getTrendingConsecutiveMonths();
        BigDecimal threshold = BigDecimal.valueOf(appProperties.getSync().getTrendingThresholdPercent());

        YearMonth end = resolveTargetMonth(year, month);
        List<YearMonth> targetMonths = new ArrayList<>();
        YearMonth cursor = end.minusMonths(consecutiveMonths - 1L);
        for (int i = 0; i < consecutiveMonths; i++) {
            targetMonths.add(cursor);
            cursor = cursor.plusMonths(1);
        }

        Map<YearMonth, Map<Long, PublicationTrend>> trendCache = new HashMap<>();
        for (YearMonth ym : targetMonths) {
            List<PublicationTrend> trends = publicationTrendRepository.findByYearAndMonth(ym.getYear(), ym.getMonthValue());
            Map<Long, PublicationTrend> keywordTrendMap = trends.stream()
                    .collect(Collectors.toMap(
                            t -> t.getKeyword().getKeywordId(),
                            t -> t,
                            (existing, replacement) -> existing
                    ));
            trendCache.put(ym, keywordTrendMap);
        }

        List<Keyword> trending = new ArrayList<>();
        List<Keyword> allKeywords = keywordRepository.findAll();

        for (Keyword keyword : allKeywords) {
            boolean isTrending = true;
            for (YearMonth ym : targetMonths) {
                Map<Long, PublicationTrend> keywordTrendMap = trendCache.get(ym);
                PublicationTrend trend = keywordTrendMap != null ? keywordTrendMap.get(keyword.getKeywordId()) : null;
                if (trend == null || trend.getDeltaPercent() == null || trend.getDeltaPercent().compareTo(threshold) < 0) {
                    isTrending = false;
                    break;
                }
            }
            if (isTrending) {
                trending.add(keyword);
            }
        }
        return trending;
    }

    /**
     * Top keyword theo trend score CỦA RIÊNG tháng được chọn — không áp dụng luật
     * "trending" 3-tháng-liên-tiếp (BR-04). Dùng cho tính năng so sánh theo tháng trên UI,
     * nên một tháng vẫn có xếp hạng dù các tháng trước đó không đủ dữ liệu để tính chuỗi trending.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TrendingKeywordResponse> findTrendingKeywordResponses(Integer year, Integer month) {
        YearMonth target = resolveTargetMonth(year, month);
        List<PublicationTrend> trends = publicationTrendRepository.findByYearAndMonth(
                target.getYear(), target.getMonthValue());

        List<PublicationTrend> sorted = trends.stream()
                .filter(t -> t.getKeyword() != null)
                .sorted(Comparator.comparing(PublicationTrend::getDeltaPercent,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PublicationTrend::getPaperCount, Comparator.reverseOrder()))
                .toList();

        List<TrendingKeywordResponse> responses = new ArrayList<>();
        int rank = 1;
        for (PublicationTrend trend : sorted) {
            responses.add(KeywordMapper.toTrendingResponse(trend.getKeyword(), trend, rank++));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendingKeywordResponse> findTopByTrendScore(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        YearMonth current = YearMonth.now().minusMonths(1);
        List<PublicationTrend> trends = publicationTrendRepository.findTopByYearMonth(
                current.getYear(), current.getMonthValue(), PageRequest.of(0, size));

        List<TrendingKeywordResponse> responses = new ArrayList<>();
        int rank = 1;
        if (trends.isEmpty()) {
            List<Keyword> byPapers = keywordRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Keyword::getPaperCount).reversed())
                    .limit(size)
                    .toList();
            for (Keyword keyword : byPapers) {
                responses.add(KeywordMapper.toTrendingResponse(keyword, null, rank++));
            }
            return responses;
        }

        for (PublicationTrend trend : trends) {
            Keyword keyword = trend.getKeyword();
            if (keyword != null) {
                responses.add(KeywordMapper.toTrendingResponse(keyword, trend, rank++));
            }
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicationTrend getCurrentMonthTrend(Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword not found: " + keywordId));
        YearMonth current = YearMonth.now().minusMonths(1);
        return publicationTrendRepository.findByKeywordIdAndYearAndMonth(
                keywordId, current.getYear(), current.getMonthValue())
                .orElse(PublicationTrend.builder()
                        .keyword(keyword)
                        .year(current.getYear())
                        .month(current.getMonthValue())
                        .paperCount(keyword.getPaperCount())
                        .deltaPercent(keyword.getTrendScore())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendingTopicResponse> findTrendingTopics() {
        List<Keyword> trendingKeywords = findTrendingKeywords(null, null);
        
        // Group by Keyword.domain
        Map<String, List<Keyword>> grouped = trendingKeywords.stream()
                .filter(k -> StringUtils.hasText(k.getDomain()))
                .collect(Collectors.groupingBy(Keyword::getDomain));

        List<TrendingTopicResponse> responses = new ArrayList<>();
        int rank = 1;

        // Sort domains by count of trending keywords descending, then by name
        List<Map.Entry<String, List<Keyword>>> sorted = grouped.entrySet().stream()
                .sorted((a, b) -> {
                    int compareCount = Integer.compare(b.getValue().size(), a.getValue().size());
                    if (compareCount != 0) return compareCount;
                    return a.getKey().compareToIgnoreCase(b.getKey());
                })
                .toList();

        for (Map.Entry<String, List<Keyword>> entry : sorted) {
            String domain = entry.getKey();
            List<Keyword> kws = entry.getValue();
            
            // Average trend score of keywords in domain
            double avgTrend = kws.stream()
                    .map(Keyword::getTrendScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);

            responses.add(TrendingTopicResponse.builder()
                    .topicId((long) Math.abs(domain.hashCode())) // derived ID
                    .topicName(domain)
                    .description("Dynamically derived trending topic based on keywords in " + domain)
                    .paperCount(kws.size()) // "Show: domain, number of trending keywords"
                    .trendScore(BigDecimal.valueOf(avgTrend).setScale(2, RoundingMode.HALF_UP))
                    .rank(rank++)
                    .build());
        }
        return responses;
    }


    private Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            Long count = (Long) row[1];
            map.put(id, count.intValue());
        }
        return map;
    }

    private BigDecimal calculateTrendScore(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        double percent = ((double) (current - previous) / previous) * 100.0;
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Suy ra tháng mục tiêu từ tham số year/month của client.
     * - Không truyền gì: mặc định tháng trước (hành vi cũ).
     * - Truyền thiếu 1 trong 2: báo lỗi rõ ràng thay vì âm thầm bỏ qua filter.
     * - Truyền cả 2 nhưng không hợp lệ (vd. month=13): báo lỗi 400 thay vì crash 500.
     */
    private YearMonth resolveTargetMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now().minusMonths(1);
        }
        if (year == null || month == null) {
            throw new BadRequestException("year and month must be provided together");
        }
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException ex) {
            throw new BadRequestException("Invalid year/month: " + ex.getMessage());
        }
    }
}
