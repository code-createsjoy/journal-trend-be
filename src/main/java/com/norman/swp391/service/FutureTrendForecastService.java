package com.norman.swp391.service;

import com.norman.swp391.dto.response.keyword.ForecastDetailResponse;
import com.norman.swp391.dto.response.keyword.ForecastListResponse;
import java.util.List;

/**
 * Dự báo hot topic 6 tháng tới dựa trên hồi quy tuyến tính (OLS) lịch sử trend.
 */
public interface FutureTrendForecastService {

    /** Chạy toàn bộ pipeline tính toán và lưu vào DB. Gọi từ Scheduler. */
    void runForecastJob();

    /** Trả về top N keyword có điểm sTPS cao nhất, dự báo cắt theo {@code months} (1-12). */
    List<ForecastListResponse> getTopForecasts(int limit, int months);

    /** Trả về chi tiết dự báo 1 keyword kèm lịch sử + {@code months} tháng tới (1-12). */
    ForecastDetailResponse getForecastDetail(Long keywordId, int months);
}
