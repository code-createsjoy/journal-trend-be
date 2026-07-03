package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.dashboard.DashboardSummaryResponse;
import com.norman.swp391.dto.response.dashboard.DashboardSummaryResponse.*;
import com.norman.swp391.dto.response.dashboard.KeywordChartResponse;
import com.norman.swp391.dto.response.dashboard.KeywordChartResponse.KeywordChartPointDto;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperKeyword;
import com.norman.swp391.entity.PublicationTrend;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.repository.*;
import com.norman.swp391.service.DashboardService;
import com.norman.swp391.service.KeywordTrendService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final PaperRepository paperRepository;
    private final JournalRepository journalRepository;
    private final KeywordRepository keywordRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final SyncLogRepository syncLogRepository;
    private final KeywordTrendService keywordTrendService;

    @Override
    @Cacheable(value = "dashboardSummary", key = "#isAdmin")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(boolean isAdmin) {
        // 1. KPI Cards
        long totalPapers = paperRepository.countByStatus(PaperStatus.ACTIVE);
        long totalJournals = journalRepository.count();
        long totalKeywords = keywordRepository.count();

        List<Keyword> trendingKeywords = keywordTrendService.findTrendingKeywords(null, null);
        long trendingKeywordsCount = trendingKeywords.size();

        long trendingTopicsCount = trendingKeywords.stream()
                .map(Keyword::getDomain)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .count();

        List<SyncLog> recentLogs = syncLogRepository.findRecentWithAdmin(PageRequest.of(0, 1));
        SyncLog lastLog = recentLogs.isEmpty() ? null : recentLogs.get(0);
        String lastSyncStatus = lastLog != null ? lastLog.getStatus().name() : "N/A";
        LocalDateTime lastSyncTime = lastLog != null ? lastLog.getStartedAt() : null;

        KpiCardsDto kpi = KpiCardsDto.builder()
                .totalPapers(totalPapers)
                .totalJournals(totalJournals)
                .totalKeywords(totalKeywords)
                .trendingKeywordsCount(trendingKeywordsCount)
                .trendingTopicsCount(trendingTopicsCount)
                .lastSyncStatus(lastSyncStatus)
                .lastSyncTime(lastSyncTime)
                .build();

        // 2. Top 10 Trending Topics
        Map<String, List<Keyword>> keywordsByDomain = trendingKeywords.stream()
                .filter(k -> StringUtils.hasText(k.getDomain()))
                .collect(Collectors.groupingBy(k -> k.getDomain().trim()));

        List<TrendingTopicDto> trendingTopics = keywordsByDomain.entrySet().stream()
                .map(entry -> {
                    String domain = entry.getKey();
                    List<Keyword> kws = entry.getValue();

                    double avgScore = kws.stream()
                            .map(Keyword::getTrendScore)
                            .filter(Objects::nonNull)
                            .mapToDouble(BigDecimal::doubleValue)
                            .average()
                            .orElse(0.0);

                    List<TopicKeywordDto> topKws = kws.stream()
                            .sorted(Comparator.comparing(Keyword::getTrendScore, Comparator.nullsLast(Comparator.reverseOrder())))
                            .limit(5)
                            .map(k -> TopicKeywordDto.builder()
                                    .term(k.getTerm())
                                    .trendScore(k.getTrendScore())
                                    .build())
                            .toList();

                    return TrendingTopicDto.builder()
                            .topicId((long) Math.abs(domain.hashCode()))
                            .topicName(domain)
                            .trendingKeywordsCount(kws.size())
                            .averageTrendScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                            .topKeywords(topKws)
                            .build();
                })
                .sorted((a, b) -> {
                    int compareScore = b.getAverageTrendScore().compareTo(a.getAverageTrendScore());
                    if (compareScore != 0) return compareScore;
                    return Integer.compare(b.getTrendingKeywordsCount(), a.getTrendingKeywordsCount());
                })
                .limit(10)
                .toList();

        // 3. Top 10 Trending Keywords
        List<Keyword> sortedTrendingKeywords = trendingKeywords.stream()
                .sorted(Comparator.comparing(Keyword::getTrendScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TrendingKeywordDto> topTrendingKeywords = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sortedTrendingKeywords.size()); i++) {
            Keyword kw = sortedTrendingKeywords.get(i);
            topTrendingKeywords.add(TrendingKeywordDto.builder()
                    .keywordId(kw.getKeywordId())
                    .rank(i + 1)
                    .keyword(kw.getTerm())
                    .domain(kw.getDomain())
                    .trendScore(kw.getTrendScore())
                    .paperCount(kw.getPaperCount())
                    .build());
        }

        // 5. Recent Publications
        Page<Paper> recentPapersPage = paperRepository.findByStatus(
                PaperStatus.ACTIVE,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "publicationDate", "id"))
        );
        List<Paper> recentPapers = recentPapersPage.getContent();
        List<Long> paperIds = recentPapers.stream().map(Paper::getId).toList();

        List<PaperKeyword> paperKeywords = paperIds.isEmpty() ? List.of() : paperKeywordRepository.findByPaperIdInWithKeyword(paperIds);
        Map<Long, List<String>> keywordsByPaperId = paperKeywords.stream()
                .collect(Collectors.groupingBy(
                        pk -> pk.getPaper().getId(),
                        Collectors.mapping(pk -> pk.getKeyword().getTerm(), Collectors.toList())
                ));

        List<RecentPublicationDto> recentPublications = recentPapers.stream()
                .map(p -> RecentPublicationDto.builder()
                        .paperId(p.getId())
                        .title(p.getTitle())
                        .journal(p.getJournalRef() != null ? p.getJournalRef().getName() : p.getJournal())
                        .publicationDate(p.getPublicationDate())
                        .citationCount(p.getCitationCount())
                        .topKeywords(keywordsByPaperId.getOrDefault(p.getId(), List.of()))
                        .build())
                .toList();

        // 6. Top Journals
        List<Object[]> topJournalsData = journalRepository.findTopJournalsByPaperCount(PageRequest.of(0, 10));
        List<TopJournalDto> topJournals = topJournalsData.stream()
                .map(row -> TopJournalDto.builder()
                        .journalId(((Number) row[0]).longValue())
                        .journalName((String) row[1])
                        .impactFactor((BigDecimal) row[2])
                        .domain((String) row[3])
                        .totalPapers(((Number) row[4]).longValue())
                        .build())
                .toList();

        // 7. Admin Sync Monitoring
        SyncMonitorDto syncMonitor = null;
        if (isAdmin && lastLog != null) {
            long duration = 0;
            if (lastLog.getStartedAt() != null && lastLog.getFinishedAt() != null) {
                duration = Duration.between(lastLog.getStartedAt(), lastLog.getFinishedAt()).toSeconds();
            }
            syncMonitor = SyncMonitorDto.builder()
                    .lastSyncTime(lastLog.getStartedAt())
                    .syncStatus(lastLog.getStatus().name())
                    .papersSynced(lastLog.getPapersFetched())
                    .durationSeconds(duration)
                    .errorMessage(lastLog.getErrorMessage())
                    .build();
        }

        return DashboardSummaryResponse.builder()
                .kpi(kpi)
                .trendingTopics(trendingTopics)
                .trendingKeywords(topTrendingKeywords)
                .recentPublications(recentPublications)
                .topJournals(topJournals)
                .syncMonitor(syncMonitor)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public KeywordChartResponse getKeywordChartData(Long keywordId) {
        Keyword kw = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword not found: " + keywordId));

        List<PublicationTrend> trends = publicationTrendRepository.findByKeywordIdOrderByYearAscMonthAsc(keywordId);
        List<KeywordChartPointDto> history = trends.stream()
                .map(pt -> KeywordChartPointDto.builder()
                        .year(pt.getYear())
                        .month(pt.getMonth())
                        .paperCount(pt.getPaperCount())
                        .trendScore(pt.getDeltaPercent())
                        .build())
                .toList();

        return KeywordChartResponse.builder()
                .keywordId(kw.getKeywordId())
                .keyword(kw.getTerm())
                .history(history)
                .build();
    }
}
