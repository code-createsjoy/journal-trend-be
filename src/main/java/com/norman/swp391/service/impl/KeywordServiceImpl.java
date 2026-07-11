package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.keyword.KeywordResponse;
import com.norman.swp391.dto.response.keyword.KeywordTrendResponse;
import com.norman.swp391.dto.response.keyword.TrendingKeywordResponse;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PublicationTrend;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.KeywordMapper;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.PublicationTrendRepository;
import com.norman.swp391.service.KeywordService;
import com.norman.swp391.service.KeywordTrendService;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeywordServiceImpl implements KeywordService {

    private final KeywordRepository keywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final KeywordTrendService keywordTrendService;

    /** Lấy toàn bộ keyword trong hệ thống. */
    @Override
    @Transactional(readOnly = true)
    public List<KeywordResponse> listAll() {
        return KeywordMapper.toResponseList(keywordRepository.findAll());
    }

    /** Lấy 1 keyword theo id. */
    @Override
    @Transactional(readOnly = true)
    public KeywordResponse getById(Long id) {
        Keyword keyword = keywordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword", id));
        return KeywordMapper.toResponse(keyword);
    }

    /** Danh sách keyword đang trending (delegate sang KeywordTrendService). */
    @Override
    @Transactional(readOnly = true)
    public List<TrendingKeywordResponse> getTrendingKeywords(Integer year, Integer month) {
        return keywordTrendService.findTrendingKeywordResponses(year, month);
    }

    /** Lấy dữ liệu trend theo tháng của 1 keyword, giới hạn N tháng gần nhất (để vẽ chart). */
    @Override
    @Transactional(readOnly = true)
    public List<KeywordTrendResponse> getKeywordTrendChart(Long keywordId, int months) {
        keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword", keywordId));
        List<PublicationTrend> trends = publicationTrendRepository.findByKeywordIdOrderByYearDescMonthDesc(keywordId);
        int limit = Math.max(months, 1);
        List<PublicationTrend> limited = trends.stream()
                .limit(limit)
                .sorted(Comparator.comparing(PublicationTrend::getYear).thenComparing(PublicationTrend::getMonth))
                .toList();
        return KeywordMapper.toTrendResponseList(limited);
    }
}
