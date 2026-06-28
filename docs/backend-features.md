# Backend Features

## 1. Sync Pipeline

`DataSyncScheduler` → `PaperSyncService` → `OpenAlexClient` → DB

### Trigger

- **Cron:** `0 0 2 * * *` (2 AM hàng ngày) — kiểm tra `app.scheduler-enabled`
- **On startup:** nếu `app.sync.on-startup = true`
- **Manual:** `POST /api/admin/sync`

### Flow chi tiết

```
1. Tạo SyncLog(RUNNING)
2. Với mỗi search query trong app.sync.search-queries:
   a. Gọi openAlexClient.fetchWorks(query, page, fromPublicationDate)
   b. Với mỗi paper:
      - Dedup theo (source_type, source_identifier) — bỏ qua nếu đã có
      - Map ExternalPaperMetadata → Paper entity
      - Upsert Authors, Keywords, Journal
      - Batch commit mỗi ingestBatchSize (default 25)
   c. Early stopping: dừng nếu earlyStopConsecutiveEmptyPages trang liên tiếp trống
   d. Dừng nếu đạt maxPapersPerRun
3. Cập nhật SyncLog(SUCCESS/FAILED) + papersFetched
4. Gọi keywordTrendService.recalculateAll() sau sync
5. Backfill trend lịch sử nếu trendBackfillMonths > 0
```

### Stale sync protection

Nếu SyncLog còn RUNNING quá `staleSyncMinutes` phút: `POST /api/admin/sync/reset-stale` đánh dấu FAILED và unlock.

---

## 2. Trend Calculation

### Công thức (BR-02)

```
TrendScore = (currentMonthCount - prevMonthCount) / prevMonthCount × 100%
```

Lưu vào `publication_trends(keyword_id, trend_year, trend_month)` — upsert dựa vào unique index.

### Điều kiện "trending" (BR-04)

- `TrendScore ≥ 15%` (cấu hình `trending-threshold-percent`)
- Trong `3 tháng liên tiếp` (`trending-consecutive-months`)
- Keyword phải có ít nhất `5 paper` (`min-keyword-papers`)

### Anomaly (BR-50)

`TrendScore ≥ 300%` (`anomaly-threshold-percent`) → gắn nhãn Anomaly trong admin overview.

### Trigger

- Tự động sau mỗi sync
- Manual: `POST /api/admin/trends/recalculate`
- Backfill lịch sử: `POST /api/admin/trends/backfill?months=12`

---

## 3. Reference Graph (Lazy Cache)

`PaperReferenceService.getReferences(paperId, limit)` — `GET /api/papers/{id}/references`

### Flow

```
1. Lookup paper_references WHERE paper_id = ?
2. Nếu rỗng + paper có sourceIdentifier:
   → openAlexClient.extractReferencedWorkIds(openAlexId)
   → Lưu vào paper_references (transaction REQUIRES_NEW)
3. Batch lookup reference_metadata WHERE openAlexId IN (...) AND fetchedAt > NOW()-7d
   → cachedMap
4. Với các ID còn thiếu (stale hoặc chưa có):
   → openAlexClient.fetchWorksByIds(missingIds) — batch 50 IDs/request
   → UPSERT vào reference_metadata:
      * Entry stale: update fields + fetchedAt = now
      * Entry mới: insert
   → Lưu trong transaction REQUIRES_NEW
   → Nếu constraint violation: fallback reload từ DB
5. Cross-reference với bảng papers (theo sourceIdentifier):
   → Gán localPaperId nếu referenced work có trong DB
6. Build List<HelixReferenceNode>
```

### TTL

7 ngày — cache stale được refresh tự động ở step 4.

### Concurrency

`REQUIRES_NEW` transaction template để tránh constraint violation khi nhiều request đồng thời insert cùng reference.

---

## 4. Citation Graph (Real-time)

`PaperReferenceService.getCitations(paperId, sort, yearFrom, yearTo, limit)` — `GET /api/papers/{id}/citations`

**Không có cache** — luôn query real-time từ OpenAlex.

```
openAlexClient.fetchCitingWorks(openAlexId, sort, yearFrom, yearTo, limit)
```

Sort: `"citations"` → `cited_by_count:desc`, `"recent"` → `publication_date:desc`.
Limit capped tại 100.

Cross-reference với bảng papers local → gán `localPaperId` / `existsLocally`.

---

## 5. Paper Review Workflow

Papers bị conflict (trùng title/abstract từ nguồn khác) được gắn `reviewStatus = PENDING`.

| Action | Endpoint | Auth |
|---|---|---|
| Xem danh sách pending | `GET /api/admin/overview` | ADMIN |
| Duyệt/từ chối | `PaperReviewService` (qua HelixAdminController) | ADMIN |
| Expire stale | `POST /api/admin/papers/review/expire-stale` | ADMIN |

Papers pending quá `pendingReviewExpiryDays` ngày (default 30) → tự động EXPIRED.

---

## 6. Notification System

Khi user follow keyword/author/journal:
- Mỗi lần sync có paper mới liên quan → tạo `Notification`
- User đọc qua `GET /api/notifications`
- Đánh dấu đã đọc: `PUT /api/notifications/{id}/read`

---

## 7. Metadata Repair

`PaperMetadataRepairService.repairFromOpenAlex(limit)` — `POST /api/admin/papers/repair-metadata?limit=50`

Tìm papers thiếu `publicationDate` hoặc `abstractText` → re-fetch từ OpenAlex theo DOI/sourceIdentifier → update tại chỗ.
