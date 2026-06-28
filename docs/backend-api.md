# Backend API Endpoints

## Auth quy ước

- `PUBLIC` — không cần token
- `AUTH` — cần Bearer token (bất kỳ role)
- `ADMIN` — cần role ADMIN hoặc SUPER_ADMIN
- `SUPER_ADMIN` — chỉ SUPER_ADMIN

---

## Helix API (`/api/**`) — Internal, ẩn khỏi Swagger

### Auth `/api/auth`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/api/auth/login` | PUBLIC | Đăng nhập → `HelixAuthSession` (user + accessToken + refreshToken) |
| POST | `/api/auth/register` | PUBLIC | Đăng ký tài khoản mới |
| POST | `/api/auth/logout` | PUBLIC | Revoke refresh token |
| POST | `/api/auth/forgot-password` | PUBLIC | Gửi email reset password |
| POST | `/api/auth/reset-password` | PUBLIC | Đặt lại password bằng token email |
| GET | `/api/auth/me` | AUTH | Lấy thông tin user hiện tại |
| PUT | `/api/auth/profile` | AUTH | Cập nhật tên hiển thị |

### Papers `/api/papers`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/papers` | PUBLIC | Danh sách paper (filter: category, q, topicId, excludeId, limit) |
| GET | `/api/papers/{id}` | PUBLIC | Chi tiết paper theo ID (404 nếu không tồn tại) |
| GET | `/api/papers/{id}/references` | PUBLIC | References graph (lazy-fetch, cache 7 ngày, limit default 50) |
| GET | `/api/papers/{id}/citations` | PUBLIC | Citation graph (real-time OpenAlex, sort: citations/recent, yearFrom/To, limit max 100) |

### Topics/Keywords `/api/topics`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/topics` | PUBLIC | Danh sách keywords/topics |
| GET | `/api/topics/{id}` | PUBLIC | Chi tiết topic |
| GET | `/api/topics/domain/{domain}` | PUBLIC | Topics theo domain |

### Authors `/api/authors`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/authors` | PUBLIC | Danh sách authors |
| GET | `/api/authors/{authorId}` | PUBLIC | Chi tiết author + papers |

### Journals `/api/journals`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/journals` | PUBLIC | Danh sách journals |

### Analytics `/api/analytics`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/analytics/snapshot` | PUBLIC | Dashboard analytics snapshot đầy đủ (KPIs, velocity, heatmap, trending) |

### Collections `/api/collections`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/collections` | AUTH | Danh sách collections của user |
| POST | `/api/collections` | AUTH | Tạo collection mới |
| GET | `/api/collections/{id}` | AUTH | Chi tiết collection |
| DELETE | `/api/collections/{id}` | AUTH | Xóa collection |
| POST | `/api/collections/save` | AUTH | Lưu paper vào collection(s) |
| DELETE | `/api/collections/remove` | AUTH | Xóa paper khỏi collection |

### Notifications `/api/notifications`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/notifications` | AUTH | Danh sách notifications (phân trang theo groupId) |
| PUT | `/api/notifications/{id}/read` | AUTH | Đánh dấu đã đọc |

### Follow `/api/follow`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST/DELETE | `/api/follow/keywords/{id}` | AUTH | Follow/unfollow keyword (max 20/user) |
| POST/DELETE | `/api/follow/journals/{id}` | AUTH | Follow/unfollow journal (max 10/user) |
| POST/DELETE | `/api/follow/authors/{id}` | AUTH | Follow/unfollow author (max 20/user) |

### Admin `/api/admin`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/admin/overview` | ADMIN | Audit logs + pending review papers + anomalies |
| POST | `/api/admin/sync` | ADMIN | Trigger manual sync |
| GET | `/api/admin/sync/status` | ADMIN | Trạng thái sync gần nhất |
| POST | `/api/admin/sync/reset-stale` | ADMIN | Reset sync bị RUNNING quá lâu |
| POST | `/api/admin/trends/recalculate` | ADMIN | Recalculate tất cả trend scores + backfill |
| POST | `/api/admin/trends/backfill` | ADMIN | Backfill trend lịch sử (`?months=12`) |
| GET | `/api/admin/trends/demo-stats` | ADMIN | Stats demo cho admin panel |
| POST | `/api/admin/papers/repair-metadata` | ADMIN | Re-fetch metadata thiếu từ OpenAlex (`?limit=50`) |
| POST | `/api/admin/papers/review/expire-stale` | ADMIN | Expire pending reviews quá hạn |
| GET | `/api/admin/sources` | ADMIN | Danh sách API sources (OpenAlex config) |
| PATCH | `/api/admin/sources/{name}` | ADMIN | Bật/tắt API source |

### Reports `/api/reports`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/reports/export` | AUTH | Export báo cáo |

---

## Public API v1 (`/api/v1/**`) — Có Swagger docs

### Auth `/api/v1/auth`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/api/v1/auth/register` | PUBLIC | Đăng ký (có email verification) |
| POST | `/api/v1/auth/login` | PUBLIC | Đăng nhập |
| POST | `/api/v1/auth/refresh` | PUBLIC | Refresh access token |
| POST | `/api/v1/auth/logout` | PUBLIC | Revoke refresh token |
| POST | `/api/v1/auth/forgot-password` | PUBLIC | Gửi email reset |
| POST | `/api/v1/auth/reset-password` | PUBLIC | Reset password |
| GET | `/api/v1/auth/me` | AUTH | Thông tin user |
| PUT | `/api/v1/auth/profile` | AUTH | Cập nhật profile |
| POST | `/api/v1/auth/change-password` | AUTH | Đổi password |

### Papers `/api/v1/papers`

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/v1/papers` | PUBLIC | Phân trang (page, size, sort, q, keyword) |
| GET | `/api/v1/papers/{id}` | PUBLIC | Chi tiết paper |

### Other v1 endpoints

| Controller | Base path | Auth |
|---|---|---|
| `KeywordController` | `/api/v1/keywords` | PUBLIC GET |
| `TrendController` | `/api/v1/trends` | PUBLIC GET |
| `AuthorController` | `/api/v1/authors` | PUBLIC GET |
| `CollectionController` | `/api/v1/collections` | AUTH |
| `DashboardController` | `/api/v1/dashboard` | PUBLIC GET |
| `NotificationController` | `/api/v1/notifications` | AUTH |
| `FollowKeywordController` | `/api/v1/follow/keywords` | AUTH |
| `FollowAuthorController` | `/api/v1/follow/authors` | AUTH |
| `AdminController` | `/api/v1/admin` | ADMIN |
| `SuperAdminController` | `/api/v1/super-admin` | SUPER_ADMIN |
| `EmailVerificationController` | `/auth/verify` | PUBLIC |

### Misc

| Path | Auth | Mô tả |
|---|---|---|
| `/health` | PUBLIC | Health check |
| `/swagger-ui/**` | PUBLIC | Swagger UI |
| `/v3/api-docs/**` | PUBLIC | OpenAPI spec |
