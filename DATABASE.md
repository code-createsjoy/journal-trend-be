# Database Schema Documentation: JournalTrend

This document provides a conceptual overview of the relational database schema used in the JournalTrend backend, exactly matching the JPA Entity definitions.

## 1. Domain Overview

The database is structured around five primary sub-domains:
1. **User Management:** Users, roles, authentication, and security tokens.
2. **Academic Metadata:** Papers, Journals, Authors, and Keywords fetched from external APIs.
3. **Analytics & Trends:** Pre-calculated time-series data for publication trends.
4. **Personalization:** Bookmarks (Collections), Followed entities, and User Notifications.
5. **System Administration:** API sync execution logs, review audits, and configurations.

---

## 2. Core Entities (Tables)

### 2.1 User Management
- **`app_users`** (Entity: `User`)
  - Core account table.
  - **Fields:** `id`, `email`, `password`, `full_name`, `user_role` (STUDENT, LECTURER, RESEARCHER, ADMIN, SUPER_ADMIN), `user_status`, `enabled`, `verified`.
- **`refresh_tokens`** (Entity: `RefreshToken`)
  - Manages active JWT refresh sessions to maintain user logins.
  - **Fields:** `id`, `token`, `user_id`, `expires_at`, `revoked`.
- **`email_verification_tokens`** & **`password_reset_tokens`**
  - Short-lived, secure tokens for account recovery and email verification workflows.

### 2.2 Academic Metadata
- **`papers`** (Entity: `Paper`)
  - The central entity representing a published academic article.
  - **Fields:** `id`, `title`, `abstract_text`, `publication_date`, `citation_count`, `doi`, `source_type`, `source_identifier`, `source_url`, `pdf_url`, `open_access`, `journal` (String), `journal_id` (FK), `primary_source`, `status`, `created_at`, `review_status`, `review_flagged_at`, `conflict_title`, `conflict_abstract`, `conflict_source`.
- **`journals`** (Entity: `Journal`)
  - Represents the academic journal or conference.
  - **Fields:** `id`, `name`, `issn`, `publisher`, `domain`, `impact_factor`.
- **`authors`** (Entity: `Author`)
  - Represents researchers who wrote the papers.
  - **Fields:** `id`, `name`, `external_id`, `affiliation`, `h_index`.
- **`keywords`** (Entity: `Keyword`)
  - Research topics extracted from papers. Used as the primary pivot for trend calculations.
  - **Fields:** `id`, `term`, `domain`, `current_trend_score`, `total_paper_count`.
- **Mapping Tables (Many-to-Many Resolvers)**
  - **`paper_authors`**: Links `papers` to `authors`.
  - **`paper_keywords`**: Links `papers` to `keywords`.

### 2.3 Analytics & Trends
- **`publication_trends`** (Entity: `PublicationTrend`)
  - Stores pre-calculated monthly statistics to speed up dashboard rendering without requiring heavy `GROUP BY` queries on every request.
  - **Fields:** `trend_id`, `keyword_id`, `trend_year`, `trend_month`, `paper_count`, `delta_percent`, `created_at`.
  - **Constraints:** Unique constraint on `(keyword_id, trend_year, trend_month)`.

### 2.4 Personalization (Bookmarks & Follows)
- **`paper_collections`** (Entity: `PaperCollection`)
  - Custom folders created by users to bookmark and organize papers.
  - **Fields:** `id`, `user_id`, `name`, `description`, `created_at`.
- **`collection_papers`** (Entity: `CollectionPaper`)
  - Join table linking `paper_collections` to `papers`.
- **`follow_keywords`** & **`follow_journals`**
  - Tracks which users are subscribed to which topics/journals for automated alerts.
- **`notifications`** (Entity: `Notification`)
  - In-app alerts generated for users.
  - **Fields:** `id`, `user_id`, `paper_id`, `trigger_type`, `status` (READ/UNREAD), `created_at`.

### 2.5 System Administration
- **`sync_logs`** (Entity: `SyncLog`)
  - Audit trail for background data synchronization jobs fetching data from OpenAlex/Semantic Scholar.
  - **Fields:** `id`, `started_at`, `finished_at`, `status` (SUCCESS, FAILED, etc.), `papers_fetched`, `error_message`, `triggered_by_admin_id`.
- **`api_source_configs`** (Entity: `ApiSourceConfig`)
  - Database-driven configurations for external APIs.
- **`paper_review_audits`** (Entity: `PaperReviewAudit`)
  - Audit logs tracking manual administrative interventions on papers (e.g., hiding a duplicate paper, resolving conflicts).
  - **Fields:** `id`, `paper_id`, `admin_id`, `action`, `note`, `created_at`.

---

## 3. Key Relationships & Cardinality

- **User $\leftrightarrow$ Collections:** One-to-Many. A user can create multiple `paper_collections`.
- **Collection $\leftrightarrow$ Paper:** Many-to-Many via `collection_papers`.
- **Paper $\rightarrow$ Journal:** Many-to-One via `journal_id`.
- **Paper $\leftrightarrow$ Author:** Many-to-Many via `paper_authors`.
- **Paper $\leftrightarrow$ Keyword:** Many-to-Many via `paper_keywords`.
- **Keyword $\leftrightarrow$ PublicationTrend:** One-to-Many.

## 4. Indexing Strategy (Performance)
The database utilizes specific indexing patterns explicitly mapped in JPA constraints (`@Table(indexes = {...})`):
- **`papers`**: Indexed on `(source_type, source_identifier)` (e.g., OPENALEX: W23141) to support exceptionally fast lookups during data ingest and deduplication.
- **`publication_trends`**: Uses a compound unique constraint on `(keyword_id, trend_year, trend_month)` which implicitly creates an index for rapid time-series retrieval.
