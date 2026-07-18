package com.norman.swp391.dto.response.role;

import com.norman.swp391.entity.enums.UserRole;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeLogResponse {

    private Long id;
    private Long operatorId;
    private String operatorName;
    private Long targetUserId;
    private String targetUserName;
    private UserRole oldRole;
    private UserRole newRole;
    private String reason;
    private LocalDateTime createdAt;
}
