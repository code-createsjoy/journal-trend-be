package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.dto.response.auth.AuthResponse;
import com.norman.swp391.dto.response.auth.TokenResponse;
import com.norman.swp391.dto.response.auth.UserResponse;
import com.norman.swp391.entity.User;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Mapper UserMapper.
 */
@UtilityClass
public class UserMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toAdminResponse.
 */
    public static UserAdminResponse toAdminResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserAdminResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toAdminResponseList.
 */
    public static List<UserAdminResponse> toAdminResponseList(List<User> users) {
        return users.stream().map(UserMapper::toAdminResponse).toList();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toAuthResponse.
 */
    public static AuthResponse toAuthResponse(String accessToken, String refreshToken, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toResponse(user))
                .build();
    }

/**
 * Ánh xạ sang DTO/phản hồi: toTokenResponse.
 */
    public static TokenResponse toTokenResponse(String accessToken, String refreshToken) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}


