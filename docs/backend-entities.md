# Backend Entities & DB Schema

SQL Server, Hibernate auto-DDL. Tất cả entity kế thừa từ `BaseAuditEntity` (createdAt, updatedAt) trừ các entity đặc biệt.

---

## Core entities

### `Paper` (bảng `papers`)

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `id` | Long PK | IDENTITY |
| `title` | NVARCHAR(1000) | `@Nationalized` |
| `abstractText` | NVARCHAR(MAX) | |
| `publicationDate` | LocalDate | |
| `citationCount` | int | |
| `doi` | NVARCHAR(255) UNIQUE | |
| `sourceType` | VARCHAR(50) | Luôn "OPENALEX" |
| `sourceIdentifier` | VARCHAR(100) | OpenAlex ID dạng "W1234…" |
| `sourceUrl` | VARCHAR(500) | |
| `pdfUrl` | VARCHAR(500) | |
| `openAccess` | boolean | |
| `journal` | NVARCHAR(500) | tên journal dạng text |
| `journalRef` | FK → Journal | nullable |
| `status` | enum PaperStatus | PUBLISHED / DRAFT |
| `reviewStatus` | enum PaperReviewStatus | NONE / PENDING / APPROVED / REJECTED / EXPIRED |
| `reviewFlaggedAt` | LocalDateTime | |
| `conflictTitle/Abstract/Source` | VARCHAR | Dữ liệu xung đột từ sync |

**Indexes:** `(source_type, source_identifier)` unique dedup, `(doi)`, `(source_identifier)`

### `Keyword` (bảng `keywords`)

- `term` NVARCHAR UNIQUE, `category` (domain từ OpenAlex)
- Liên kết với papers qua `paper_keywords` join table

### `PublicationTrend` (bảng `publication_trends`)

| Trường | Ghi chú |
|---|---|
| `keywordId` FK | |
| `trendYear` / `trendMonth` | năm/tháng |
| `paperCount` | số paper trong tháng đó |
| `trendScore` | % tăng trưởng so với tháng trước |
| `isTrending` | true khi score ≥ 15% trong 3 tháng liên tiếp |

**Unique index:** `(keyword_id, trend_year, trend_month)` — dùng cho upsert

### `Author` (bảng `authors`)

- `name`, `openAlexId` UNIQUE, `affiliation`, `citedByCount`, `worksCount`, `hIndex`
- Liên kết với papers qua `paper_authors` join table

### `Journal` (bảng `journals`)

- `name`, `publisher`, `issn`, `domain`, `impactFactor`
- Papers liên kết qua `journalRef` FK

---

## User & Auth entities

### `User` (bảng `users`)

- `email` UNIQUE, `passwordHash`, `fullName`
- `role` enum UserRole: STUDENT / LECTURER / RESEARCHER / ADMIN / SUPER_ADMIN
- `status` enum UserStatus: ACTIVE / BANNED / PENDING_VERIFICATION
- `emailVerified` boolean

### `RefreshToken` (bảng `refresh_tokens`)

- `token` UNIQUE, `userId` FK, `expiresAt`, `revoked`

### `EmailVerificationToken` / `PasswordResetToken`

- Token + expiresAt, liên kết với User

---

## Reference & Citation entities

### `PaperReference` (bảng `paper_references`)

| Trường | Ghi chú |
|---|---|
| `paperId` FK | paper gốc |
| `referencedOpenAlexId` | OpenAlex ID của paper được cite |
| `fetchedAt` | thời điểm fetch từ OpenAlex |

**Unique constraint:** `(paper_id, referenced_openalex_id)` — ngăn duplicate khi concurrent insert

### `ReferenceMetadata` (bảng `reference_metadata`)

| Trường | Ghi chú |
|---|---|
| `openAlexId` UNIQUE | OpenAlex ID của referenced work |
| `title` | |
| `publicationYear` | |
| `doi` | |
| `citationCount` | |
| `localPaperId` | FK → Paper nếu work đó có trong DB local |
| `fetchedAt` | Anchor cho TTL (7 ngày) |

---

## Feature entities

### `PaperCollection` / `CollectionPaper`

- User tạo collection, paper được lưu vào collection
- Giới hạn: 200 paper/user (BR-57)

### `FollowKeyword` / `FollowJournal` / `FollowAuthor`

- `userId` + target FK, `followedAt`
- Giới hạn: keyword 20, journal 10, author 20 (BR-55/56)

### `Notification`

- `userId`, `type` (NotificationTriggerType), `title`, `body`, `readStatus`

### `SyncLog`

- `status` (RUNNING/SUCCESS/FAILED), `startedAt`, `finishedAt`, `papersFetched`, `errorMessage`

### `ApiSourceConfig`

- `name`, `baseUrl`, `enabled`, `syncSchedule`, `lastSyncAt`, `successRate`

### `PaperReviewAudit`

- Lịch sử hành động duyệt: actor, action (APPROVED/REJECTED/…), target paperId, timestamp

### `KeywordSyncState`

- Theo dõi trạng thái sync từng keyword (last synced page, last synced date)

---

## Join tables (không phải entity, managed bởi JPA)

| Bảng | Mô tả |
|---|---|
| `paper_keywords` | Paper ↔ Keyword |
| `paper_authors` | Paper ↔ Author |
| `collection_papers` | Collection ↔ Paper |
