package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.report.PersonalReportResponse;
import com.norman.swp391.dto.response.report.PersonalReportResponse.*;
import com.norman.swp391.entity.*;
import com.norman.swp391.entity.enums.PaperReviewStatus;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.*;
import com.norman.swp391.service.PersonalReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalReportServiceImpl implements PersonalReportService {

    private final FollowKeywordRepository followKeywordRepository;
    private final FollowAuthorRepository followAuthorRepository;
    private final FollowJournalRepository followJournalRepository;
    private final PaperRepository paperRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final KeywordRepository keywordRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final AuthorRepository authorRepository;
    private final JournalRepository journalRepository;

    /**
     * Tổng hợp báo cáo cá nhân hóa cho 1 user, gồm 3 phần: xu hướng (theo keyword/journal đang follow),
     * gợi ý đọc tiếp (paper mới/liên quan chưa đọc), và toàn cảnh lĩnh vực (tác giả dẫn đầu, keyword liên quan,
     * khoảng trống nghiên cứu). Nếu user chưa follow gì, tự động fallback dùng top keyword/author phổ biến.
     */
    @Override
    @Transactional(readOnly = true)
    public PersonalReportResponse generatePersonalReport(Long userId, String filterBy) {
        List<FollowKeyword> followKeywords = followKeywordRepository.findByUserId(userId);
        List<FollowAuthor> followAuthors = followAuthorRepository.findByUserId(userId);
        List<FollowJournal> followJournals = followJournalRepository.findByUserId(userId);

        // IDs thực tế user đang follow (không fallback) — dùng cho filter tabs
        List<Long> followedKeywordIds = new ArrayList<>();
        Set<String> domains = new HashSet<>();
        for (FollowKeyword fk : followKeywords) {
            followedKeywordIds.add(fk.getKeyword().getKeywordId());
            if (fk.getKeyword().getDomain() != null) {
                domains.add(fk.getKeyword().getDomain().toLowerCase());
            }
        }
        List<Long> followedAuthorIds = followAuthors.stream()
                .map(fa -> fa.getAuthor().getId())
                .collect(Collectors.toList());
        List<Long> followedJournalIds = followJournals.stream()
                .map(fj -> fj.getJournal().getId())
                .collect(Collectors.toList());

        // Dùng trực tiếp dữ liệu thực tế user đang follow — không fallback
        List<Long> keywordIds = followedKeywordIds;
        List<Long> authorIds = followedAuthorIds;

        Set<Long> bookmarkedPaperIds = new HashSet<>(collectionPaperRepository.findPaperIdsByUserId(userId));

        TrendsSection trends = buildTrendsSection(keywordIds, domains);
        List<RecommendedPaper> recommendations = buildRecommendationsSection(
                filterBy, keywordIds, authorIds,
                followedKeywordIds, followedAuthorIds, followedJournalIds,
                bookmarkedPaperIds);
        LandscapeSection landscape = buildLandscapeSection(keywordIds, domains, followKeywords);

        PersonalReportResponse.FollowStats followStats = PersonalReportResponse.FollowStats.builder()
                .keywordCount(followedKeywordIds.size())
                .authorCount(followedAuthorIds.size())
                .journalCount(followedJournalIds.size())
                .build();

        return PersonalReportResponse.builder()
                .trends(trends)
                .recommendations(recommendations)
                .landscape(landscape)
                .followStats(followStats)
                .build();
    }

    /** Dựng phần "Xu hướng": line chart số paper/tháng theo keyword follow, bar chart top journal theo domain. */
    private TrendsSection buildTrendsSection(List<Long> keywordIds, Set<String> domains) {
        // Chỉ lấy 3 tháng đã hoàn thành, không lấy tháng hiện tại
        YearMonth now = YearMonth.now();
        List<Integer> yearMonths = new ArrayList<>();
        for (int i = 3; i >= 1; i--) {
            YearMonth ym = now.minusMonths(i);
            yearMonths.add(ym.getYear() * 100 + ym.getMonthValue());
        }

        // Line Chart: Toàn bộ keyword user đang follow (tối đa 20), tự chọn hiển thị
        List<KeywordTrendPoint> lineChart = new ArrayList<>();
        List<Object[]> monthlyRows = paperKeywordRepository.countMonthlyPapersByKeywordIds(
                keywordIds, yearMonths);
        for (Object[] row : monthlyRows) {
            lineChart.add(KeywordTrendPoint.builder()
                    .term((String) row[0])
                    .year((Integer) row[1])
                    .month((Integer) row[2])
                    .paperCount((Long) row[3])
                    .build());
        }

        // Bar Chart: Top journals theo lĩnh vực
        List<JournalVolumePoint> barChart = new ArrayList<>();
        List<Object[]> journalRows = paperKeywordRepository.findTopJournalsByDomains(domains, PageRequest.of(0, 8));
        for (Object[] row : journalRows) {
            barChart.add(JournalVolumePoint.builder()
                    .journalName((String) row[0])
                    .paperCount((Long) row[1])
                    .build());
        }

        return TrendsSection.builder()
                .lineChart(lineChart)
                .barChart(barChart)
                .build();
    }

    /**
     * Dựng phần "Gợi ý đọc tiếp" (10-20 bài), gom theo thứ tự ưu tiên: bài mới từ author follow →
     * bài trùng nhiều keyword follow nhất → bài được trích dẫn nhiều trong các keyword đó →
     * nếu vẫn thiếu thì bù bằng bài phổ biến nhất hệ thống. Loại trừ paper đã bookmark.
     */
    private List<RecommendedPaper> buildRecommendationsSection(
            String filterBy,
            List<Long> keywordIds, List<Long> authorIds,
            List<Long> followedKeywordIds, List<Long> followedAuthorIds, List<Long> followedJournalIds,
            Set<Long> bookmarkedPaperIds) {

        List<RecommendedPaper> result = switch (filterBy.toUpperCase()) {
            case "KEYWORD" -> buildKeywordFilterRecommendations(followedKeywordIds, bookmarkedPaperIds);
            case "AUTHOR"  -> buildAuthorFilterRecommendations(followedAuthorIds, bookmarkedPaperIds);
            case "JOURNAL" -> buildJournalFilterRecommendations(followedJournalIds, bookmarkedPaperIds);
            default        -> buildAllRecommendations(keywordIds, authorIds, bookmarkedPaperIds);
        };

        enrichWithAuthors(result);
        return result;
    }

    private List<RecommendedPaper> buildKeywordFilterRecommendations(
            List<Long> followedKeywordIds, Set<Long> bookmarkedPaperIds) {
        if (followedKeywordIds.isEmpty()) return new ArrayList<>();

        // Sort: overlap count DESC → trendScore DESC — không filter trendScore, chỉ dùng để sắp xếp
        List<Object[]> rows = paperRepository.findByKeywordIdsWithOverlapSortedByTrend(
                followedKeywordIds, PageRequest.of(0, 25));
        List<RecommendedPaper> result = new ArrayList<>();
        for (Object[] row : rows) {
            Paper p = (Paper) row[0];
            Long matchCount = (Long) row[1];
            Number maxTrendVal = (Number) row[2];
            Double maxTrend = maxTrendVal != null ? maxTrendVal.doubleValue() : null;
            if (bookmarkedPaperIds.contains(p.getId())) continue;

            String reason;
            if (maxTrend != null && maxTrend >= 15.0) {
                reason = "Trùng khớp " + matchCount + " keyword đang trending (trendScore " + String.format("%.1f", maxTrend) + "%)";
            } else {
                reason = "Trùng khớp " + matchCount + " keyword bạn đang follow";
            }

            String matchType = (maxTrend != null && maxTrend >= 15.0) ? "TRENDING_KEYWORD" : "KEYWORD_OVERLAP";
            result.add(toRecommendedPaper(p, matchType, reason));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private List<RecommendedPaper> buildAuthorFilterRecommendations(
            List<Long> followedAuthorIds, Set<Long> bookmarkedPaperIds) {
        if (followedAuthorIds.isEmpty()) return new ArrayList<>();

        List<Paper> papers = paperRepository.findLatestByAuthorIds(followedAuthorIds, PageRequest.of(0, 60));
        List<Long> paperIds = papers.stream().map(Paper::getId).collect(Collectors.toList());

        // Load PaperAuthor để biết bài nào thuộc author nào (tránh N+1)
        List<PaperAuthor> paLinks = paperAuthorRepository.findByPaperIdInWithAuthor(paperIds);
        Map<Long, Set<Long>> paperToFollowedAuthors = new HashMap<>();
        for (PaperAuthor pa : paLinks) {
            Long authorId = pa.getAuthor().getId();
            if (followedAuthorIds.contains(authorId)) {
                paperToFollowedAuthors
                        .computeIfAbsent(pa.getPaper().getId(), k -> new HashSet<>())
                        .add(authorId);
            }
        }

        Map<Long, Integer> authorPaperCount = new HashMap<>();
        List<RecommendedPaper> result = new ArrayList<>();
        for (Paper p : papers) {
            if (bookmarkedPaperIds.contains(p.getId())) continue;
            Set<Long> authorsOfPaper = paperToFollowedAuthors.getOrDefault(p.getId(), Set.of());
            boolean canInclude = authorsOfPaper.stream()
                    .anyMatch(aId -> authorPaperCount.getOrDefault(aId, 0) < 3);
            if (!canInclude) continue;
            authorsOfPaper.forEach(aId -> authorPaperCount.merge(aId, 1, Integer::sum));
            result.add(toRecommendedPaper(p, "FOLLOWED_AUTHOR",
                    "Bài viết mới nhất từ tác giả bạn đang theo dõi"));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private List<RecommendedPaper> buildJournalFilterRecommendations(
            List<Long> followedJournalIds, Set<Long> bookmarkedPaperIds) {
        if (followedJournalIds.isEmpty()) return new ArrayList<>();

        List<Paper> papers = paperRepository.findByFollowedJournalIdsTrending(
                followedJournalIds, PageRequest.of(0, 20));
        List<RecommendedPaper> result = new ArrayList<>();
        for (Paper p : papers) {
            if (bookmarkedPaperIds.contains(p.getId())) continue;
            result.add(toRecommendedPaper(p, "TRENDING_JOURNAL",
                    "Bài viết trending trong journal bạn đang theo dõi"));
            if (result.size() >= 20) break;
        }
        return result;
    }

    private List<RecommendedPaper> buildAllRecommendations(
            List<Long> keywordIds, List<Long> authorIds, Set<Long> bookmarkedPaperIds) {

        // Trường hợp user chưa follow bất kỳ thứ gì → chỉ hiển thị RISING + POPULAR
        boolean hasNoFollow = keywordIds.isEmpty() && authorIds.isEmpty();
        if (hasNoFollow) {
            return buildNoFollowRecommendations(bookmarkedPaperIds);
        }

        Map<Long, RecommendedPaper> map = new LinkedHashMap<>();

        // FOLLOWED_AUTHOR
        if (!authorIds.isEmpty()) {
            List<Paper> latestPapers = paperRepository.findLatestByAuthorIds(authorIds, PageRequest.of(0, 15));
            for (Paper p : latestPapers) {
                if (bookmarkedPaperIds.contains(p.getId())) continue;
                map.put(p.getId(), toRecommendedPaper(p, "FOLLOWED_AUTHOR",
                        "Bài viết mới nhất từ tác giả bạn đang theo dõi"));
            }
        }

        // KEYWORD_OVERLAP → có thể nâng lên COMBINED_MATCH
        List<Object[]> overlapRows = paperRepository.findByKeywordOverlap(keywordIds, PageRequest.of(0, 15));
        for (Object[] row : overlapRows) {
            Paper p = (Paper) row[0];
            Long matchCount = (Long) row[1];
            if (bookmarkedPaperIds.contains(p.getId())) continue;
            if (map.containsKey(p.getId())) {
                RecommendedPaper existing = map.get(p.getId());
                existing.setMatchType("COMBINED_MATCH");
                existing.setRecommendationReason("Khớp tác giả follow & trùng khớp " + matchCount + " từ khóa quan tâm");
            } else {
                map.put(p.getId(), toRecommendedPaper(p, "KEYWORD_OVERLAP",
                        "Trùng khớp " + matchCount + " từ khóa nghiên cứu bạn đang follow"));
            }
        }

        // TOP_CITED
        List<Paper> citedPapers = paperRepository.findTopCitedByKeywordIds(keywordIds, PageRequest.of(0, 15));
        for (Paper p : citedPapers) {
            if (bookmarkedPaperIds.contains(p.getId()) || map.containsKey(p.getId())) continue;
            map.put(p.getId(), toRecommendedPaper(p, "TOP_CITED",
                    "Bài viết có tầm ảnh hưởng (trích dẫn cao) trong chủ đề quan tâm"));
        }

        // RISING_PAPERS: bài mới có keyword trending
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Paper> risingPapers = paperRepository.findRisingPapers(keywordIds, sixMonthsAgo, PageRequest.of(0, 8));
        for (Paper p : risingPapers) {
            if (bookmarkedPaperIds.contains(p.getId()) || map.containsKey(p.getId())) continue;
            map.put(p.getId(), toRecommendedPaper(p, "RISING",
                    "Nghiên cứu mới đang nổi bật trong chủ đề bạn quan tâm"));
        }

        // POPULAR fallback nếu chưa đủ 10
        if (map.size() < 10) {
            Pageable fallbackPageable = PageRequest.of(0, 15, Sort.by("citationCount").descending());
            List<Paper> popularFallback = paperRepository.findByStatus(PaperStatus.ACTIVE, fallbackPageable).getContent();
            for (Paper p : popularFallback) {
                if (bookmarkedPaperIds.contains(p.getId()) || map.containsKey(p.getId())) continue;
                map.put(p.getId(), toRecommendedPaper(p, "POPULAR",
                        "Nghiên cứu thịnh hành nổi bật trên hệ thống"));
                if (map.size() >= 10) break;
            }
        }

        // Diversity Filter: tối đa 3 bài từ cùng journal
        List<RecommendedPaper> filtered = applyDiversityFilter(new ArrayList<>(map.values()));
        return filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
    }

    private List<RecommendedPaper> buildNoFollowRecommendations(Set<Long> bookmarkedPaperIds) {
        Map<Long, RecommendedPaper> map = new LinkedHashMap<>();

        // RISING: bài mới ≤ 6 tháng có keyword trendScore ≥ 15%, sắp xếp trendScore cao → thấp
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Paper> risingPapers = paperRepository.findRisingPapersGlobal(sixMonthsAgo, PageRequest.of(0, 20));
        for (Paper p : risingPapers) {
            if (bookmarkedPaperIds.contains(p.getId())) continue;
            map.put(p.getId(), toRecommendedPaper(p, "RISING",
                    "Nghiên cứu mới đang nổi bật trên hệ thống"));
        }

        // POPULAR: bù đủ 20 nếu RISING chưa đủ
        if (map.size() < 20) {
            Pageable fallbackPageable = PageRequest.of(0, 30, Sort.by("citationCount").descending());
            List<Paper> popularFallback = paperRepository.findByStatus(PaperStatus.ACTIVE, fallbackPageable).getContent();
            for (Paper p : popularFallback) {
                if (bookmarkedPaperIds.contains(p.getId()) || map.containsKey(p.getId())) continue;
                map.put(p.getId(), toRecommendedPaper(p, "POPULAR",
                        "Nghiên cứu thịnh hành nổi bật trên hệ thống"));
                if (map.size() >= 20) break;
            }
        }

        List<RecommendedPaper> result = new ArrayList<>(map.values());
        return result.size() > 20 ? result.subList(0, 20) : result;
    }

    private List<RecommendedPaper> applyDiversityFilter(List<RecommendedPaper> papers) {
        Map<String, Integer> journalCount = new HashMap<>();
        List<RecommendedPaper> result = new ArrayList<>();
        for (RecommendedPaper rp : papers) {
            String journal = rp.getJournal() != null ? rp.getJournal() : "Unknown";
            int count = journalCount.getOrDefault(journal, 0);
            if (count >= 3) continue;
            journalCount.put(journal, count + 1);
            result.add(rp);
        }
        return result;
    }

    private RecommendedPaper toRecommendedPaper(Paper p, String matchType, String reason) {
        return RecommendedPaper.builder()
                .id(p.getId())
                .title(p.getTitle())
                .journal(p.getJournal() != null ? p.getJournal() : "Academic Journal")
                .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : 2026)
                .citations(p.getCitationCount())
                .doi(p.getDoi())
                .matchType(matchType)
                .recommendationReason(reason)
                .build();
    }

    private void enrichWithAuthors(List<RecommendedPaper> papers) {
        if (papers.isEmpty()) return;
        List<Long> paperIds = papers.stream().map(RecommendedPaper::getId).collect(Collectors.toList());
        List<PaperAuthor> paLinks = paperAuthorRepository.findByPaperIdInWithAuthor(paperIds);
        Map<Long, List<String>> authorMap = new HashMap<>();
        for (PaperAuthor pa : paLinks) {
            authorMap.computeIfAbsent(pa.getPaper().getId(), k -> new ArrayList<>())
                    .add(pa.getAuthor().getName());
        }
        for (RecommendedPaper rp : papers) {
            rp.setAuthors(authorMap.getOrDefault(rp.getId(), List.of("Unknown Author")));
        }
    }

    /**
     * Dựng phần "Toàn cảnh lĩnh vực": bubble chart tác giả dẫn đầu theo keyword follow,
     * word cloud keyword hay xuất hiện cùng nhau, và các keyword đang có ít nghiên cứu (research gap).
     */
    private LandscapeSection buildLandscapeSection(List<Long> keywordIds, Set<String> domains, List<FollowKeyword> followKeywords) {
        // Bubble Chart: Tác giả dẫn đầu
        List<AuthorInfluencePoint> bubbleChart = new ArrayList<>();
        List<Object[]> authorRows = paperAuthorRepository.findTopAuthorsByKeywordIds(keywordIds, PageRequest.of(0, 6));

        // Đếm DISTINCT followed keywords thực sự xuất hiện trong papers của từng author (1 query batch)
        List<Long> authorIds = authorRows.stream().map(row -> ((Author) row[0]).getId()).toList();
        Map<Long, Long> matchingKeywordCountByAuthor = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<Object[]> pairs = paperKeywordRepository.findAuthorFollowedKeywordPairs(authorIds, keywordIds);
            pairs.stream()
                    .collect(Collectors.groupingBy(
                            row -> (Long) row[0],
                            Collectors.mapping(row -> (Long) row[1], Collectors.toSet())))
                    .forEach((authorId, kwIds) -> matchingKeywordCountByAuthor.put(authorId, (long) kwIds.size()));
        }

        for (Object[] row : authorRows) {
            Author author = (Author) row[0];
            Long count = (Long) row[1];
            long matchCount = matchingKeywordCountByAuthor.getOrDefault(author.getId(), 1L);

            bubbleChart.add(AuthorInfluencePoint.builder()
                    .authorId(author.getId())
                    .authorName(author.getName())
                    .paperCount(count)
                    .mainDomain(author.getAffiliation() != null ? author.getAffiliation() : "Academic Institute")
                    .matchingKeywordCount((int) matchCount)
                    .hIndex(author.getHIndex())
                    .citationCount(author.getCitationCount())
                    .build());
        }

        // Word Cloud: Keyword co-occurrence
        List<KeywordCoOccurrencePoint> tagCloud = new ArrayList<>();
        List<Object[]> coOccurrenceRows = paperKeywordRepository.findKeywordCoOccurrence(keywordIds, PageRequest.of(0, 12));
        for (Object[] row : coOccurrenceRows) {
            Double avgTrend = (Double) row[2];
            tagCloud.add(KeywordCoOccurrencePoint.builder()
                    .term((String) row[0])
                    .coOccurrenceCount((Long) row[1])
                    .growthRate(avgTrend != null ? Math.round(avgTrend * 10.0) / 10.0 : 0.0)
                    .build());
        }

        // Khoảng trống nghiên cứu (cũ): Keyword ít bài nhất trong domain — giữ lại để tương thích
        List<ResearchGapPoint> researchGaps = new ArrayList<>();
        List<Keyword> gapKeywords = keywordRepository.findResearchGapsInDomains(domains, PageRequest.of(0, 5));
        for (Keyword k : gapKeywords) {
            researchGaps.add(ResearchGapPoint.builder()
                    .term(k.getTerm())
                    .paperCount((long) k.getPaperCount())
                    .build());
        }

        // followedDomains: nhóm keyword follow theo domain, mỗi nhóm có hotTopics + researchGaps riêng
        List<PersonalReportResponse.FollowedDomain> followedDomains = new ArrayList<>();
        if (!followKeywords.isEmpty()) {
            // Nhóm keyword follow theo domain
            Map<String, List<FollowKeyword>> byDomain = new LinkedHashMap<>();
            for (FollowKeyword fk : followKeywords) {
                String domain = fk.getKeyword().getDomain();
                if (domain == null || domain.isBlank()) continue;
                byDomain.computeIfAbsent(domain, d -> new ArrayList<>()).add(fk);
            }

            for (Map.Entry<String, List<FollowKeyword>> entry : byDomain.entrySet()) {
                String domain = entry.getKey();
                List<FollowKeyword> fks = entry.getValue();

                List<String> followedTerms = fks.stream()
                        .map(fk -> fk.getKeyword().getTerm())
                        .collect(Collectors.toList());
                List<Long> excludeIds = fks.stream()
                        .map(fk -> fk.getKeyword().getKeywordId())
                        .collect(Collectors.toList());

                List<Keyword> hotKws = keywordRepository.findHotTopicsByDomain(domain, excludeIds, PageRequest.of(0, 5));
                List<ResearchGapPoint> hotTopics = hotKws.stream()
                        .map(k -> ResearchGapPoint.builder()
                                .term(k.getTerm())
                                .paperCount((long) k.getPaperCount())
                                .build())
                        .collect(Collectors.toList());

                List<Keyword> gapKws = keywordRepository.findResearchGapsByDomain(domain, excludeIds, PageRequest.of(0, 5));
                List<ResearchGapPoint> gaps = gapKws.stream()
                        .map(k -> ResearchGapPoint.builder()
                                .term(k.getTerm())
                                .paperCount((long) k.getPaperCount())
                                .build())
                        .collect(Collectors.toList());

                followedDomains.add(PersonalReportResponse.FollowedDomain.builder()
                        .domain(domain)
                        .followedKeywords(followedTerms)
                        .hotTopics(hotTopics)
                        .researchGaps(gaps)
                        .build());
            }
        }

        return LandscapeSection.builder()
                .bubbleChart(bubbleChart)
                .tagCloud(tagCloud)
                .researchGaps(researchGaps)
                .followedDomains(followedDomains)
                .build();
    }
}
