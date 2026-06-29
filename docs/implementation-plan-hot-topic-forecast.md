# Implementation Plan — Dự Báo Hot Topic Tương Lai (6 Tháng)

> Tài liệu này là kế hoạch triển khai chi tiết, theo thứ tự thực hiện.  
> Đọc kèm: `future-hot-topic-formulas.md` (công thức) và `future_hot_topic_design.md` (thiết kế gốc).

---

## Tổng Quan Thứ Tự Thực Hiện

```
BACKEND  (thực hiện trước)
  B1  →  SQL: Tạo bảng future_trend_forecasts
  B2  →  Entity: FutureTrendForecast.java
  B3  →  Repository: FutureTrendForecastRepository.java
  B4  →  Repository: Bổ sung query vào PublicationTrendRepository.java
  B5  →  Config: Thêm forecastCron vào AppProperties.java
  B6  →  DTO: ForecastMonthDto, ForecastListResponse, ForecastDetailResponse
  B7  →  Service: FutureTrendForecastService.java (interface)
  B8  →  Service: FutureTrendForecastServiceImpl.java (5 công thức)
  B9  →  Scheduler: FutureTrendForecastScheduler.java
  B10 →  Controller: Thêm endpoints vào TrendController.java

FRONTEND  (sau khi backend xong)
  F1  →  Types: Định nghĩa TypeScript cho forecast data
  F2  →  Hook: useForecastList, useForecastDetail
  F3  →  Component: HotTopicForecastCard (bảng xếp hạng)
  F4  →  Component: ForecastLineChart (biểu đồ nét liền + đứt nét)
  F5  →  Route: Thêm section vào trends.tsx
```

---

## BACKEND

---

### B1 — SQL: Tạo Bảng `future_trend_forecasts`

Chạy script này trực tiếp trên SQL Server (hoặc Hibernate tự tạo từ Entity):

```sql
CREATE TABLE future_trend_forecasts (
    id                   BIGINT IDENTITY(1,1) PRIMARY KEY,
    keyword_id           BIGINT         NOT NULL,
    potential_score      DECIMAL(5,2)   NOT NULL,
    predicted_papers_6m  INT            NOT NULL,
    predicted_growth_rate DECIMAL(10,2) NOT NULL,
    forecast_reason      NVARCHAR(100)  NOT NULL,
    forecast_months_json NVARCHAR(MAX)  NOT NULL,
    calculated_at        DATETIME       NOT NULL,

    CONSTRAINT FK_Forecast_Keyword
        FOREIGN KEY (keyword_id) REFERENCES keywords(keyword_id)
        ON DELETE CASCADE
);

CREATE INDEX IDX_Forecast_Score
    ON future_trend_forecasts(potential_score DESC);

CREATE INDEX IDX_Forecast_Keyword
    ON future_trend_forecasts(keyword_id);
```

> `forecast_months_json` lưu JSON string của 6 tháng dự báo.  
> Tránh tạo bảng join thêm — đơn giản hóa query, vẫn đủ dùng cho frontend.

---

### B2 — Entity: `FutureTrendForecast.java`

**File:** `src/main/java/com/norman/swp391/entity/FutureTrendForecast.java`

```java
package com.norman.swp391.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "future_trend_forecasts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FutureTrendForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Column(name = "potential_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal potentialScore;

    @Column(name = "predicted_papers_6m", nullable = false)
    private int predictedPapers6m;

    @Column(name = "predicted_growth_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedGrowthRate;

    @Column(name = "forecast_reason", nullable = false, length = 100)
    private String forecastReason;

    // JSON string: [{"year":2026,"month":7,"paperCount":52}, ...]
    @Column(name = "forecast_months_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String forecastMonthsJson;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
```

---

### B3 — Repository: `FutureTrendForecastRepository.java`

**File:** `src/main/java/com/norman/swp391/repository/FutureTrendForecastRepository.java`

```java
package com.norman.swp391.repository;

import com.norman.swp391.entity.FutureTrendForecast;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FutureTrendForecastRepository extends JpaRepository<FutureTrendForecast, Long> {

    // Top N theo điểm cao nhất — dùng cho API danh sách
    @Query("""
        SELECT f FROM FutureTrendForecast f
        JOIN FETCH f.keyword
        ORDER BY f.potentialScore DESC
        """)
    List<FutureTrendForecast> findTopByScore(Pageable pageable);

    // Chi tiết 1 keyword — dùng cho API detail
    @Query("""
        SELECT f FROM FutureTrendForecast f
        JOIN FETCH f.keyword
        WHERE f.keyword.keywordId = :keywordId
        """)
    Optional<FutureTrendForecast> findByKeywordId(@Param("keywordId") Long keywordId);

    // Xóa bản ghi cũ trước khi insert mới — tránh deleteAll() gây downtime
    @Modifying
    @Query("DELETE FROM FutureTrendForecast f WHERE f.calculatedAt < :before")
    void deleteByCalculatedAtBefore(@Param("before") LocalDateTime before);

    // Kiểm tra lần tính gần nhất — dùng để log
    @Query("SELECT MAX(f.calculatedAt) FROM FutureTrendForecast f")
    Optional<LocalDateTime> findLatestCalculatedAt();
}
```

---

### B4 — Repository: Bổ sung query vào `PublicationTrendRepository.java`

**File:** `src/main/java/com/norman/swp391/repository/PublicationTrendRepository.java`  
**Thêm method sau vào interface hiện có** (không xóa gì cũ):

```java
// Lấy 12 tháng lịch sử gần nhất theo thứ tự thời gian tăng dần
@Query("""
    SELECT pt FROM PublicationTrend pt
    WHERE pt.keyword.keywordId = :keywordId
    ORDER BY pt.year ASC, pt.month ASC
    """)
List<PublicationTrend> findHistoryByKeywordId(@Param("keywordId") Long keywordId);
```

---

### B5 — Config: Thêm vào `AppProperties.java`

**File:** `src/main/java/com/norman/swp391/config/AppProperties.java`  
**Thêm vào bên trong class `Sync`** (cạnh các field hiện có):

```java
/** Cron cho job dự báo hot topic — mặc định 4AM ngày 1 hàng tháng. */
private String forecastCron = "0 0 4 1 * *";

/** Số tháng lịch sử tối thiểu để tính forecast. */
private int forecastMinMonths = 6;

/** Số keyword tối đa lưu vào bảng forecast. */
private int forecastMaxKeywords = 200;

/** Trọng số slope trong sTPS (mặc định 0.5). */
private double forecastWeightSlope = 0.5;

/** Trọng số acceleration trong sTPS (mặc định 0.3). */
private double forecastWeightAcc = 0.3;

/** Trọng số volume trong sTPS (mặc định 0.2). */
private double forecastWeightVolume = 0.2;
```

---

### B6 — DTOs

#### `ForecastMonthDto.java`
**File:** `src/main/java/com/norman/swp391/dto/response/keyword/ForecastMonthDto.java`

```java
package com.norman.swp391.dto.response.keyword;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ForecastMonthDto {
    private int year;
    private int month;
    private int paperCount;
}
```

---

#### `ForecastListResponse.java`
**File:** `src/main/java/com/norman/swp391/dto/response/keyword/ForecastListResponse.java`

```java
package com.norman.swp391.dto.response.keyword;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ForecastListResponse {
    private Long keywordId;
    private String term;
    private String domain;
    private BigDecimal potentialScore;       // sTPS 0-100
    private int predictedPapers6m;           // tổng bài dự báo 6 tháng
    private BigDecimal predictedGrowthRate;  // % tăng trưởng
    private String forecastReason;           // "Bùng nổ sớm" / ...
    private int currentPaperCount;           // số bài hiện tại
}
```

---

#### `ForecastDetailResponse.java`
**File:** `src/main/java/com/norman/swp391/dto/response/keyword/ForecastDetailResponse.java`

```java
package com.norman.swp391.dto.response.keyword;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ForecastDetailResponse {
    private Long keywordId;
    private String term;
    private String domain;
    private BigDecimal potentialScore;
    private int predictedPapers6m;
    private BigDecimal predictedGrowthRate;
    private String forecastReason;

    // Lịch sử 12 tháng (nét liền trên chart)
    private List<ForecastMonthDto> historicalMonths;

    // Dự báo 6 tháng (nét đứt trên chart)
    private List<ForecastMonthDto> forecastMonths;
}
```

---

### B7 — Service Interface: `FutureTrendForecastService.java`

**File:** `src/main/java/com/norman/swp391/service/FutureTrendForecastService.java`

```java
package com.norman.swp391.service;

import com.norman.swp391.dto.response.keyword.ForecastDetailResponse;
import com.norman.swp391.dto.response.keyword.ForecastListResponse;
import java.util.List;

public interface FutureTrendForecastService {

    /** Chạy toàn bộ pipeline tính toán và lưu vào DB. Gọi từ Scheduler. */
    void runForecastJob();

    /** Trả về top N keyword có điểm sTPS cao nhất. */
    List<ForecastListResponse> getTopForecasts(int limit);

    /** Trả về chi tiết dự báo 1 keyword kèm lịch sử + 6 tháng tới. */
    ForecastDetailResponse getForecastDetail(Long keywordId);
}
```

---

### B8 — Service Impl: `FutureTrendForecastServiceImpl.java`

**File:** `src/main/java/com/norman/swp391/service/impl/FutureTrendForecastServiceImpl.java`

```java
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
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.repository.FutureTrendForecastRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.PublicationTrendRepository;
import com.norman.swp391.service.FutureTrendForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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

        // ── VÒNG 1: Thu thập chỉ số thô + tìm min/max ─────────────────
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
            List<PublicationTrend> history = publicationTrendRepository
                .findHistoryByKeywordId(kw.getKeywordId())
                .stream().limit(12).toList();

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

            String reason = classifyReason(sTPS, m.acc());

            toSave.add(FutureTrendForecast.builder()
                .keyword(m.keyword())
                .potentialScore(bd(sTPS, 2))
                .predictedPapers6m(predictedTotal)
                .predictedGrowthRate(bd(growthRate, 2))
                .forecastReason(reason)
                .forecastMonthsJson(toJson(forecastMonths))
                .calculatedAt(jobStart)
                .build());
        }

        // Xóa bản ghi cũ NGAY TRƯỚC KHI save bản mới — tránh downtime
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

        // Lấy lịch sử 12 tháng để vẽ phần nét liền
        List<ForecastMonthDto> historical = publicationTrendRepository
            .findHistoryByKeywordId(keywordId)
            .stream().limit(12)
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
    // CÔNG THỨC 1 — OLS SLOPE
    // Nguồn: Wikipedia "Simple Linear Regression" > mục "Formulation and computation"
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
    // CÔNG THỨC 2 — ACCELERATION (chia đôi 6/6)
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
    // Nguồn: Wikipedia "Feature Scaling" > mục "Rescaling (min-max normalization)"
    // https://en.wikipedia.org/wiki/Feature_scaling
    // ────────────────────────────────────────────────────────
    private double minMax(double value, double min, double max) {
        if (max == min) return 0.5;
        return (value - min) / (max - min);
    }

    // ────────────────────────────────────────────────────────
    // CÔNG THỨC 5 — DỰ BÁO 6 THÁNG (Linear Forecast)
    // Nguồn: Wikipedia "Simple Linear Regression" — áp dụng y = slope*x + intercept
    // ────────────────────────────────────────────────────────
    private List<ForecastMonthDto> buildForecast(
            List<PublicationTrend> history, double slope, double intercept) {

        int n = history.size();
        PublicationTrend last = history.get(n - 1);
        YearMonth lastMonth = YearMonth.of(last.getYear(), last.getMonth());

        List<ForecastMonthDto> result = new ArrayList<>();
        for (int m = 1; m <= 6; m++) {
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
    private String classifyReason(double sTPS, double acc) {
        if (sTPS >= 80 && acc > 0) return "Bùng nổ sớm";
        if (sTPS >= 60)            return "Tăng trưởng vượt bậc";
        return "Tăng trưởng ổn định";
    }

    private BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private String toJson(List<ForecastMonthDto> months) {
        try {
            return objectMapper.writeValueAsString(months);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<ForecastMonthDto> fromJson(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ForecastMonthDto.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
```

---

### B9 — Scheduler: `FutureTrendForecastScheduler.java`

**File:** `src/main/java/com/norman/swp391/scheduler/FutureTrendForecastScheduler.java`

```java
package com.norman.swp391.scheduler;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.service.FutureTrendForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FutureTrendForecastScheduler {

    private final FutureTrendForecastService forecastService;
    private final AppProperties appProperties;

    // Chạy lúc 4:00 AM ngày mùng 1 hàng tháng
    // (sau DataSyncScheduler 2AM và PaperReviewMaintenance 3:30AM)
    @Scheduled(cron = "${app.sync.forecast-cron:0 0 4 1 * *}")
    public void runMonthlyForecast() {
        if (!appProperties.isSchedulerEnabled()) {
            log.debug("[ForecastScheduler] Scheduler disabled, bỏ qua");
            return;
        }
        log.info("[ForecastScheduler] Bắt đầu job dự báo hot topic tháng mới");
        try {
            forecastService.runForecastJob();
        } catch (Exception e) {
            log.error("[ForecastScheduler] Job thất bại: {}", e.getMessage(), e);
        }
    }
}
```

---

### B10 — Controller: Thêm endpoints vào `TrendController.java`

**File:** `src/main/java/com/norman/swp391/controller/v1/TrendController.java`  
**Thêm vào controller hiện có** (không thay thế code cũ):

```java
// Thêm dependency vào constructor:
private final FutureTrendForecastService forecastService;

// Thêm 2 endpoint mới:

/** Danh sách top keyword có tiềm năng cao nhất (mặc định 10). */
@GetMapping("/forecast")
public ApiResponse<List<ForecastListResponse>> getTopForecasts(
        @RequestParam(defaultValue = "10") int limit) {
    return ApiResponse.ok(forecastService.getTopForecasts(limit));
}

/** Chi tiết dự báo 1 keyword kèm lịch sử + 6 tháng tới. */
@GetMapping("/forecast/{keywordId}")
public ApiResponse<ForecastDetailResponse> getForecastDetail(
        @PathVariable Long keywordId) {
    return ApiResponse.ok(forecastService.getForecastDetail(keywordId));
}
```

**Kết quả API:**
```
GET /api/v1/trends/forecast?limit=10
GET /api/v1/trends/forecast/{keywordId}
```

---

## FRONTEND

---

### F1 — Types: `src/types/forecast.ts`

**File:** `src/types/forecast.ts`

```typescript
export type ForecastMonth = {
  year: number;
  month: number;
  paperCount: number;
};

// Response từ GET /api/v1/trends/forecast?limit=N
export type ForecastListItem = {
  keywordId: number;
  term: string;
  domain: string;
  potentialScore: number;      // sTPS 0-100
  predictedPapers6m: number;
  predictedGrowthRate: number; // %
  forecastReason: string;
  currentPaperCount: number;
};

// Response từ GET /api/v1/trends/forecast/{keywordId}
export type ForecastDetail = {
  keywordId: number;
  term: string;
  domain: string;
  potentialScore: number;
  predictedPapers6m: number;
  predictedGrowthRate: number;
  forecastReason: string;
  historicalMonths: ForecastMonth[]; // 12 tháng nét liền
  forecastMonths: ForecastMonth[];   // 6 tháng nét đứt
};
```

---

### F2 — Hook: `src/hooks/data/use-forecast.ts`

**File:** `src/hooks/data/use-forecast.ts`

```typescript
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/api/client";
import type { ForecastDetail, ForecastListItem } from "@/types/forecast";

type ApiWrap<T> = { data: T };

// Danh sách top N keyword tiềm năng
export function useForecastList(limit = 10) {
  return useQuery({
    queryKey: ["forecast", "list", limit],
    queryFn: () =>
      apiClient
        .get<ApiWrap<ForecastListItem[]>>("/v1/trends/forecast", {
          params: { limit },
        })
        .then((r) => r.data),
    staleTime: 1000 * 60 * 60, // 1 giờ — kết quả tính 1 lần/tháng
  });
}

// Chi tiết 1 keyword
export function useForecastDetail(keywordId: number | null) {
  return useQuery({
    queryKey: ["forecast", "detail", keywordId],
    queryFn: () =>
      apiClient
        .get<ApiWrap<ForecastDetail>>(`/v1/trends/forecast/${keywordId}`)
        .then((r) => r.data),
    enabled: keywordId !== null,
    staleTime: 1000 * 60 * 60,
  });
}
```

---

### F3 — Component: `HotTopicForecastCard`

**File:** `src/components/HotTopicForecastCard.tsx`

```tsx
import type { ForecastListItem } from "@/types/forecast";

const REASON_BADGE: Record<string, { label: string; class: string }> = {
  "Bùng nổ sớm":           { label: "Bùng nổ sớm",     class: "bg-orange-500/15 text-orange-500 border-orange-500/30" },
  "Tăng trưởng vượt bậc":  { label: "Vượt bậc",        class: "bg-purple-500/15 text-purple-500 border-purple-500/30" },
  "Tăng trưởng ổn định":   { label: "Ổn định",          class: "bg-blue-500/15   text-blue-500   border-blue-500/30"   },
};

type Props = {
  items: ForecastListItem[];
  isLoading: boolean;
  onSelect?: (keywordId: number) => void;
};

export function HotTopicForecastCard({ items, isLoading, onSelect }: Props) {
  if (isLoading) {
    return (
      <div className="h-48 flex items-center justify-center text-sm text-muted-foreground animate-pulse">
        Đang tải dự báo...
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="h-48 flex items-center justify-center text-sm text-muted-foreground">
        Chưa có dữ liệu dự báo. Scheduler chạy ngày 1 hàng tháng.
      </div>
    );
  }

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-[10px] uppercase tracking-widest text-muted-foreground border-b border-border">
          <th className="text-left font-medium pb-3">#</th>
          <th className="text-left font-medium pb-3">Keyword</th>
          <th className="text-right font-medium pb-3">Điểm sTPS</th>
          <th className="text-right font-medium pb-3">Dự báo 6T</th>
          <th className="text-right font-medium pb-3">Tăng trưởng</th>
          <th className="text-left font-medium pb-3 pl-4">Phân loại</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border">
        {items.map((item, idx) => {
          const badge = REASON_BADGE[item.forecastReason] ?? REASON_BADGE["Tăng trưởng ổn định"];
          return (
            <tr
              key={item.keywordId}
              className="hover:bg-secondary/40 transition-colors cursor-pointer"
              onClick={() => onSelect?.(item.keywordId)}
            >
              <td className="py-3 text-muted-foreground font-mono text-xs w-8">
                {idx + 1}
              </td>
              <td className="py-3 font-medium text-foreground">
                <div>{item.term}</div>
                {item.domain && (
                  <div className="text-[10px] text-muted-foreground">{item.domain}</div>
                )}
              </td>
              <td className="py-3 text-right">
                <span
                  className={`font-mono font-bold text-sm ${
                    item.potentialScore >= 80
                      ? "text-orange-500"
                      : item.potentialScore >= 60
                      ? "text-purple-500"
                      : "text-blue-500"
                  }`}
                >
                  {item.potentialScore.toFixed(1)}
                </span>
                <span className="text-[10px] text-muted-foreground">/100</span>
              </td>
              <td className="py-3 text-right font-mono text-muted-foreground">
                {item.predictedPapers6m.toLocaleString()} bài
              </td>
              <td className="py-3 text-right font-mono text-success">
                +{item.predictedGrowthRate.toFixed(1)}%
              </td>
              <td className="py-3 pl-4">
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-md border font-medium ${badge.class}`}
                >
                  {badge.label}
                </span>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
```

---

### F4 — Component: `ForecastLineChart`

**File:** `src/components/ForecastLineChart.tsx`

```tsx
import {
  CartesianGrid, Legend, Line, LineChart,
  ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import type { ForecastDetail } from "@/types/forecast";

const MONTH_NAMES = ["","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

type ChartPoint = {
  label: string;
  sortKey: number;
  historical?: number;
  forecast?: number;
  isForecast: boolean;
};

type Props = {
  detail: ForecastDetail;
};

const tooltipStyle = {
  background: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: 8,
  color: "var(--popover-foreground)",
  fontSize: 12,
} as const;

export function ForecastLineChart({ detail }: Props) {
  // Kết hợp dữ liệu lịch sử + dự báo thành 1 mảng cho Recharts
  const chartData: ChartPoint[] = [
    ...detail.historicalMonths.map((p) => ({
      label: `${MONTH_NAMES[p.month]} ${p.year}`,
      sortKey: p.year * 100 + p.month,
      historical: p.paperCount,
      isForecast: false,
    })),
    ...detail.forecastMonths.map((p) => ({
      label: `${MONTH_NAMES[p.month]} ${p.year}`,
      sortKey: p.year * 100 + p.month,
      forecast: p.paperCount,
      isForecast: true,
    })),
  ].sort((a, b) => a.sortKey - b.sortKey);

  // Điểm nối giữa lịch sử và dự báo (tháng cuối của lịch sử)
  const lastHistorical = detail.historicalMonths.at(-1);
  const bridgeLabel = lastHistorical
    ? `${MONTH_NAMES[lastHistorical.month]} ${lastHistorical.year}`
    : undefined;

  return (
    <div>
      <div className="flex items-center gap-4 mb-3 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="inline-block w-6 h-0.5 bg-indigo-500 rounded" />
          Dữ liệu thực tế
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block w-6 border-t-2 border-dashed border-purple-400" />
          Dự báo 6 tháng
        </span>
      </div>

      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="label"
            stroke="var(--muted-foreground)"
            fontSize={10}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            stroke="var(--muted-foreground)"
            fontSize={10}
            tickLine={false}
            axisLine={false}
            label={{
              value: "Bài báo/tháng",
              angle: -90,
              position: "insideLeft",
              style: { fill: "var(--muted-foreground)", fontSize: 9 },
            }}
          />
          <Tooltip
            contentStyle={tooltipStyle}
            formatter={(value: number, name: string) => [
              `${value} bài`,
              name === "historical" ? "Thực tế" : "Dự báo",
            ]}
          />

          {/* Đường ranh giới giữa lịch sử và dự báo */}
          {bridgeLabel && (
            <ReferenceLine
              x={bridgeLabel}
              stroke="var(--border)"
              strokeDasharray="4 4"
              label={{ value: "Hiện tại", fontSize: 9, fill: "var(--muted-foreground)" }}
            />
          )}

          {/* Nét liền — lịch sử */}
          <Line
            type="monotone"
            dataKey="historical"
            stroke="#6366f1"
            strokeWidth={2}
            dot={{ r: 3, fill: "#6366f1" }}
            activeDot={{ r: 5 }}
            connectNulls={false}
            name="historical"
          />

          {/* Nét đứt — dự báo */}
          <Line
            type="monotone"
            dataKey="forecast"
            stroke="#a855f7"
            strokeWidth={2}
            strokeDasharray="5 5"
            dot={{ r: 3, fill: "#a855f7" }}
            activeDot={{ r: 5 }}
            connectNulls={false}
            name="forecast"
          />
        </LineChart>
      </ResponsiveContainer>

      <p className="mt-2 text-[10px] text-muted-foreground text-center">
        * Dự báo dựa trên hồi quy tuyến tính (OLS) — 12 tháng lịch sử.
        Kết quả cập nhật ngày 1 hàng tháng.
      </p>
    </div>
  );
}
```

---

### F5 — Cập nhật `trends.tsx`

**File:** `src/routes/trends.tsx`  
**Thêm vào đầu file** (imports):

```typescript
import { useState } from "react";
import { TrendingUp } from "lucide-react";
import { useForecastList, useForecastDetail } from "@/hooks/data/use-forecast";
import { HotTopicForecastCard } from "@/components/HotTopicForecastCard";
import { ForecastLineChart } from "@/components/ForecastLineChart";
```

**Thêm vào trong function `TrendsPage()`** (sau các hook hiện có):

```typescript
const [selectedForecastId, setSelectedForecastId] = useState<number | null>(null);
const { data: forecastList = [], isLoading: loadingForecast } = useForecastList(10);
const { data: forecastDetail } = useForecastDetail(selectedForecastId);
```

**Thêm JSX vào trong return** (đặt trước hoặc sau section biểu đồ lịch sử hiện có):

```tsx
{/* ── SECTION: Dự báo Hot Topic 6 tháng tới ── */}
<div className="mb-6">
  <Card title="Dự Báo Hot Topic — 6 Tháng Tới">
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">

      {/* Bảng xếp hạng */}
      <div>
        <p className="text-xs text-muted-foreground mb-3">
          Xếp hạng theo điểm sTPS. Click vào keyword để xem biểu đồ dự báo.
        </p>
        <HotTopicForecastCard
          items={forecastList}
          isLoading={loadingForecast}
          onSelect={setSelectedForecastId}
        />
      </div>

      {/* Biểu đồ chi tiết */}
      <div>
        {forecastDetail ? (
          <>
            <p className="text-sm font-medium text-foreground mb-1">
              {forecastDetail.term}
            </p>
            <p className="text-xs text-muted-foreground mb-3">
              {forecastDetail.domain} · sTPS {forecastDetail.potentialScore}/100
              · {forecastDetail.forecastReason}
            </p>
            <ForecastLineChart detail={forecastDetail} />
          </>
        ) : (
          <div className="h-[300px] flex items-center justify-center text-sm text-muted-foreground">
            <div className="text-center">
              <TrendingUp className="size-8 mx-auto mb-2 opacity-30" />
              <p>Chọn một keyword để xem biểu đồ dự báo</p>
            </div>
          </div>
        )}
      </div>

    </div>
  </Card>
</div>
```

---

## Checklist Triển Khai

### Backend
- [ ] B1 — Chạy SQL tạo bảng `future_trend_forecasts`
- [ ] B2 — Tạo `FutureTrendForecast.java`
- [ ] B3 — Tạo `FutureTrendForecastRepository.java`
- [ ] B4 — Thêm `findHistoryByKeywordId` vào `PublicationTrendRepository.java`
- [ ] B5 — Thêm 6 config field vào `AppProperties.Sync`
- [ ] B6 — Tạo 3 DTO: `ForecastMonthDto`, `ForecastListResponse`, `ForecastDetailResponse`
- [ ] B7 — Tạo `FutureTrendForecastService.java` (interface)
- [ ] B8 — Tạo `FutureTrendForecastServiceImpl.java`
- [ ] B9 — Tạo `FutureTrendForecastScheduler.java`
- [ ] B10 — Thêm 2 endpoint vào `TrendController.java`
- [ ] Kiểm tra: `mvn spring-boot:run` không có lỗi compile
- [ ] Test thủ công: gọi API `GET /api/v1/trends/forecast` trả về `[]` (chưa có data)
- [ ] Test thủ công: trigger job thủ công bằng cách gọi `forecastService.runForecastJob()` qua một admin endpoint tạm
- [ ] Test thủ công: gọi lại API → có dữ liệu trả về

### Frontend
- [ ] F1 — Tạo `src/types/forecast.ts`
- [ ] F2 — Tạo `src/hooks/data/use-forecast.ts`
- [ ] F3 — Tạo `src/components/HotTopicForecastCard.tsx`
- [ ] F4 — Tạo `src/components/ForecastLineChart.tsx`
- [ ] F5 — Cập nhật `src/routes/trends.tsx`
- [ ] Kiểm tra: `npm run dev` không có lỗi TypeScript
- [ ] Kiểm tra UI: bảng xếp hạng hiển thị đúng badge màu
- [ ] Kiểm tra UI: click vào keyword → biểu đồ hiện ra
- [ ] Kiểm tra UI: nét liền (lịch sử) và nét đứt (dự báo) phân biệt rõ

---

## Lưu Ý Quan Trọng

| Vấn đề | Giải pháp trong plan này |
|--------|--------------------------|
| `deleteAll()` gây downtime | Dùng `deleteByCalculatedAtBefore(jobStart)` — xóa sau khi tính xong |
| N+1 query khi lưu | `forecastRepository.saveAll(batch)` — 1 lần duy nhất |
| ObjectMapper inject | Spring Boot tự tạo bean ObjectMapper — inject bình thường |
| Scheduler không chạy local | Set `app.scheduler-enabled=true` trong application.yml |
| Thiếu data tháng đầu | Job cần ít nhất `forecastMinMonths=6` bản ghi trong `publication_trends` |

---

*Cập nhật: 2026-06-29*
