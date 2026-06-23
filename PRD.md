# Product Requirements Document (PRD): JournalTrend

## 1. Product Overview
**JournalTrend** is a Scientific Journal Publication Trend Tracking System designed to help researchers, lecturers, and students identify emerging research topics and track the evolution of research fields over time. While traditional platforms focus primarily on keyword searches, JournalTrend aggregates academic metadata from free public APIs (such as Semantic Scholar, OpenAlex, and Crossref), calculates trend dynamics, and provides powerful visualizations of research topic popularity.

**Out of Scope:**
- Full-text PDF access or storage (due to copyright restrictions).
- Real-time data synchronization (only periodic batch sync is supported).
- Broad multi-domain coverage beyond pre-selected fields (restricted strictly to Computer Science & AI, e.g., Machine Learning, Generative AI, Robotics).
- Citation graphs or co-authorship network visualizations.

## 2. Business Goals
- **Empower Researchers:** Provide actionable insights into which research topics are gaining or losing momentum to help users make informed decisions on their research direction.
- **Centralized Insights:** Create a single, automated dashboard for academic trend analysis without requiring users to manually compile and analyze data across multiple academic databases.
- **User Retention:** Encourage regular platform usage through personalized features such as bookmarks, followed topics, and automated email/in-app notifications for emerging trends.

## 3. User Stories

### End Users (Researchers / Students / Lecturers)
- **Account Management:** As a user, I want to register an account, verify my email, and securely log in so that my data is personalized and protected. If I forget my password, I want to be able to reset it via email.
- **Search:** As a researcher, I want to search for papers by keyword, author, or journal so that I can find relevant academic publications.
- **Visual Trends:** As a researcher, I want to view trend charts for specific keywords over time so that I can understand how a research field is evolving.
- **Discover:** As a user, I want to see an auto-updated "Trending Topics" dashboard so that I can quickly discover new and popular research areas.
- **Stay Updated:** As a user, I want to follow specific keywords or journals and receive notifications so that I stay informed on new publications matching my interests.
- **Save for Later:** As a user, I want to bookmark specific papers so that I can easily find and review them later.
- **Export Data:** As a user, I want to export analytical reports (CSV/PDF) so that I can include trend data in my own research documents or presentations.

### System Administrators
- **User Management:** As an administrator, I want to manage user accounts and system permissions to ensure platform security.
- **System Configuration:** As an administrator, I want to configure external API sources and set up data synchronization schedules to ensure the system processes the right data at the right time.
- **Manual Operations:** As an administrator, I want to manually trigger data synchronizations if scheduled jobs fail or immediate updates are needed.

## 4. Functional Requirements
- **Authentication & Security:** 
  - The system must support user registration with Email Verification (links expire in 24 hours).
  - The system must support Password Reset functionality (tokens expire in 30 minutes).
  - Authentication must be handled via secure JWT (JSON Web Tokens) with both Access and Refresh tokens.
- **Search & Filtering Engine:** The system must allow users to query papers by keyword, author, and journal using the aggregated database metadata within predefined domains (AI, ML, Robotics, Data Science).
- **Trend Analytics & Calculation:**
  - The system must display publication trend charts by keyword/topic.
  - **Trending Rule (BR-04):** The system must automatically identify a topic as "Trending" if its trend score is $\ge +15\%$ for 3 consecutive months.
  - **Score Formula (BR-02):** Trend Score = `((Current Month Count - Previous Month Count) / Previous Month Count) * 100%`.
- **Personalization Module & Quotas:**
  - Users must be able to bookmark papers (Limit: Max 200 papers per user).
  - Users must be able to follow up to 20 keywords and 10 journals.
  - The system must trigger email or in-app notifications for new matches based on followed entities.
  - **Deduplication (BR-05):** Duplicate papers aggregated from multiple API sources must be deduplicated by their DOI before notifying users.
- **Reporting:** The system must support exporting trend data and paper lists in CSV or PDF formats.
- **Background Synchronization:** 
  - The system must run periodic batch jobs (e.g., daily at 02:00 AM) to fetch metadata (title, abstract, keywords, year, authors, journal) from Semantic Scholar, OpenAlex, and Crossref.
  - **Restriction (BR-01):** Full-text PDFs must not be retrieved or cached to comply with copyright restrictions.

## 5. Non-Functional Requirements
- **Performance:** 
  - Analytical trend charts and dashboards should render quickly (target: under 3 seconds) for an optimal user experience.
  - Background synchronization jobs must use batch processing (e.g., ingest batch size 25) and rate-limiting to avoid exhausting database connections or getting blocked by third-party APIs.
- **Scalability:** 
  - The application follows a decoupled architecture (React frontend + Spring Boot backend), allowing independent scaling.
  - The database must utilize proper indexing on `keyword`, `year`, `month`, and `trendScore` to ensure fast analytical queries.
- **Security & Rate Limiting:** 
  - APIs must be secured against brute-force and DDoS attacks via a strict Rate Limit (maximum 60 requests per minute per IP).
  - Passwords must be securely hashed before storage.
- **Usability:** The frontend must be a highly responsive Single Page Application (SPA) using modern UI frameworks (Tailwind CSS/Material-UI) and interactive charting libraries (Chart.js/Recharts).
- **Compliance:** Adherence to third-party API rate limits and strict enforcement of the metadata-only policy (no full-text storage).

## 6. Success Metrics
- **Engagement:** Average session duration and weekly active users (WAU).
- **Feature Adoption Rate:** 
  - Percentage of active users who have set up at least one "Followed Keyword/Journal".
  - Average number of bookmarked papers per user.
- **System Reliability:** 
  - 99.9% uptime for the backend REST API.
  - 100% success rate for daily automated data synchronization jobs without unhandled exceptions.
- **Data Growth & Quality:** Total number of unique, deduplicated metadata records successfully synced per month.
