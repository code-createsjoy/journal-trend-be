# OpenAlex Integration

`OpenAlexClient` — `com.norman.swp391.integration.openalex.OpenAlexClient`

Dùng Spring `RestClient` (không phải WebClient). Được inject bean `externalApiRestClient` từ `RestClientConfig` (timeout từ `AppProperties`).

---

## Các method public

### `fetchWorks(search, page, fromPublicationDate)`

Tìm danh sách paper theo từ khóa. Filter mặc định: `has_doi:true,has_abstract:true` + ngày xuất bản.

```
GET /works?search=...&page=...&per_page=50&filter=has_doi:true,...
```

### `fetchWorkById(idOrDoi)`

Lấy metadata một paper cụ thể theo OpenAlex ID (`W1234`) hoặc DOI.

```
GET /works/W1234
GET /works/https://doi.org/10.xxxx/xxxx
```

### `fetchWorksByIds(openAlexIds)`

Batch fetch tối đa 50 IDs mỗi request qua pipe filter.

```
GET /works?filter=openalex_id:W1|W2|W3&select=id,title,publication_year,...
```

Dùng trong **reference metadata cache** — chỉ lấy minimal fields.

### `extractReferencedWorkIds(openAlexId)`

Lấy danh sách IDs của `referenced_works` cho một paper.

```
GET /works/W1234?select=id,referenced_works
```

### `fetchCitingWorks(openAlexId, sort, yearFrom, yearTo, perPage)`

Lấy danh sách paper đang cite paper gốc — **real-time, không cache**.

```
GET /works?filter=cites:W1234[,from_publication_date:...][,to_publication_date:...]
         &sort=cited_by_count:desc&per_page=20&select=id,title,...
```

Sort options: `"cited_by_count:desc"` (default) hoặc `"publication_date:desc"`.

### `fetchAuthorProfile(authorId)`

Lấy profile tác giả từ OpenAlex.

```
GET /authors/A1234
```

---

## Retry & Rate limit

- **150ms delay** sau mỗi request thành công để không vượt rate limit OpenAlex (10 req/s).
- **429 Too Many Requests**: retry với backoff `2s × attempt` (tối đa `openAlexRetryAttempts` lần, default 3).
- `Retry-After` header được tôn trọng nếu có.
- **Quota exhausted** (body chứa "Insufficient budget"): throw `OpenAlexQuotaExhaustedException` → dừng toàn bộ sync.
- Lỗi khác: retry với `500ms × attempt`, log WARN sau lần cuối.

---

## ID normalization

- OpenAlex work ID: `W{number}` — URL `https://openalex.org/W1234` được strip thành `W1234`
- OpenAlex author ID: `A{number}` — tương tự
- DOI: strip prefix `https://doi.org/` hoặc `http://doi.org/`

---

## Abstract reconstruction

OpenAlex trả `abstract_inverted_index` (word → [positions]). `reconstructAbstract()` sắp xếp lại theo position thành chuỗi text đầy đủ.

---

## Keyword extraction

Từ `topics` (ưu tiên) + `concepts` (fallback). Gán `domain` theo subfield/field/domain của topic. Dedup case-insensitive.
