package com.norman.swp391.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.keyword.ForecastDetailResponse;
import com.norman.swp391.dto.response.keyword.ForecastListResponse;
import com.norman.swp391.dto.response.keyword.ForecastMonthDto;
import com.norman.swp391.entity.FutureTrendForecast;
import com.norman.swp391.entity.Keyword;
import com.norman.swp391.entity.PublicationTrend;
import com.norman.swp391.entity.enums.ForecastCategory;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.repository.FutureTrendForecastRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.PublicationTrendRepository;
import com.norman.swp391.service.FutureTrendForecastService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FutureTrendForecastServiceImpl implements FutureTrendForecastService {

    private final KeywordRepository keywordRepository;
    private final PublicationTrendRepository publicationTrendRepository;
    private final FutureTrendForecastRepository forecastRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    // ────────────────────────────────────────────────────────
    // PUBLIC API
    // ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void runForecastJob() {
        log.info("[ForecastJob] Bắt đầu tính toán dự báo hot topic");
        LocalDateTime jobStart = LocalDateTime.now();
        AppProperties.Sync cfg = appProperties.getSync();

        List<Keyword> allKeywords = keywordRepository.findAll();
        int minMonths = cfg.getForecastMinMonths();

        // Tải toàn bộ lịch sử trend trong 1 query rồi gom theo keyword (tránh N+1 trên hàng nghìn keyword).
        Map<Long, List<PublicationTrend>> historyByKeyword = new HashMap<>();
        for (PublicationTrend pt : publicationTrendRepository.findAllWithKeywordOrderedByDate()) {
            historyByKeyword
                .computeIfAbsent(pt.getKeyword().getKeywordId(), k -> new ArrayList<>())
                .add(pt);
        }

        // ── VÒNG 1: Thu thập chỉ số thô + tìm min/max ───────────────
        record KeywordMetrics(
            Keyword keyword,
            List<PublicationTrend> history,
            double slope,
            double intercept,
            double acc,
            double vol
        ) {}

        List<KeywordMetrics> validList = new ArrayList<>();
        double maxSlope = -Double.MAX_VALUE, minSlope = Double.MAX_VALUE;
        double maxAcc   = -Double.MAX_VALUE, minAcc   = Double.MAX_VALUE;
        double maxVol   = -Double.MAX_VALUE, minVol   = Double.MAX_VALUE;

        for (Keyword kw : allKeywords) {
            List<PublicationTrend> history = recentWindow(historyByKeyword.get(kw.getKeywordId()));

            if (history.size() < minMonths) continue;

            double slope = calcSlope(history);
            if (slope <= 0) continue;  // Loại keyword không tăng trưởng

            double intercept = calcIntercept(history, slope);
            double acc       = calcAcceleration(history);
            double vol       = Math.log(kw.getPaperCount() + 1.0);

            validList.add(new KeywordMetrics(kw, history, slope, intercept, acc, vol));

            maxSlope = Math.max(maxSlope, slope); minSlope = Math.min(minSlope, slope);
            maxAcc   = Math.max(maxAcc, acc);     minAcc   = Math.min(minAcc, acc);
            maxVol   = Math.max(maxVol, vol);     minVol   = Math.min(minVol, vol);
        }

        if (validList.isEmpty()) {
            log.warn("[ForecastJob] Không có keyword nào hợp lệ để dự báo");
            return;
        }

        // ── VÒNG 2: Chuẩn hóa Min-Max + tính sTPS + dự báo ───────────
        double wSlope = cfg.getForecastWeightSlope();
        double wAcc   = cfg.getForecastWeightAcc();
        double wVol   = cfg.getForecastWeightVolume();

        List<FutureTrendForecast> toSave = new ArrayList<>();

        for (KeywordMetrics m : validList) {
            double sNorm = minMax(m.slope(), minSlope, maxSlope);
            double aNorm = minMax(m.acc(),   minAcc,   maxAcc);
            double vNorm = minMax(m.vol(),   minVol,   maxVol);

            double sTPS = (sNorm * wSlope + aNorm * wAcc + vNorm * wVol) * 100.0;

            List<ForecastMonthDto> forecastMonths = buildForecast(m.history(), m.slope(), m.intercept());
            int predictedTotal = forecastMonths.stream().mapToInt(ForecastMonthDto::getPaperCount).sum();

            double currentTotal = Math.max(1.0, m.keyword().getPaperCount());
            double growthRate   = (predictedTotal / currentTotal) * 100.0;

            String category = ForecastCategory.classify(sTPS, m.acc()).name();

            toSave.add(FutureTrendForecast.builder()
                .keyword(m.keyword())
                .potentialScore(bd(sTPS, 2))
                .predictedPapers6m(predictedTotal)
                .predictedGrowthRate(bd(growthRate, 2))
                .forecastReason(category)
                .forecastMonthsJson(toJson(forecastMonths))
                .calculatedAt(jobStart)
                .build());
        }

        // Giữ lại tối đa forecastMaxKeywords keyword điểm cao nhất
        int maxKeywords = cfg.getForecastMaxKeywords();
        if (toSave.size() > maxKeywords) {
            toSave.sort(Comparator.comparing(FutureTrendForecast::getPotentialScore).reversed());
            toSave = new ArrayList<>(toSave.subList(0, maxKeywords));
        }

        // Xóa bản ghi cũ NGAY TRƯỚC KHI save bản mới → tránh downtime
        forecastRepository.deleteByCalculatedAtBefore(jobStart);
        forecastRepository.saveAll(toSave);

        log.info("[ForecastJob] Hoàn tất: {} keyword được dự báo", toSave.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ForecastListResponse> getTopForecasts(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return forecastRepository.findTopByScore(PageRequest.of(0, size))
            .stream()
            .map(f -> ForecastListResponse.builder()
                .keywordId(f.getKeyword().getKeywordId())
                .term(f.getKeyword().getTerm())
                .domain(f.getKeyword().getDomain())
                .potentialScore(f.getPotentialScore())
                .predictedPapers6m(f.getPredictedPapers6m())
                .predictedGrowthRate(f.getPredictedGrowthRate())
                .forecastReason(f.getForecastReason())
                .currentPaperCount(f.getKeyword().getPaperCount())
                .build())
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ForecastDetailResponse getForecastDetail(Long keywordId) {
        FutureTrendForecast forecast = forecastRepository.findByKeywordId(keywordId)
            .orElseThrow(() -> new ResourceNotFoundException("Không có dự báo cho keyword: " + keywordId));

        // Lấy lịch sử 12 tháng gần nhất để vẽ phần nét liền (thứ tự thời gian tăng dần)
        List<ForecastMonthDto> historical = recentHistoryAscending(keywordId).stream()
            .map(pt -> ForecastMonthDto.builder()
                .year(pt.getYear())
                .month(pt.getMonth())
                .paperCount(pt.getPaperCount())
                .build())
            .toList();

        List<ForecastMonthDto> forecastMonths = fromJson(forecast.getForecastMonthsJson());

        return ForecastDetailResponse.builder()
            .keywordId(forecast.getKeyword().getKeywordId())
            .term(forecast.getKeyword().getTerm())
            .domain(forecast.getKeyword().getDomain())
            .potentialScore(forecast.getPotentialScore())
            .predictedPapers6m(forecast.getPredictedPapers6m())
            .predictedGrowthRate(forecast.getPredictedGrowthRate())
            .forecastReason(forecast.getForecastReason())
            .historicalMonths(historical)
            .forecastMonths(forecastMonths)
            .build();
    }

    // ────────────────────────────────────────────────────────
    // DỮ LIỆU LỊCH SỬ
    // Lấy tối đa `forecast-history-window` tháng GẦN NHẤT rồi sắp xếp tăng dần
    // theo thời gian. (DESC + limit + reverse — tránh lấy nhầm các tháng cũ nhất.)
    // ────────────────────────────────────────────────────────
    private List<PublicationTrend> recentHistoryAscending(Long keywordId) {
        int historyWindow = appProperties.getSync().getForecastHistoryWindow();
        List<PublicationTrend> recentDesc = publicationTrendRepository
            .findByKeywordIdOrderByYearDescMonthDesc(keywordId)
            .stream()
            .limit(historyWindow)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.reverse(recentDesc);
        return recentDesc;
    }

    /**
     * Cắt {@code forecast-history-window} tháng GẦN NHẤT từ list lịch sử đã sắp tăng dần.
     * Dùng trong job (list được gom sẵn từ 1 query) để tránh N+1.
     */
    private List<PublicationTrend> recentWindow(List<PublicationTrend> ascHistory) {
        if (ascHistory == null || ascHistory.isEmpty()) {
            return List.of();
        }
        int historyWindow = appProperties.getSync().getForecastHistoryWindow();
        int size = ascHistory.size();
        int from = Math.max(0, size - historyWindow);
        return ascHistory.subList(from, size);
    }

    // ────────────────────────────────────────────────────────
    // CÔNG THỨC 1 — OLS SLOPE
    // Nguồn: Wikipedia "Simple Linear Regression" > "Formulation and computation"
    // https://en.wikipedia.org/wiki/Simple_linear_regression
    // ────────────────────────────────────────────────────────
    private double calcSlope(List<PublicationTrend> history) {
        int n = history.size();
        double xBar = (n - 1) / 2.0;
        double yBar = history.stream().mapToInt(PublicationTrend::getPaperCount).average().orElse(0);

        double sXX = 0, sXY = 0;
        for (int i = 0; i < n; i++) {
            double dx = i - xBar;
            double dy = history.get(i).getPaperCount() - yBar;
            sXX += dx * dx;
            sXY += dx * dy;
        }
        return sXX == 0 ? 0 : sXY / sXX;
    }

    private double calcIntercept(List<PublicationTrend> history, double slope) {
        int n = history.size();
        double xBar = (n - 1) / 2.0;
        double yBar = history.stream().mapToInt(PublicationTrend::getPaperCount).average().orElse(0);
        return yBar - slope * xBar;
    }

    // ────────────────────────────────────────────────────────
    // CÔNG THỨC 2 — ACCELERATION (chia đôi chuỗi)
    // Nguồn: Wikipedia "Simple Linear Regression" — áp dụng OLS trên 2 nửa chuỗi
    // ────────────────────────────────────────────────────────
    private double calcAcceleration(List<PublicationTrend> history) {
        int n = history.size();
        int half = n / 2;
        List<PublicationTrend> prior  = history.subList(0, half);
        List<PublicationTrend> recent = history.subList(half, n);
        return calcSlope(recent) - calcSlope(prior);
    }

    // ────────────────────────────────────────────────────────
    // CÔNG THỨC 4a — MIN-MAX NORMALIZATION
    // Nguồn: Wikipedia "Feature Scaling" > "Rescaling (min-max normalization)"
    // https://en.wikipedia.org/wiki/Feature_scaling
    // ────────────────────────────────────────────────────────
    private double minMax(double value, double min, double max) {
        if (max == min) return 0.5;
        return (value - min) / (max - min);
    }

    // ────────────────────────────────────────────────────────
    // CÔNG THỨC 5 — DỰ BÁO N THÁNG (Linear Forecast)
    // Số tháng dự báo = `forecast-horizon` (cấu hình).
    // Nguồn: Wikipedia "Simple Linear Regression" — y = slope*x + intercept
    // ────────────────────────────────────────────────────────
    private List<ForecastMonthDto> buildForecast(
            List<PublicationTrend> history, double slope, double intercept) {

        int horizon = appProperties.getSync().getForecastHorizon();
        int n = history.size();
        PublicationTrend last = history.get(n - 1);
        YearMonth lastMonth = YearMonth.of(last.getYear(), last.getMonth());

        List<ForecastMonthDto> result = new ArrayList<>();
        for (int m = 1; m <= horizon; m++) {
            double xFuture   = (n - 1) + m;
            int paperCount   = Math.max(0, (int) Math.round(slope * xFuture + intercept));
            YearMonth target = lastMonth.plusMonths(m);

            result.add(ForecastMonthDto.builder()
                .year(target.getYear())
                .month(target.getMonthValue())
                .paperCount(paperCount)
                .build());
        }
        return result;
    }

    // ────────────────────────────────────────────────────────
    // HỖ TRỢ
    // ────────────────────────────────────────────────────────
    private BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private String toJson(List<ForecastMonthDto> months) {
        try {
            return objectMapper.writeValueAsString(months);
        } catch (JsonProcessingException e) {
            log.warn("[ForecastJob] Không serialize được forecastMonths: {}", e.getMessage());
            return "[]";
        }
    }

    private List<ForecastMonthDto> fromJson(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ForecastMonthDto.class));
        } catch (JsonProcessingException e) {
            log.warn("[Forecast] Không parse được forecastMonthsJson: {}", e.getMessage());
            return List.of();
        }
    }
}
