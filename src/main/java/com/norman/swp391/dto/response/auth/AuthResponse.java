package com.norman.swp391.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phản hồi auth.
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UserResponse user;
}


