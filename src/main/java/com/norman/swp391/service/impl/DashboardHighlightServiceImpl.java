package com.norman.swp391.service.impl;

import com.norman.swp391.dto.helix.HelixDtos.HelixDashboardHighlights;
import com.norman.swp391.dto.helix.HelixDtos.HelixHighlightCard;
import com.norman.swp391.dto.response.topic.TrendingTopicResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.*;
import com.norman.swp391.service.DashboardHighlightService;
import com.norman.swp391.service.TopicTrendService;
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

    private final TopicTrendService topicTrendService;
    private final AuthorRepository authorRepository;
    private final PaperRepository paperRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final FollowTopicRepository followTopicRepository;
    private final TopicRepository topicRepository;

    @Override
    @Transactional(readOnly = true)
/**
 * Tạo/ghép dữ liệu: buildHighlights.
 */
    public HelixDashboardHighlights buildHighlights() {
        List<TrendingTopicResponse> trending = topicTrendService.findTopByTrendScore(1);
        TrendingTopicResponse topTopic = trending.isEmpty() ? null : trending.get(0);

        HelixHighlightCard topKeyword = topTopic != null
                ? card(
                        String.valueOf(topTopic.getTopicId()),
                        topTopic.getTopicName(),
                        "Top trending keyword",
                        score(topTopic),
                        "trend %")
                : emptyCard("keyword", "No keyword data");

        HelixHighlightCard topTopicCard = topTopic != null
                ? card(
                        String.valueOf(topTopic.getTopicId()),
                        topTopic.getTopicName(),
                        topTopic.getPaperCount() + " papers",
                        score(topTopic),
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

        HelixHighlightCard topFollowedTopic = mostFollowedTopicCard().orElse(topTopicCard);

        return new HelixDashboardHighlights(topKeyword, topAuthor, topPaper, topFollowedTopic);
    }

/**
 * Xử lý nghiệp vụ: mostSavedPaperCard.
 */
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

/**
 * Xử lý nghiệp vụ: mostFollowedTopicCard.
 */
    private Optional<HelixHighlightCard> mostFollowedTopicCard() {
        List<Object[]> rows = followTopicRepository.countFollowsByTopic(PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Long topicId = (Long) rows.get(0)[0];
        Long followers = (Long) rows.get(0)[1];
        return topicRepository.findById(topicId).map(t -> card(
                String.valueOf(t.getId()),
                t.getName(),
                "Most followed on platform",
                followers.doubleValue(),
                "followers"));
    }

/**
 * Xử lý nghiệp vụ: authorCard.
 */
    private HelixHighlightCard authorCard(Author author) {
        return card(
                String.valueOf(author.getId()),
                author.getName(),
                author.getAffiliation() != null ? author.getAffiliation() : "—",
                author.getCitationCount(),
                "citations");
    }

/**
 * Xử lý nghiệp vụ: paperCardByCitations.
 */
    private HelixHighlightCard paperCardByCitations(Paper paper) {
        return card(
                String.valueOf(paper.getId()),
                truncate(paper.getTitle(), 80),
                paper.getJournal() != null ? paper.getJournal() : "—",
                paper.getCitationCount(),
                "citations");
    }

/**
 * Xử lý nghiệp vụ: score.
 */
    private static double score(TrendingTopicResponse topic) {
        return topic.getTrendScore() != null ? topic.getTrendScore().doubleValue() : 0;
    }

    private static HelixHighlightCard card(
            String id, String title, String subtitle, double metric, String metricLabel) {
        return new HelixHighlightCard(id, title, subtitle, metric, metricLabel);
    }

/**
 * Xử lý nghiệp vụ: emptyCard.
 */
    private static HelixHighlightCard emptyCard(String kind, String title) {
        return new HelixHighlightCard("", title, kind, 0, "");
    }

/**
 * Xử lý nghiệp vụ: truncate.
 */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
