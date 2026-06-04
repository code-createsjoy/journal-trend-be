package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.TrendDemoStatsResponse;
import org.springframework.stereotype.Component;


public interface TrendDemoStatsService {
    TrendDemoStatsResponse getStats();
}
