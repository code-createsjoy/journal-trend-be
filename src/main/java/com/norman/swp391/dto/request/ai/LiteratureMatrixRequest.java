package com.norman.swp391.dto.request.ai;

import java.util.List;
import lombok.Data;

@Data
public class LiteratureMatrixRequest {

    /** Nếu truyền, lấy toàn bộ paper ACTIVE trong collection này (phải thuộc user hiện tại). */
    private Long collectionId;

    /**
     * Nếu collectionId cũng được truyền, paperIds thu hẹp về đúng các paper này trong collection
     * đó. Nếu collectionId để trống, paperIds được dùng trực tiếp làm danh sách paper cần phân tích.
     */
    private List<Long> paperIds;

    /**
     * Tùy chọn: chỉ định cột nào hiển thị trong markdownTable (ngoài Title/Authors/Year cố định).
     * Giá trị hợp lệ (không phân biệt hoa/thường): Objective, Methodology, Dataset, Performance
     * (alias của Key Results), Limitations. Để trống = hiển thị đủ tất cả cột.
     */
    private List<String> customColumns;
}
