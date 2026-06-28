# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Backend — JournalTrend (`journal-trend-be`)

Spring Boot 3.5.3 + Java 21 backend for the JournalTrend academic publication tracking system.

---

## Commands

```bash
# Run
mvn spring-boot:run

# Build JAR
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=AuthServiceImplTest

# Run a single test method
mvn test -Dtest=AuthServiceImplTest#testLogin
```

---

## Key Environment Variables

Set via `application.yml` or environment overrides:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | `localhost:1433` | SQL Server connection |
| `DB_USERNAME` / `DB_PASSWORD` | — | DB credentials |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | — | JWT signing keys |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | — | Gmail SMTP credentials |
| `OPENALEX_API_KEY` | — | OpenAlex email/key |
| `SCHEDULER_ENABLED` | `true` | Toggle background sync |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` | CORS |

---

## Architecture

**Layer pattern:** `Controller → Service interface → ServiceImpl → Repository → JPA Entity`

```
com.norman.swp391/
├─ config/          AppProperties.java holds all YAML config as typed beans (nested Jwt, OpenAlex, Sync classes)
├─ controller/
│   ├─ v1/          Public REST API (AuthController, PaperController, TrendController, etc.)
│   └─ helix/       Internal APIs hidden from Swagger (@Hidden) — citation graph, analytics, admin ops
├─ dto/
│   ├─ common/      ApiResponse<T> wrapper used by all endpoints
│   ├─ request/     Incoming payloads (auth/, admin/, collection/)
│   └─ response/    Outgoing shapes (auth/, paper/, keyword/, admin/)
├─ entity/          JPA models; BaseAuditEntity provides createdAt/updatedAt
├─ enums/           UserRole, PaperStatus, SyncStatus, etc.
├─ exception/       GlobalExceptionHandler — centralized error mapping
├─ integration/openalex/  OpenAlexClient — external REST calls
├─ mapper/          Entity ↔ DTO conversions
├─ repository/      Spring Data JPA (23 repositories)
├─ scheduler/       DataSyncScheduler (runs at 2 AM), PaperReviewMaintenanceScheduler
├─ security/        JWT filter chain, Bucket4j rate limiting (60 req/min), RBAC
└─ service/impl/    Business logic; helix/HelixApiService aggregates all Helix controller logic
```

### Security Flow

1. `POST /api/v1/auth/login` → returns access token (15 min) + refresh token (7 days)
2. `JwtAuthenticationFilter` validates Bearer tokens on every request
3. `RateLimitFilter` (Bucket4j) blocks >60 req/min per IP
4. `@PreAuthorize` on controllers enforces `UserRole` (STUDENT, LECTURER, RESEARCHER, ADMIN, SUPER_ADMIN)

### Trend Calculation (BR-02)

`TrendScore = (currentMonth - prevMonth) / prevMonth × 100%`

A keyword is "trending" when score ≥ 15% for 3 consecutive months (BR-04). Pre-calculated monthly values stored in `publication_trends` table for fast dashboard queries.

### OpenAlex Sync

`DataSyncScheduler` → `PaperSyncService` → `OpenAlexClient`. Paginates through results, deduplicates by DOI (`source_type` + `source_identifier` unique index), stores only metadata (no full-text, BR-01).

---

## Database

SQL Server. Schema auto-managed by Hibernate (`spring.jpa.hibernate.ddl-auto`). Key join tables: `paper_authors`, `paper_keywords`, `collection_papers`, `paper_references`.

Performance indexes: `papers(source_type, source_identifier)` for dedup; `publication_trends(keyword_id, trend_year, trend_month)` unique for upserts.

---

## Key Domain Concepts

- **Paper:** Academic publication fetched from OpenAlex; stored with status (`PUBLISHED`, `DRAFT`) and review flags
- **Keyword:** Topic tag linked to papers; each keyword has a pre-calculated monthly trend series
- **PublicationTrend:** Denormalized monthly count per keyword — queried directly for dashboard charts
- **Collection:** User-curated paper lists (like playlists)
- **Follow:** Users follow Keywords, Journals, or Authors to get `Notification` alerts
- **SyncLog:** Records each OpenAlex sync run (RUNNING / SUCCESS / FAILED)
- **Helix API:** Internal endpoint group (`/api/helix/`) for citation graph, deep analytics, and admin ops — hidden from public Swagger docs
