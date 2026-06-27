# Software Architecture Document (SAD): JournalTrend Backend

This document outlines the architectural design, patterns, and structural layout of the JournalTrend Spring Boot backend system.

## 1. Architectural Pattern

The project follows a standard **N-Tier (Layered) Architecture**, ensuring separation of concerns, making the system maintainable, testable, and scalable.

### 1.1 Layers
- **Presentation Layer (`controller`):** Handles incoming HTTP REST requests, validates input, and delegates business logic to the service layer. Returns structured DTOs (Data Transfer Objects). Divided into two distinct API groups: `v1` (Standard API) and `helix` (Internal/Alternative APIs).
- **Business Logic Layer (`service`):** Contains the core business rules. Interfaces are defined in the `service` package, and their implementations reside in `service/impl`. Includes a specialized `service/helix` package.
- **Data Access Layer (`repository`):** Spring Data JPA interfaces that handle database interactions via Hibernate ORM.
- **Integration Layer (`integration`):** Responsible for outward-facing communication with external APIs.

## 2. Directory & Package Structure

The source code (`src/main/java/com/norman/swp391`) is logically grouped by technical concern:

- **`config/`**: Global application configurations (`AppProperties`, `AsyncConfig`, `DataInitializer`, `OpenApiConfig`, `RestClientConfig`, `SchedulerConfig`).
- **`controller/`**: REST API endpoints exposed to clients.
  - **`v1/`**: The standard public-facing REST API (e.g., `PaperController`, `DashboardController`).
  - **`helix/`**: Specialized APIs returning `HelixDtos` (often hidden from public Swagger, e.g., `HelixAdminController`, `HelixPapersController`).
- **`dto/`**: Data Transfer Objects used to receive requests and send responses. Also divided into standard DTOs and `helix` DTOs.
- **`entity/`**: JPA Domain models mapping directly to the database. Includes the `enums/` package (`UserRole`, `PaperStatus`, etc.).
- **`exception/`**: Centralized error handling (`GlobalExceptionHandler`) to translate backend exceptions into standardized HTTP error responses.
- **`integration/`**: External API clients. Currently implements the `openalex` client.
- **`mapper/`**: Classes responsible for mapping data between `entity` models and `dto` models.
- **`repository/`**: Interfaces extending `JpaRepository` for data access.
- **`scheduler/`**: Cron jobs and background tasks (`DataSyncScheduler`, `PaperReviewMaintenanceScheduler`).
- **`security/`**: JWT provider, authentication filters (`JwtAuthenticationFilter`), rate limiting (`RateLimitFilter`), and Spring Security logic (`SecurityUtils`, `SecurityConfig`).
- **`service/` & `service/impl/`**: The core application logic.

## 3. Core Subsystems

### 3.1 Security & Authentication (`security` package)
- **Authentication Mechanism:** Stateless token-based authentication using **JSON Web Tokens (JWT)**.
- **Flow:** 
  1. Client sends credentials to `AuthController` (or `HelixAuthController`).
  2. If valid, the system generates an `Access Token` (short-lived) and a `Refresh Token` (long-lived).
  3. `JwtAuthenticationFilter` intercepts subsequent requests, validates the token, and establishes the Security Context.
- **Rate Limiting:** `RateLimitFilter` prevents API abuse (e.g., DDoS or brute force) by restricting requests per IP.
- **Role-Based Access Control (RBAC):** `STUDENT`, `LECTURER`, `RESEARCHER`, `ADMIN`, `SUPER_ADMIN` roles enforce endpoint-level security constraints (via `@PreAuthorize`).

### 3.2 Data Synchronization Engine (`scheduler`, `integration`, `service`)
- **Trigger:** `DataSyncScheduler` runs periodically or `AdminController` triggers sync manually.
- **Execution:** It delegates to `PaperSyncService`.
- **Integration:** The service utilizes `OpenAlexClient` to fetch metadata.
- **Persistence:** Fetched data is transformed, deduplicated (using DOIs), and saved to the local database. Sync operations are logged via `SyncLog`.

### 3.3 Trend Analytics Engine
- `KeywordTrendService` computes trend scores based on monthly paper counts.
- Results are pre-calculated and persisted into the `PublicationTrend` entity to ensure extremely fast dashboard rendering (avoiding real-time SQL aggregations).
- Includes historical backfill capabilities (`backfillHistoricalMonths`) and recalculation jobs.

## 4. Third-Party Integrations
- **Database:** Microsoft SQL Server (via JDBC Driver).
- **External APIs:** OpenAlex (integrated via Spring REST clients configured in `RestClientConfig`). Note: Crossref is a potential future source but currently unimplemented.
- **Email Delivery:** SMTP (via Spring Mail) for sending email verifications and password resets.
- **Documentation:** SpringDoc OpenAPI configured via `OpenApiConfig`.

## 5. Scalability & Performance Considerations
- **Stateless APIs:** Ensures the backend can be horizontally scaled.
- **Data ingestion:** Data ingestion uses batch processing via OpenAlex pagination to prevent memory exhaustion.
- **Asynchronous Execution:** Handled via `@EnableAsync` (in `AsyncConfig`).
- **Data Initialization:** `DataInitializer` populates default users/configurations safely on startup.
