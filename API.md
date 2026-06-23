# REST API Documentation: JournalTrend Backend

This document provides an overview of the core RESTful API endpoints available in the JournalTrend backend system. The APIs are divided into two distinct namespaces: Standard `v1` APIs and `helix` APIs.

> **Note:** For complete request/response schemas, JSON payloads, DTO structures, and interactive testing, please run the application locally and visit the **Swagger UI** at `http://localhost:8080/swagger-ui.html` or view the OpenAPI specification at `http://localhost:8080/api-docs`.

---

## 1. Standard APIs (`/api/v1`)
These endpoints represent the primary public-facing API for the frontend application.

### 1.1 Authentication & Authorization (`/auth`)
- `POST /api/v1/auth/register` - Register a new user account.
- `POST /api/v1/auth/login` - Authenticate user and receive JWT Access & Refresh tokens.
- `POST /api/v1/auth/refresh` - Obtain a new Access Token using a valid Refresh Token.
- `POST /api/v1/auth/logout` - Invalidate current tokens.
- `POST /api/v1/auth/forgot-password` - Trigger password reset email.
- `GET /api/v1/auth/verify` - Verify user's email address using a token.
- `POST /api/v1/auth/resend-verification` - Resend the email verification link.
- `GET /api/v1/auth/check-verification-status` - Check if the authenticated user's email is verified.

### 1.2 Papers (`/papers`)
- `GET /api/v1/papers` - Search papers with filters (by keyword, author, journal, year, domain).
- `GET /api/v1/papers/{id}` - Get detailed metadata for a specific paper.
- `GET /api/v1/papers/by-domain` - Retrieve papers categorized by specific research domains.
- `GET /api/v1/papers/years` - Get a list of distinct publication years available in the database.

### 1.3 Keywords & Trends (`/keywords`, `/trends`)
- `GET /api/v1/keywords` - Search and list available keywords.
- `GET /api/v1/keywords/trending` - Retrieve current "Trending Topics".
- `GET /api/v1/keywords/{id}` - Get specific keyword details.
- `GET /api/v1/keywords/{id}/trends` - Get historical trend score data.
- `GET /api/v1/trends` - Generic endpoint to query aggregated publication trends.

### 1.4 Dashboards (`/dashboard`)
- `GET /api/v1/dashboard/summary` - Get high-level summary statistics.
- `GET /api/v1/dashboard/keyword-chart` - Get formatted data arrays optimized for rendering charts.

### 1.5 Personalization (`/collections`, `/follow`)
- `GET, POST, PUT, DELETE /api/v1/collections` - Manage paper collections (bookmarks).
- `GET, POST, DELETE /api/v1/collections/{id}/papers` - Manage papers within a collection.
- `GET, POST, DELETE /api/v1/follow/keywords` - Manage keyword subscriptions.

### 1.6 General
- `GET /api/v1/notifications` - Retrieve in-app notifications.
- `GET /api/v1/authors/...` - Author search and details.

### 1.7 Administration (`/admin`, `/super-admin`)
- `POST /api/v1/admin/sync` - Manually trigger the background data sync.
- `GET /api/v1/admin/sync-logs` - Retrieve the history of data synchronizations.
- `GET /api/v1/admin/users` - List all users.
- `POST /api/v1/admin/users/{id}/lock` & `/unlock` - Lock/Unlock user accounts.
- `DELETE /api/v1/admin/papers/{id}` - Delete a paper.
- `GET /api/v1/admin/papers/review` - Review flagged papers.
- `POST /api/v1/admin/papers/{id}/review/accept` - Accept a flagged paper.
- `POST /api/v1/admin/trends/recalculate` & `/backfill` - Trend data management.
- `GET /api/v1/super-admin/admins` - List all users with administrative roles.
- `PUT /api/v1/super-admin/users/{id}/role` - Update a user's role (Super Admin only).

---

## 2. Helix APIs (`/api/helix` or specialized paths)
The system includes a secondary "Helix" API layer. These controllers typically return `HelixDtos` and are often marked with `@Hidden` to exclude them from public Swagger documentation, acting as a specialized or internal administration layer.

- `HelixAdminController` (`/api/admin/overview`, `/api/admin/sync`, `/api/admin/sources`...)
- `HelixAuthController`
- `HelixAnalyticsController`
- `HelixAuthorsController`
- `HelixCollectionsController`
- `HelixFollowController`
- `HelixJournalsController`
- `HelixKeywordsController`
- `HelixNotificationsController`
- `HelixPapersController`
- `HelixReportsController`

---

## 3. System Health (`/health`)
- `GET /health` - Unsecured endpoint used by load balancers, Docker, or monitoring tools to verify if the application is running.
