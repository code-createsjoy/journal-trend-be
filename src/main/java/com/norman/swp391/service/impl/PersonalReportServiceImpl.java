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

    @Override
    @Transactional(readOnly = true)
    public PersonalReportResponse generatePersonalReport(Long userId) {
        // 1. Lấy bộ lọc từ các mục người dùng đang follow
        List<FollowKeyword> followKeywords = followKeywordRepository.findByUserId(userId);
        List<FollowAuthor> followAuthors = followAuthorRepository.findByUserId(userId);
        List<FollowJournal> followJournals = followJournalRepository.findByUserId(userId);

        List<Long> keywordIds = new ArrayList<>();
        Set<String> domains = new HashSet<>();
        for (FollowKeyword fk : followKeywords) {
            keywordIds.add(fk.getKeyword().getKeywordId());
            if (fk.getKeyword().getDomain() != null) {
                domains.add(fk.getKeyword().getDomain().toLowerCase());
            }
        }

        List<Long> authorIds = followAuthors.stream()
                .map(fa -> fa.getAuthor().getId())
                .collect(Collectors.toList());

        // Cơ chế Fallback: Nếu người dùng chưa follow từ khóa nào, lấy Top 5 từ khóa phổ biến trong hệ thống
        if (keywordIds.isEmpty()) {
            List<Object[]> topKeywords = paperKeywordRepository.findTopKeywordsByPaperCount(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE, PageRequest.of(0, 5));
            for (Object[] row : topKeywords) {
                Long kwId = (Long) row[0];
                keywordIds.add(kwId);
                Keyword kw = keywordRepository.findById(kwId).orElse(null);
                if (kw != null && kw.getDomain() != null) {
                    domains.add(kw.getDomain().toLowerCase());
                }
            }
        }

        // Cơ chế Fallback cho Tác giả: Nếu chưa follow ai, lấy Top 5 tác giả nổi bật
        if (authorIds.isEmpty()) {
            authorIds = authorRepository.findFeatured(PageRequest.of(0, 5))
                    .getContent().stream()
                    .map(Author::getId)
                    .collect(Collectors.toList());
        }

        if (domains.isEmpty()) {
            domains.add("general");
        }

        // Đọc danh sách ID bài báo người dùng đã lưu để loại trừ khỏi gợi ý
        Set<Long> bookmarkedPaperIds = new HashSet<>(collectionPaperRepository.findPaperIdsByUserId(userId));

        // 2. Xây dựng NHIỆM VỤ 1: XU HƯỚNG
        TrendsSection trends = buildTrendsSection(keywordIds, domains);

        // 3. Xây dựng NHIỆM VỤ 2: GỢI Ý ĐỌC TIẾP (Tối thiểu 10 bài, tối đa 20 bài)
        List<RecommendedPaper> recommendations = buildRecommendationsSection(keywordIds, authorIds, bookmarkedPaperIds);

        // 4. Xây dựng NHIỆM VỤ 3: TOÀN CẢNH LĨNH VỰC
        LandscapeSection landscape = buildLandscapeSection(keywordIds, domains);

        return PersonalReportResponse.builder()
                .trends(trends)
                .recommendations(recommendations)
                .landscape(landscape)
                .build();
    }

    private TrendsSection buildTrendsSection(List<Long> keywordIds, Set<String> domains) {
        // Giới hạn top 10 keyword có nhiều paper nhất trong danh sách follow
        List<Long> top10KeywordIds = keywordIds;
        if (keywordIds.size() > 5) {
            List<Object[]> topRows = paperKeywordRepository.findTopKeywordsByPaperCount(
                    PaperStatus.ACTIVE, PaperReviewStatus.NONE,
                    org.springframework.data.domain.PageRequest.of(0, 5));
            Set<Long> topIdSet = topRows.stream()
                    .map(r -> (Long) r[0])
                    .filter(keywordIds::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // Bổ sung từ keywordIds gốc nếu chưa đủ 5
            for (Long id : keywordIds) {
                if (topIdSet.size() >= 5) break;
                topIdSet.add(id);
            }
            top10KeywordIds = new ArrayList<>(topIdSet);
        }

        // Tính 3 tháng đã hoàn thành + tháng hiện tại đang cập nhật
        YearMonth lastCompleted = YearMonth.now().minusMonths(1);
        List<Integer> yearMonths = new ArrayList<>();
        for (int i = 2; i >= 0; i--) {
            YearMonth ym = lastCompleted.minusMonths(i);
            yearMonths.add(ym.getYear() * 100 + ym.getMonthValue());
        }
        YearMonth current = YearMonth.now();
        yearMonths.add(current.getYear() * 100 + current.getMonthValue());

        // Line Chart: Xu hướng từ khóa theo tháng (3 tháng gần nhất, dữ liệu thật từ DB)
        List<KeywordTrendPoint> lineChart = new ArrayList<>();
        List<Object[]> monthlyRows = paperKeywordRepository.countMonthlyPapersByKeywordIds(
                top10KeywordIds, yearMonths);
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

    private List<RecommendedPaper> buildRecommendationsSection(
            List<Long> keywordIds, List<Long> authorIds, Set<Long> bookmarkedPaperIds) {

        Map<Long, RecommendedPaper> map = new LinkedHashMap<>();

        // Tiêu chí B: Bài viết mới nhất từ tác giả đang follow (Độ ưu tiên cao nhất)
        if (!authorIds.isEmpty()) {
            List<Paper> latestPapers = paperRepository.findLatestByAuthorIds(authorIds, PageRequest.of(0, 15));
            for (Paper p : latestPapers) {
                if (bookmarkedPaperIds.contains(p.getId())) continue;
                map.put(p.getId(), RecommendedPaper.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .journal(p.getJournal() != null ? p.getJournal() : "Academic Journal")
                        .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : 2026)
                        .citations(p.getCitationCount())
                        .doi(p.getDoi())
                        .matchType("FOLLOWED_AUTHOR")
                        .recommendationReason("Bài viết mới nhất từ tác giả bạn đang theo dõi")
                        .build());
            }
        }

        // Tiêu chí C: Trùng khớp từ khóa nhiều nhất
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
                map.put(p.getId(), RecommendedPaper.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .journal(p.getJournal() != null ? p.getJournal() : "Academic Journal")
                        .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : 2026)
                        .citations(p.getCitationCount())
                        .doi(p.getDoi())
                        .matchType("KEYWORD_OVERLAP")
                        .recommendationReason("Trùng khớp " + matchCount + " từ khóa nghiên cứu bạn đang follow")
                        .build());
            }
        }

        // Tiêu chí A: Trích dẫn nhiều nhất trong từ khóa follow mà chưa đọc
        List<Paper> citedPapers = paperRepository.findTopCitedByKeywordIds(keywordIds, PageRequest.of(0, 15));
        for (Paper p : citedPapers) {
            if (bookmarkedPaperIds.contains(p.getId())) continue;
            if (map.containsKey(p.getId())) continue; // Đã map ở các tiêu chí trên

            map.put(p.getId(), RecommendedPaper.builder()
                    .id(p.getId())
                    .title(p.getTitle())
                    .journal(p.getJournal() != null ? p.getJournal() : "Academic Journal")
                    .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : 2026)
                    .citations(p.getCitationCount())
                    .doi(p.getDoi())
                    .matchType("TOP_CITED")
                    .recommendationReason("Bài viết có tầm ảnh hưởng (trích dẫn cao) trong chủ đề quan tâm")
                    .build());
        }

        // Nếu số lượng gợi ý ít hơn 10 bài, tự động bù thêm bằng các bài báo nổi bật hệ thống
        if (map.size() < 10) {
            Pageable fallbackPageable = PageRequest.of(0, 15, Sort.by("citationCount").descending());
            List<Paper> popularFallback = paperRepository.findByStatus(PaperStatus.ACTIVE, fallbackPageable).getContent();
            for (Paper p : popularFallback) {
                if (bookmarkedPaperIds.contains(p.getId())) continue;
                if (map.containsKey(p.getId())) continue;

                map.put(p.getId(), RecommendedPaper.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .journal(p.getJournal() != null ? p.getJournal() : "Academic Journal")
                        .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : 2026)
                        .citations(p.getCitationCount())
                        .doi(p.getDoi())
                        .matchType("POPULAR")
                        .recommendationReason("Nghiên cứu thịnh hành nổi bật trên hệ thống")
                        .build());
                if (map.size() >= 10) break;
            }
        }

        // Giới hạn danh sách trong khoảng tối đa 20 bài báo
        List<RecommendedPaper> finalRecommendations = new ArrayList<>(map.values());
        if (finalRecommendations.size() > 20) {
            finalRecommendations = finalRecommendations.subList(0, 20);
        }

        // Đổ thông tin danh sách tác giả của từng bài báo (Tránh N+1 query)
        if (!finalRecommendations.isEmpty()) {
            List<Long> paperIds = finalRecommendations.stream().map(RecommendedPaper::getId).collect(Collectors.toList());
            List<PaperAuthor> paLinks = paperAuthorRepository.findByPaperIdInWithAuthor(paperIds);
            Map<Long, List<String>> authorMap = new HashMap<>();
            for (PaperAuthor pa : paLinks) {
                authorMap.computeIfAbsent(pa.getPaper().getId(), k -> new ArrayList<>())
                        .add(pa.getAuthor().getName());
            }
            for (RecommendedPaper rp : finalRecommendations) {
                rp.setAuthors(authorMap.getOrDefault(rp.getId(), List.of("Unknown Author")));
            }
        }

        return finalRecommendations;
    }

    private LandscapeSection buildLandscapeSection(List<Long> keywordIds, Set<String> domains) {
        // Bubble Chart: Tác giả dẫn đầu
        List<AuthorInfluencePoint> bubbleChart = new ArrayList<>();
        List<Object[]> authorRows = paperAuthorRepository.findTopAuthorsByKeywordIds(keywordIds, PageRequest.of(0, 6));
        for (Object[] row : authorRows) {
            Author author = (Author) row[0];
            Long count = (Long) row[1];
            
            // Tính số lượng từ khóa trùng khớp (matching keyword count) của tác giả với bộ lọc
            List<Object[]> topKws = paperKeywordRepository.findTopKeywordsByAuthor(author.getId(), PageRequest.of(0, 10));
            int overlapCount = 0;
            for (Object[] kwRow : topKws) {
                String term = (String) kwRow[0];
                boolean isOverlap = keywordRepository.findByTerm(term)
                        .map(k -> keywordIds.contains(k.getKeywordId()))
                        .orElse(false);
                if (isOverlap) {
                    overlapCount++;
                }
            }

            bubbleChart.add(AuthorInfluencePoint.builder()
                    .authorId(author.getId())
                    .authorName(author.getName())
                    .paperCount(count)
                    .mainDomain(author.getAffiliation() != null ? author.getAffiliation() : "Academic Institute")
                    .matchingKeywordCount(Math.max(overlapCount, 1))
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

        // Khoảng trống nghiên cứu: Keyword ít bài nhất trong domain
        List<ResearchGapPoint> researchGaps = new ArrayList<>();
        List<Keyword> gapKeywords = keywordRepository.findResearchGapsInDomains(domains, PageRequest.of(0, 5));
        for (Keyword k : gapKeywords) {
            researchGaps.add(ResearchGapPoint.builder()
                    .term(k.getTerm())
                    .paperCount((long) k.getPaperCount())
                    .build());
        }

        return LandscapeSection.builder()
                .bubbleChart(bubbleChart)
                .tagCloud(tagCloud)
                .researchGaps(researchGaps)
                .build();
    }
}
