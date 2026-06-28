# Backend Config Reference

Tất cả config được đọc qua `AppProperties` với prefix `app.*` trong `application.yml`.

---

## Top-level

| Property | Default | Mô tả |
|---|---|---|
| `app.cors-allowed-origins` | `http://localhost:5173,http://localhost:5174` | Danh sách origin CORS |
| `app.frontend-base-url` | — | URL frontend (dùng trong email links) |
| `app.backend-base-url` | `http://localhost:8080` | URL backend |
| `app.password-reset-expiration-minutes` | — | Hạn token reset password |
| `app.email-verification-expiration-minutes` | `1440` (24h) | Hạn token verify email |
| `app.rate-limit-per-minute` | `60` | Bucket4j rate limit per IP/phút |
| `app.scheduler-enabled` | `true` | Tắt/bật scheduler sync |

---

## JWT (`app.jwt.*`)

| Property | Default | Mô tả |
|---|---|---|
| `app.jwt.access-secret` | **bắt buộc** | Signing key cho access token |
| `app.jwt.refresh-secret` | **bắt buộc** | Signing key cho refresh token |
| `app.jwt.access-expiration-ms` | `900000` (15 phút) | TTL access token |
| `app.jwt.refresh-expiration-ms` | `604800000` (7 ngày) | TTL refresh token |

---

## OpenAlex (`app.openalex.*`)

| Property | Default | Mô tả |
|---|---|---|
| `app.openalex.base-url` | `https://api.openalex.org` | Base URL |
| `app.openalex.mailto` | — | Email để vào polite pool (khuyến nghị đặt) |
| `app.openalex.api-key` | — | API key nếu có (không bắt buộc) |
| `app.openalex.per-page` | `50` | Số kết quả mỗi trang |

---

## Sync (`app.sync.*`)

| Property | Default | Mô tả |
|---|---|---|
| `app.sync.cron` | `0 0 2 * * *` | Cron expression (2 AM hàng ngày) |
| `app.sync.on-startup` | `true` | Chạy sync ngay khi app start |
| `app.sync.search-queries` | `["computer science", "machine learning", "artificial intelligence", "data science"]` | Từ khóa tìm kiếm OpenAlex |
| `app.sync.max-pages` | `5` | Trang tối đa mỗi query |
| `app.sync.max-papers-per-run` | `1000` | Cap tổng paper mỗi lần sync |
| `app.sync.from-publication-date` | `2026-01-01` | Chỉ lấy paper xuất bản từ ngày này trở đi |
| `app.sync.ingest-batch-size` | `25` | Số paper commit mỗi DB transaction |
| `app.sync.overlap-days` | `7` | Overlap ngày để sync an toàn (incremental) |
| `app.sync.early-stopping-enabled` | `true` | Dừng sớm khi không có paper mới |
| `app.sync.early-stop-consecutive-empty-pages` | `3` | N trang liên tiếp trống → dừng |
| `app.sync.stale-sync-minutes` | `10` | Sau bao nhiêu phút RUNNING thì coi là stale |
| `app.sync.http-connect-timeout-ms` | `10000` | Connect timeout HTTP |
| `app.sync.http-read-timeout-ms` | `30000` | Read timeout HTTP |
| `app.sync.open-alex-retry-attempts` | `3` | Số lần retry mỗi HTTP call |
| `app.sync.enrich-on-sync` | `false` | Re-fetch metadata thiếu sau sync |
| `app.sync.enrich-batch-size` | `20` | Số paper enrich mỗi batch |
| `app.sync.enrich-delay-ms` | `50` | Delay giữa các enrich calls |

### Trend calculation

| Property | Default | Mô tả |
|---|---|---|
| `app.sync.min-keyword-papers` | `5` | Số paper tối thiểu để keyword được trending |
| `app.sync.trending-threshold-percent` | `15` | % tăng trưởng để là trending (BR-04) |
| `app.sync.trending-consecutive-months` | `3` | Số tháng liên tiếp phải đạt ngưỡng |
| `app.sync.trend-backfill-months` | `12` | Số tháng lịch sử backfill sau recalculate |
| `app.sync.anomaly-threshold-percent` | `300` | % tăng để gắn nhãn Anomaly (BR-50) |

### Business rules

| Property | Default | Mô tả |
|---|---|---|
| `app.sync.max-follow-keywords-per-user` | `20` | BR-55 |
| `app.sync.max-follow-journals-per-user` | `10` | BR-56 |
| `app.sync.max-follow-authors-per-user` | `20` | |
| `app.sync.max-bookmark-papers-per-user` | `200` | BR-57 |
| `app.sync.pending-review-expiry-days` | `30` | BR-97: paper pending quá hạn → EXPIRED |

---

## Env vars thường dùng khi chạy

```env
DB_URL=localhost:1433
DB_USERNAME=sa
DB_PASSWORD=...
APP_JWT_ACCESS_SECRET=...
APP_JWT_REFRESH_SECRET=...
APP_OPENALEX_MAILTO=email@example.com
MAIL_USERNAME=gmail@gmail.com
MAIL_PASSWORD=app-password
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_SCHEDULER_ENABLED=true
```
