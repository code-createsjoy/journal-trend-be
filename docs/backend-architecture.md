# Backend Architecture

Spring Boot 3.5.3 · Java 21 · SQL Server · Hibernate auto-DDL

## Layer pattern

```
HTTP request
  → RateLimitFilter (Bucket4j, 60 req/min/IP)
  → JwtAuthenticationFilter
  → Controller
  → Service interface
  → ServiceImpl
  → Repository (Spring Data JPA)
  → DB (SQL Server)
```

## Package tree

```
com.norman.swp391/
├── config/
│   ├── AppProperties.java          Tất cả YAML config dạng typed bean (Jwt, OpenAlex, Sync)
│   ├── AsyncConfig.java            ThreadPoolTaskExecutor cho @Async
│   ├── RestClientConfig.java       RestClient bean cho OpenAlex (timeout từ AppProperties)
│   ├── SchedulerConfig.java        Bật @EnableScheduling
│   └── DataInitializer.java        Seed dữ liệu khởi đầu (SUPER_ADMIN account)
│
├── controller/
│   ├── v1/                         Public API — có Swagger docs
│   │   ├── AuthController          /api/v1/auth/**
│   │   ├── PaperController         /api/v1/papers/**
│   │   ├── KeywordController       /api/v1/keywords/**
│   │   ├── TrendController         /api/v1/trends/**
│   │   ├── AuthorController        /api/v1/authors/**
│   │   ├── CollectionController    /api/v1/collections/**
│   │   ├── DashboardController     /api/v1/dashboard/**
│   │   ├── NotificationController  /api/v1/notifications/**
│   │   ├── FollowKeywordController /api/v1/follow/keywords/**
│   │   ├── FollowAuthorController  /api/v1/follow/authors/**
│   │   ├── EmailVerificationController
│   │   ├── AdminController         /api/v1/admin/**
│   │   └── SuperAdminController    /api/v1/super-admin/**
│   │
│   └── helix/                      Internal API — @Hidden (Swagger ẩn)
│       ├── HelixAuthController     /api/auth/**
│       ├── HelixPapersController   /api/papers/**
│       ├── HelixKeywordsController /api/topics/**
│       ├── HelixAuthorsController  /api/authors/**
│       ├── HelixJournalsController /api/journals/**
│       ├── HelixAnalyticsController /api/analytics/**
│       ├── HelixCollectionsController /api/collections/**
│       ├── HelixNotificationsController /api/notifications/**
│       ├── HelixFollowController   /api/follow/**
│       ├── HelixAdminController    /api/admin/**
│       └── HelixReportsController  /api/reports/**
│
├── dto/
│   ├── common/ApiResponse.java     Wrapper chung: { success, message, data, timestamp }
│   ├── helix/HelixDtos.java        Tất cả Helix record DTOs (paper, auth, analytics, v.v.)
│   ├── request/                    Incoming payloads (auth/, admin/, collection/)
│   └── response/                   Outgoing shapes (auth/, paper/, keyword/, admin/)
│
├── entity/                         JPA entities — xem backend-entities.md
│
├── exception/
│   ├── GlobalExceptionHandler.java Centralized error → ApiResponse mapping
│   ├── ResourceNotFoundException   → 404
│   ├── BadRequestException         → 400
│   └── OpenAlexQuotaExhaustedException → dừng sync khi hết quota
│
├── integration/openalex/
│   └── OpenAlexClient.java         Toàn bộ HTTP calls ra OpenAlex — xem backend-openalex.md
│
├── mapper/                         Entity ↔ DTO conversion (manual, không dùng MapStruct)
│
├── repository/                     Spring Data JPA — 26 repository interfaces
│
├── scheduler/
│   ├── DataSyncScheduler.java      Cron "0 0 2 * * *" → PaperSyncService.startSync()
│   └── PaperReviewMaintenanceScheduler.java  Expire stale pending reviews
│
├── security/                       xem backend-security.md
│
└── service/
    ├── impl/                       Business logic implementations
    └── helix/HelixApiService.java  Aggregates tất cả logic cho Helix controllers
```

## Service inventory

| Service | Trách nhiệm |
|---|---|
| `AuthService` | Register, login, logout, refresh token, forgot/reset password |
| `PaperService` | CRUD paper, search, pagination |
| `PaperSyncService` | Sync pipeline từ OpenAlex → DB |
| `PaperReferenceService` | Lazy-cache references, real-time citations |
| `PaperReviewService` | Workflow duyệt/từ chối paper xung đột |
| `KeywordService` | CRUD keyword |
| `KeywordTrendService` | Tính trend score, recalculate all, backfill lịch sử |
| `AuthorService` | Author lookup, profile enrichment từ OpenAlex |
| `JournalService` | Journal persistence |
| `CollectionService` | User collection (bookmark) management |
| `NotificationService` | Tạo và đọc notifications |
| `FollowKeywordService` / `FollowAuthorService` / `FollowJournalService` | Follow/unfollow + giới hạn BR |
| `DashboardService` | Dashboard summary stats |
| `EmailService` | Gmail SMTP (verification, password reset) |
| `ReportExportService` | Export báo cáo |
| `TrendDemoStatsService` | Stats cho admin demo panel |
| `ApiSourceService` | Quản lý ApiSourceConfig (enabled/disabled) |
| `PaperMetadataRepairService` | Re-fetch metadata thiếu từ OpenAlex |
| `HelixApiService` | Aggregator — dùng bởi tất cả Helix controllers |
