# Backend Security

## Filter chain (theo thứ tự)

```
Request → RateLimitFilter → JwtAuthenticationFilter → Spring Security authorizeHttpRequests
```

1. **`RateLimitFilter`** (Bucket4j): 60 req/min/IP. Trả 429 nếu vượt.
2. **`JwtAuthenticationFilter`**: Extract Bearer token → `JwtTokenProvider.validateToken()` → set `SecurityContext`.
3. **`SecurityConfig.authorizeHttpRequests`**: Kiểm tra path + role.

---

## Endpoint access matrix

| Pattern | Phương thức | Quyền |
|---|---|---|
| `/health` | GET | PUBLIC |
| `/auth/verify` | GET | PUBLIC |
| `/api/v1/auth/**` | * | PUBLIC |
| `/api/auth/**` | * | PUBLIC |
| `/api/analytics/**`, `/api/papers/**`, `/api/v1/papers/**`, `/api/topics/**`, `/api/authors/**`, `/api/journals/**`, `/api/v1/dashboard/**` | GET | PUBLIC |
| `/swagger-ui/**`, `/v3/api-docs/**` | GET | PUBLIC |
| `/api/v1/super-admin/**` | * | SUPER_ADMIN |
| `/api/v1/admin/**`, `/api/admin/**` | * | ADMIN hoặc SUPER_ADMIN |
| `/**` OPTIONS | OPTIONS | PUBLIC (preflight CORS) |
| Tất cả còn lại | * | AUTH (authenticated) |

**Lưu ý:** `anonymous()` bị disable — request không có token và không thuộc PUBLIC sẽ nhận 401, không phải truy cập ẩn danh.

---

## JWT flow

```
1. POST /api/auth/login { email, password }
   → Trả { user, accessToken (15 phút), refreshToken (7 ngày) }

2. Client gửi: Authorization: Bearer <accessToken>

3. Khi access token hết hạn:
   POST /api/v1/auth/refresh { refreshToken }
   → Trả { accessToken mới }

4. POST /api/auth/logout { refreshToken }
   → refreshToken bị revoke trong DB
```

### Token signing

- Access token: HS256 với `app.jwt.access-secret`
- Refresh token: HS256 với `app.jwt.refresh-secret` (key riêng tách biệt)
- `JwtTokenProvider` xử lý sign + validate

---

## Role hierarchy

| Role | Mô tả |
|---|---|
| `STUDENT` | User thường — đọc paper, bookmark, follow |
| `LECTURER` | Tương tự STUDENT (không có quyền thêm trong code hiện tại) |
| `RESEARCHER` | Tương tự LECTURER |
| `ADMIN` | Quản lý user, trigger sync, duyệt paper |
| `SUPER_ADMIN` | Toàn quyền + `/api/v1/super-admin/**` |

`@PreAuthorize` được dùng ở controller level. `@EnableMethodSecurity` bật trong `SecurityConfig`.

---

## CORS

- Allowed origins: từ `app.cors-allowed-origins` (comma-separated)
- Nếu không cấu hình: fallback `allowedOriginPatterns("*")`
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- `allowCredentials: true`

---

## Error responses

Lỗi auth trả về format `ApiResponse<Void>`:

```json
{
  "success": false,
  "message": "Please sign in",
  "timestamp": "2026-06-28T..."
}
```

- 401: token thiếu / không hợp lệ / hết hạn
- 403: thiếu role
- 429: vượt rate limit
