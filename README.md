# JournalTrend - Scientific Journal Publication Trend Tracking System

> **Course:** SWP391 AI-Augmented Software Requirements (Semester: Summer 2026)
> **Topic Code:** SU26SWP06
> **Document Version:** SU26-v1.0

---

## Project Overview

As the volume of academic publications grows exponentially, researchers, lecturers, and students struggle to identify emerging topics and track how research fields evolve. [cite_start]Existing platforms support keyword search but offer little trend visualization[cite: 3]. 

[cite_start]**JournalTrend** is designed to address these pain points by aggregating publication metadata from free public APIs, calculating trend dynamics, and visualizing the rise and fall of research topics over time[cite: 3, 4].

### System Scope

#### IN SCOPE 
* [cite_start]**Paper Search:** Query papers by keyword, author, or journal backed by external APIs[cite: 4].
* [cite_start]**Trend Analytics:** Publication trend charts by keyword/topic over time and an auto-updated trending topics dashboard[cite: 4].
* [cite_start]**Personalization:** Bookmark papers, follow specific keywords/journals, and receive email/in-app notifications for new matches[cite: 4].
* [cite_start]**Reporting:** Export simple analytical reports via CSV or PDF[cite: 4].
* [cite_start]**Administration:** Manage users, configure API sources, and set up data sync schedules[cite: 4].

#### OUT OF SCOPE 
* [cite_start]Full-text PDF access due to copyright restrictions[cite: 4].
* [cite_start]Real-time data synchronization (periodic batch sync only: daily/weekly)[cite: 4].
* [cite_start]Multi-domain coverage beyond pre-selected fields (e.g., restricted to Computer Science, AI)[cite: 4].
* [cite_start]AI-powered research recommendations beyond standard keyword matching[cite: 4].
* [cite_start]Citation graphs or co-authorship network visualizations[cite: 4].

---

## Team Members & Roles

The project is currently being managed and implemented individually:

* [cite_start]| Nguyễn Quốc Thái | SE194540 | BackEnd | SpringBoot |
* [cite_start]| Hà Nhật Tiến | SE194541 | BackEnd | SpringBoot |
* [cite_start]| Nguyễn Hữu Khánh | SE194635 | FrontEnd | ReactJS/Tailwind |
* [cite_start]| Nguyễn Đặng Minh Tâm | SE194685 | FrontEnd | ReactJS/Tailwind |
* [cite_start]| Phạm Xuân Nam | SE194686 | FrontEnd | ReactJS/Tailwind |

---

## Technology Stack

The system utilizes a decoupled Full-Stack architecture separating the interactive frontend SPA (Single Page Application) from the robust backend REST API:

### 1. Backend Core & ORM
* **Framework:** Spring Boot (Java) for building scalable RESTful web services.
* **Data Persistence:** Hibernate / Spring Data JPA for Object-Relational Mapping (ORM) and data lifecycle management.
* **Security & Scheduling:** Spring Security for token-based authentication (JWT) and Spring @Scheduled for periodic background batch jobs.

### 2. Database & Data Integration
* **Database Management System:** SQL Server / MySQL.
* [cite_start]**External Integrations:** Spring RestTemplate / WebClient to consume RESTful Free Public APIs from **Semantic Scholar**, **OpenAlex**, and **Crossref** to batch-sync academic metadata[cite: 3, 4].

### 3. Frontend & Visualization
* **Library:** React.js (JavaScript/TypeScript) for a highly responsive, component-based user interface.
* **State Management & Routing:** React Router for navigation and Context API / Redux Toolkit for clean state handling.
* [cite_start]**Data Visualization:** Chart.js / Recharts (React wrapper) to power the analytical trend timelines[cite: 4].
* **UI Framework:** Tailwind CSS / Material-UI (MUI) for a fully responsive dashboard administration layout.

---

## Core Business Rules (BR)

The system enforces the following behaviors, definitions, and calculations:

* [cite_start]**BR-01 (Behavioral):** The system **SHALL only** store and display metadata (title, abstract, keywords, year, authors, journal); full-text content **SHALL NOT** be retrieved or cached due to copyright restrictions[cite: 6].
* [cite_start]**BR-02 (Calculational):** Trend score is computed using the following formula[cite: 6]:
    $$\text{Trend Score} = \frac{\text{Current Month Paper Count} - \text{Previous Month Paper Count}}{\text{Previous Month Paper Count}} \times 100\%$$
* **BR-03 (Temporal):** API synchronization runs automatically on a configured schedule (default: daily at 02:00 AM). Manual sync triggers are restricted to the System Admin only[cite: 6].
* **BR-04 (Definitional):** A **"Trending Topic"** is strictly defined as any keyword maintaining a trend score $\ge +15\%$ for 3 consecutive months[cite: 6].
* **BR-05 (Behavioral):** Notifications are sent only once per new paper per user subscription; duplicate papers imported from multiple API sources are automatically deduplicated by their **DOI**[cite: 6].

---

## Domain Entity Architecture

[cite_start]The relational core manages data flows across 6 primary domain entities (mapped directly via Hibernate annotations)[cite: 5]:
1. [cite_start]**Paper:** `paperID, title, abstract, keywords[], year, journalID, authors[], source, citationCount` [cite: 5]
2. [cite_start]**Journal:** `journalID, name, publisher, impactFactor, domain, issn` [cite: 5]
3. [cite_start]**Keyword:** `keywordID, term, domain, paperCount, trendScore` [cite: 5]
4. [cite_start]**PublicationTrend:** `trendID, keywordID, year, month, paperCount, delta%` [cite: 5]
5. [cite_start]**Bookmark:** `bookmarkID, userID, targetType, targetID, savedAt, note` [cite: 5]
6. [cite_start]**Notification:** `notifID, userID, triggerType, keyword/journalID, paperID, sentAt, read` [cite: 5]
