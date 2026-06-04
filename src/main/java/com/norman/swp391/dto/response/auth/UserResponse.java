package com.norman.swp391.dto.response.auth;

import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin user.
 */
@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


