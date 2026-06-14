package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.service.ReportExportService;
import com.norman.swp391.service.KeywordTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Triển khai xuất CSV.
 */
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private final KeywordTrendService keywordTrendService;
    private final PaperRepository paperRepository;

    @Override
    @Transactional(readOnly = true)
    public String exportTopicTrendsCsv() {
        List<TrendingKeywordResponse> keywords = keywordTrendService.findTopByTrendScore(50);
        StringBuilder sb = new StringBuilder("rank,keyword_id,term,domain,paper_count,trend_score\n");
        for (TrendingKeywordResponse k : keywords) {
            sb.append(k.getRank())
                    .append(',')
                    .append(k.getKeywordId())
                    .append(',')
                    .append(csv(k.getTerm()))
                    .append(',')
                    .append(csv(k.getDomain()))
                    .append(',')
                    .append(k.getPaperCount())
                    .append(',')
                    .append(k.getTrendScore() != null ? k.getTrendScore() : 0)
                    .append('\n');
        }
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public String exportPapersCsv(int limit) {
        int size = Math.max(1, Math.min(limit, 1000));
        List<Paper> papers = paperRepository
                .findByStatus(PaperStatus.ACTIVE, PageRequest.of(0, size, Sort.by("citationCount").descending()))
                .getContent();
        StringBuilder sb = new StringBuilder("id,title,journal,citations,doi,publication_date\n");
        for (Paper p : papers) {
            sb.append(p.getId())
                    .append(',')
                    .append(csv(p.getTitle()))
                    .append(',')
                    .append(csv(p.getJournal()))
                    .append(',')
                    .append(p.getCitationCount())
                    .append(',')
                    .append(csv(p.getDoi()))
                    .append(',')
                    .append(p.getPublicationDate() != null ? p.getPublicationDate() : "")
                    .append('\n');
        }
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
