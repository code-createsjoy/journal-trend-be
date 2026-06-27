# Feature Request: Interactive References & Citation Graph for Academic Papers

## Background

JournalTrend currently allows users to view paper details such as:

- Title
- Authors
- Publication Year
- Abstract
- Citations
- Keywords

However, users cannot easily explore the academic relationships between papers.

We want to introduce an interactive graph visualization that helps users understand:

- Which papers are referenced by the current paper.
- Which papers cite the current paper.
- The academic influence and knowledge flow between publications.

---

# Feature Overview

Add a new section inside the Paper Detail page:

```text
Paper Detail
├── Overview
├── Authors
├── Keywords
├── References Graph
└── Citation Graph
```

---

# Part 1: References Graph

## Purpose

Show the papers that are cited by the current paper.

### Example

```text
Current Paper
├── Paper A
├── Paper B
├── Paper C
└── Paper D
```

- Current paper is displayed as the center node.
- Referenced papers are displayed as surrounding nodes.

---

## Data Source

Use OpenAlex referenced works:

```json
{
  "referenced_works": [
    "W123",
    "W456",
    "W789"
  ]
}
```

Store references in a dedicated table:

```java
PaperReference {
    paperId;
    referencedOpenAlexId;
}
```

---

## Metadata Strategy

Do **not** sync all referenced papers.

Instead:

1. Check whether metadata already exists locally.
2. If not found:
   - Fetch metadata from OpenAlex lazily.
   - Cache locally.

Store:

```java
ReferenceMetadata {
    openAlexId;
    title;
    publicationYear;
    doi;
}
```

No need to store full paper details.

---

## Graph Behavior

When the user opens **References Graph**:

Display:

```text
Current Paper
 |
 +-- Paper A
 +-- Paper B
 +-- Paper C
```

### Node Tooltip

When hovering over a node:

```text
Title: Attention Is All You Need
Year: 2017
DOI: 10.48550/arXiv.1706.03762
```

Optional:

```text
Citation Count: 150000
```

### Node Click

When clicking a node:

- Open the Paper Detail page if that paper exists locally.
- If not:
  - Show metadata popup.
  - Allow user to import/sync the paper.

---

## Limits

Default maximum references displayed:

```text
50 nodes
```

If more references exist:

- Show first 50 references.
- Display **Load More** button.

---

# Part 2: Citation Graph

## Purpose

Show papers that cite the current paper.

### Example

```text
          Paper X
             ↑
Paper Y ← Current Paper → Paper Z
             ↓
          Paper W
```

This graph helps users understand the academic influence of a paper.

---

## Important Performance Requirement

Do **not** load every citing paper.

Some papers have hundreds or thousands of citations.

Loading all citations would:

- Overload OpenAlex
- Slow down the frontend
- Create unreadable graphs

---

## Default Strategy

Show:

```text
Top 20 citing papers
```

Only.

---

## Sorting Options

Allow:

```text
Sort By:
- Citation Count
- Most Recent
```

Default:

```text
Citation Count Descending
```

---

## Filters

Provide:

- Year Range
- Max Nodes

Example:

```text
2023 - 2026
Max Nodes: 20 / 50 / 100
```

---

## Node Tooltip

Show:

- Title
- Publication Year
- Citation Count

---

# Visualization Requirements

Frontend should use a graph visualization library.

### Recommended Options

- React Flow
- Cytoscape.js

### Required Capabilities

- Zoom
- Pan
- Hover tooltip
- Click node
- Dynamic loading

---

# Academic Value

This feature should transform paper exploration from a simple metadata view into an academic relationship explorer.

Users should be able to:

- Discover foundational papers
- Understand research lineage
- Explore influential descendants
- Identify highly cited works
- Navigate citation networks visually

The graph should emphasize academic understanding rather than displaying raw IDs or DOI values.