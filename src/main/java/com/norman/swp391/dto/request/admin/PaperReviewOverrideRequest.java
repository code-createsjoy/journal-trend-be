package com.norman.swp391.dto.request.admin;

import lombok.Data;

/** BR-104: ghi đè metadata từ nguồn conflict. */
@Data
public class PaperReviewOverrideRequest {
    private String title;
    private String abstractText;
    private String note;
}
