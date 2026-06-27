package com.norman.swp391.service;

import com.norman.swp391.dto.helix.HelixDtos.HelixCitationNode;
import com.norman.swp391.dto.helix.HelixDtos.HelixReferenceNode;
import java.util.List;

/**
 * Service xử lý references & citation graph cho paper detail.
 */
public interface PaperReferenceService {

    /**
     * Lấy danh sách referenced works cho paper có id cho trước.
     * Lazy fetch từ OpenAlex nếu chưa có trong cache.
     *
     * @param paperId ID của paper trong DB local
     * @param limit   Số lượng tối đa nodes trả về (default 50)
     * @return Danh sách HelixReferenceNode
     */
    List<HelixReferenceNode> getReferences(Long paperId, int limit);

    /**
     * Lấy danh sách citing works (papers trích dẫn paper hiện tại).
     * Real-time query từ OpenAlex.
     *
     * @param paperId   ID của paper trong DB local
     * @param sort      "citations" (default) hoặc "recent"
     * @param yearFrom  Năm bắt đầu (nullable)
     * @param yearTo    Năm kết thúc (nullable)
     * @param limit     Số lượng tối đa nodes trả về (default 20)
     * @return Danh sách HelixCitationNode
     */
    List<HelixCitationNode> getCitations(Long paperId, String sort, Integer yearFrom, Integer yearTo, int limit);
}
