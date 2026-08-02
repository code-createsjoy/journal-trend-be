package com.norman.swp391.service;

import com.norman.swp391.dto.request.ai.LiteratureMatrixRequest;
import com.norman.swp391.dto.response.ai.LiteratureMatrixResponse;

public interface LiteratureMatrixService {

    /**
     * Sinh ma trận so sánh tài liệu bằng AI (Groq) cho các paper trong 1 collection hoặc theo danh
     * sách paperId. USER bị giới hạn 10 lượt/ngày; ADMIN/SUPER_ADMIN không giới hạn.
     */
    LiteratureMatrixResponse generate(LiteratureMatrixRequest request);
}
