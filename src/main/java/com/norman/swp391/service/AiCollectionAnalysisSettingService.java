package com.norman.swp391.service;

public interface AiCollectionAnalysisSettingService {

    /** Cap số paper/lượt phân tích AI collection hiện tại (đọc từ DB, tự seed giá trị mặc định nếu chưa có). */
    int getMaxPapers();

    /** Admin đổi cap. Trả về giá trị mới sau khi lưu. */
    int updateMaxPapers(int newMaxPapers);
}
