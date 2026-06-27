package com.norman.swp391.service;

import com.norman.swp391.dto.response.report.PersonalReportResponse;

public interface PersonalReportService {
    PersonalReportResponse generatePersonalReport(Long userId);
}
