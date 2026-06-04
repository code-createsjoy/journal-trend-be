package com.norman.swp391.security;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tạo và xác thực JWT access/refresh.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final AppProperties appProperties;

/**
 * Tạo hoặc lưu: createAccessToken.
 */
    public String createAccessToken(Long userId, String email, UserRole role) {
        return buildToken(userId, email, role, TYPE_ACCESS, appProperties.getJwt().getAccessSecret(),
                appProperties.getJwt().getAccessExpirationMs());
    }

/**
 * Tạo hoặc lưu: createRefreshToken.
 */
    public String createRefreshToken(Long userId, String email, UserRole role) {
        return buildToken(userId, email, role, TYPE_REFRESH, appProperties.getJwt().getRefreshSecret(),
                appProperties.getJwt().getRefreshExpirationMs());
    }

/**
 * Xác thực: validateAccessToken.
 */
    public boolean validateAccessToken(String token) {
        return validateToken(token, appProperties.getJwt().getAccessSecret(), TYPE_ACCESS);
    }

/**
 * Xác thực: validateRefreshToken.
 */
    public boolean validateRefreshToken(String token) {
        return validateToken(token, appProperties.getJwt().getRefreshSecret(), TYPE_REFRESH);
    }

/**
 * Lấy dữ liệu: getUserIdFromAccessToken.
 */
    public Long getUserIdFromAccessToken(String token) {
        return getUserId(token, appProperties.getJwt().getAccessSecret(), TYPE_ACCESS);
    }

/**
 * Xử lý nghiệp vụ: extractUserId.
 */
    public Long extractUserId(Claims claims) {
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        return userId != null ? userId.longValue() : null;
    }

/**
 * Lấy dữ liệu: getRoleFromAccessToken.
 */
    public UserRole getRoleFromAccessToken(String token) {
        Claims claims = parseClaims(token, appProperties.getJwt().getAccessSecret());
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

/**
 * Lấy dữ liệu: getAccessExpirationMs.
 */
    public long getAccessExpirationMs() {
        return appProperties.getJwt().getAccessExpirationMs();
    }

/**
 * Lấy dữ liệu: getRefreshExpirationMs.
 */
    public long getRefreshExpirationMs() {
        return appProperties.getJwt().getRefreshExpirationMs();
    }

/**
 * Tạo/ghép dữ liệu: buildToken.
 */
    private String buildToken(Long userId, String email, UserRole role, String type, String secret, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey(secret))
                .compact();
    }

/**
 * Xác thực: validateToken.
 */
    private boolean validateToken(String token, String secret, String expectedType) {
        try {
            Claims claims = parseClaims(token, secret);
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return expectedType.equals(type);
        } catch (JwtException ex) {
            return false;
        }
    }

/**
 * Lấy dữ liệu: getUserId.
 */
    private Long getUserId(String token, String secret, String expectedType) {
        Claims claims = parseClaims(token, secret);
        String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("Invalid token type");
        }
        Long userId = extractUserId(claims);
        if (userId == null) {
            throw new JwtException("Missing user id claim");
        }
        return userId;
    }

/**
 * Xử lý nghiệp vụ: parseClaims.
 */
    private Claims parseClaims(String token, String secret) {
        return Jwts.parser()
                .verifyWith(signingKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

/**
 * Xử lý nghiệp vụ: signingKey.
 */
    private SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
