package com.norman.swp391.controller.helix;

import com.norman.swp391.dto.response.report.PersonalReportResponse;
import com.norman.swp391.service.PersonalReportService;
import com.norman.swp391.service.ReportExportService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Xuất CSV cho Helix admin.
 */
@Hidden
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'RESEARCHER', 'ADMIN', 'SUPER_ADMIN')")
public class HelixReportsController {

    private final ReportExportService reportExportService;
    private final PersonalReportService personalReportService;

    @GetMapping("/personal")
    public PersonalReportResponse getPersonalReport() {
        Long userId = com.norman.swp391.security.SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new com.norman.swp391.exception.BadRequestException("Not authenticated");
        }
        return personalReportService.generatePersonalReport(userId);
    }

    @GetMapping(value = "/topic-trends.csv", produces = "text/csv")
/**
 * Xử lý nghiệp vụ: exportTopicTrends.
 */
    public ResponseEntity<String> exportTopicTrends() {
        return csvResponse("topic-trends.csv", reportExportService.exportTopicTrendsCsv());
    }

    @GetMapping(value = "/papers.csv", produces = "text/csv")
    public ResponseEntity<String> exportPapers(@RequestParam(defaultValue = "500") int limit) {
        return csvResponse("papers.csv", reportExportService.exportPapersCsv(limit));
    }

/**
 * Xử lý nghiệp vụ: csvResponse.
 */
    private ResponseEntity<String> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }
}
