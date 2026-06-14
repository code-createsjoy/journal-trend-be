package com.norman.swp391.service.impl;

import com.norman.swp391.dto.helix.HelixDtos.HelixDashboardHighlights;
import com.norman.swp391.dto.helix.HelixDtos.HelixHighlightCard;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.*;
import com.norman.swp391.service.DashboardHighlightService;
import com.norman.swp391.service.KeywordTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Tổng hợp thẻ highlight cho dashboard Helix.
 */
@Service
@RequiredArgsConstructor
public class DashboardHighlightServiceImpl implements DashboardHighlightService {

    private final KeywordTrendService keywordTrendService;
    private final AuthorRepository authorRepository;
    private final PaperRepository paperRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final FollowKeywordRepository followKeywordRepository;
    private final KeywordRepository keywordRepository;

    @Override
    @Transactional(readOnly = true)
    public HelixDashboardHighlights buildHighlights() {
        List<TrendingKeywordResponse> trending = keywordTrendService.findTopByTrendScore(1);
        TrendingKeywordResponse topKeywordInfo = trending.isEmpty() ? null : trending.get(0);

        HelixHighlightCard topKeyword = topKeywordInfo != null
                ? card(
                        String.valueOf(topKeywordInfo.getKeywordId()),
                        topKeywordInfo.getTerm(),
                        "Top trending keyword",
                        score(topKeywordInfo),
                        "trend %")
                : emptyCard("keyword", "No keyword data");

        HelixHighlightCard topTopicCard = topKeywordInfo != null
                ? card(
                        String.valueOf(topKeywordInfo.getKeywordId()),
                        topKeywordInfo.getDomain() != null ? topKeywordInfo.getDomain() : "General",
                        topKeywordInfo.getPaperCount() + " papers",
                        score(topKeywordInfo),
                        "trend %")
                : emptyCard("topic", "No topic data");

        HelixHighlightCard topAuthor = authorRepository.findFeatured(PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(this::authorCard)
                .orElse(emptyCard("author", "No author data"));

        HelixHighlightCard topPaper = paperRepository
                .findFirstByStatusOrderByCitationCountDesc(PaperStatus.ACTIVE)
                .map(this::paperCardByCitations)
                .orElseGet(() -> mostSavedPaperCard().orElse(emptyCard("paper", "No paper data")));

        HelixHighlightCard topFollowedTopic = mostFollowedKeywordCard().orElse(topTopicCard);

        return new HelixDashboardHighlights(topKeyword, topAuthor, topPaper, topFollowedTopic);
    }

    private Optional<HelixHighlightCard> mostSavedPaperCard() {
        List<Object[]> rows = collectionPaperRepository.findMostSavedPaperIds(PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Long paperId = (Long) rows.get(0)[0];
        Long saves = (Long) rows.get(0)[1];
        return paperRepository.findById(paperId).map(p -> card(
                String.valueOf(p.getId()),
                truncate(p.getTitle(), 80),
                p.getJournal() != null ? p.getJournal() : "—",
                saves.doubleValue(),
                "saves"));
    }

    private Optional<HelixHighlightCard> mostFollowedKeywordCard() {
        List<Object[]> rows = followKeywordRepository.countFollowsByKeyword(PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Long keywordId = (Long) rows.get(0)[0];
        Long followers = (Long) rows.get(0)[1];
        return keywordRepository.findById(keywordId).map(k -> card(
                String.valueOf(k.getKeywordId()),
                k.getTerm(),
                "Most followed on platform",
                followers.doubleValue(),
                "followers"));
    }

    private HelixHighlightCard authorCard(Author author) {
        return card(
                String.valueOf(author.getId()),
                author.getName(),
                author.getAffiliation() != null ? author.getAffiliation() : "—",
                author.getCitationCount(),
                "citations");
    }

    private HelixHighlightCard paperCardByCitations(Paper paper) {
        return card(
                String.valueOf(paper.getId()),
                truncate(paper.getTitle(), 80),
                paper.getJournal() != null ? paper.getJournal() : "—",
                paper.getCitationCount(),
                "citations");
    }

    private static double score(TrendingKeywordResponse keyword) {
        return keyword.getTrendScore() != null ? keyword.getTrendScore().doubleValue() : 0;
    }

    private static HelixHighlightCard card(
            String id, String title, String subtitle, double metric, String metricLabel) {
        return new HelixHighlightCard(id, title, subtitle, metric, metricLabel);
    }

    private static HelixHighlightCard emptyCard(String kind, String title) {
        return new HelixHighlightCard("", title, kind, 0, "");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
