package com.norman.swp391.service;

import com.norman.swp391.dto.helix.HelixDtos.HelixCitationNode;
import com.norman.swp391.dto.helix.HelixDtos.HelixPaperGraph;
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

    /**
     * Gộp references và citations vào 1 response duy nhất.
     *
     * @param paperId    ID paper trong DB local
     * @param refLimit   Số references tối đa (default 50)
     * @param citLimit   Số citations tối đa (default 20)
     * @param sort       Sort cho citations: "citations" hoặc "recent"
     * @param yearFrom   Lọc citation từ năm (nullable)
     * @param yearTo     Lọc citation đến năm (nullable)
     */
    HelixPaperGraph getPaperGraph(Long paperId, int refLimit, int citLimit, String sort, Integer yearFrom, Integer yearTo);
}
