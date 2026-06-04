package com.norman.swp391.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.common.ApiResponse;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn số request theo IP mỗi phút.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Kiểm tra rate limit theo IP; trả 429 nếu vượt ngưỡng.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, key -> newBucket());

        if (!bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = ApiResponse.<Void>builder()
                    .success(false)
                    .message("Rate limit exceeded. Try again later.")
                    .timestamp(LocalDateTime.now())
                    .build();
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Tạo bucket token cho một IP (giới hạn request/phút).
     */
    private Bucket newBucket() {
        int limit = appProperties.getRateLimitPerMinute();
        return Bucket.builder()
                .addLimit(b -> b.capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Lấy địa chỉ IP client (hỗ trợ header X-Forwarded-For).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
