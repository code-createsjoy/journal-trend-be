package com.norman.swp391.service;

/**
 * Xuất báo cáo CSV cho admin / Helix.
 */
public interface ReportExportService {

/**
 * Xử lý nghiệp vụ: exportTopicTrendsCsv.
 */
    String exportTopicTrendsCsv();

/**
 * Xử lý nghiệp vụ: exportPapersCsv.
 */
    String exportPapersCsv(int limit);
}
