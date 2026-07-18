package com.norman.swp391.dto.request.role;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ghi chú tùy chọn của Admin/Super Admin khi duyệt đơn. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRequestApproveRequest {

    @Size(max = 1000, message = "Note is too long")
    private String note;
}
