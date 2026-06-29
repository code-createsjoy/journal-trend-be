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

    /** Trả về top N keyword có điểm sTPS cao nhất. */
    List<ForecastListResponse> getTopForecasts(int limit);

    /** Trả về chi tiết dự báo 1 keyword kèm lịch sử + 6 tháng tới. */
    ForecastDetailResponse getForecastDetail(Long keywordId);
}
