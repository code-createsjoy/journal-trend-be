# Interactive References & Citation Graph — Backend Documentation

## Tổng Quan

Hai chức năng mới được thêm vào hệ thống JournalTrend nhằm hỗ trợ khám phá mối quan hệ học thuật giữa các bài báo:

1. **References Graph** — Hiển thị các papers **được trích dẫn bởi** paper hiện tại
2. **Citation Graph** — Hiển thị các papers **trích dẫn** paper hiện tại

Cả hai chức năng đều dựa trên dữ liệu từ **OpenAlex API**, và được thiết kế để tích hợp với giao diện graph visualization phía frontend.

---

## 1. References Graph

### Mục đích

Cho phép người dùng xem danh sách các bài báo mà paper hiện tại đã tham chiếu (cited), giúp hiểu nền tảng tri thức và nguồn gốc nghiên cứu.

### API Endpoint

```
GET /api/papers/{id}/references?limit=50
```

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---------|------|----------|----------|-------|
| `id` | Long | ✅ | — | ID paper trong DB |
| `limit` | int | ❌ | 50 | Số lượng tối đa references trả về |

### Response

```json
[
  {
    "openAlexId": "W2741809807",
    "title": "Attention Is All You Need",
    "year": 2017,
    "doi": "10.48550/arXiv.1706.03762",
    "citations": 150000,
    "localPaperId": "42",
    "existsLocally": true
  },
  {
    "openAlexId": "W1234567890",
    "title": "Deep Residual Learning for Image Recognition",
    "year": 2015,
    "doi": "10.1109/CVPR.2016.90",
    "citations": 200000,
    "localPaperId": null,
    "existsLocally": false
  }
]
```

### Chiến lược dữ liệu

- **Pre-populate khi sync**: Khi đồng bộ papers từ OpenAlex, trường `referenced_works` được parse tự động và lưu vào bảng `paper_references`.
- **Lazy fetch**: Nếu paper chưa có references trong DB (ví dụ paper cũ trước khi có feature), hệ thống tự động gọi OpenAlex lấy `referenced_works` và lưu cache.
- **Metadata cache**: Metadata nhẹ (title, year, doi, citation count) của mỗi referenced work được lưu trong bảng `reference_metadata` để tránh gọi API lặp lại.
- **Cross-reference**: Tự động kiểm tra xem referenced work đã tồn tại trong DB local chưa, gắn `localPaperId` nếu có.

### Bảng DB mới

| Bảng | Mục đích |
|------|----------|
| `paper_references` | Lưu quan hệ paper → referenced OpenAlex work IDs |
| `reference_metadata` | Cache metadata nhẹ cho referenced works |

---

## 2. Citation Graph

### Mục đích

Cho phép người dùng xem danh sách các bài báo đã trích dẫn paper hiện tại, giúp đánh giá tầm ảnh hưởng và dòng chảy tri thức.

### API Endpoint

```
GET /api/papers/{id}/citations?sort=citations&yearFrom=2023&yearTo=2026&limit=20
```

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---------|------|----------|----------|-------|
| `id` | Long | ✅ | — | ID paper trong DB |
| `sort` | String | ❌ | `citations` | Cách sắp xếp: `citations` (theo citation count) hoặc `recent` (theo ngày xuất bản) |
| `yearFrom` | Integer | ❌ | — | Lọc từ năm |
| `yearTo` | Integer | ❌ | — | Lọc đến năm |
| `limit` | int | ❌ | 20 | Số lượng tối đa, tối đa 100 |

### Response

```json
[
  {
    "openAlexId": "W3045678901",
    "title": "GPT-4 Technical Report",
    "year": 2023,
    "doi": "10.48550/arXiv.2303.08774",
    "citations": 5000,
    "localPaperId": null,
    "existsLocally": false
  },
  {
    "openAlexId": "W3012345678",
    "title": "LLaMA: Open and Efficient Foundation Language Models",
    "year": 2023,
    "doi": "10.48550/arXiv.2302.13971",
    "citations": 3500,
    "localPaperId": "108",
    "existsLocally": true
  }
]
```

### Chiến lược dữ liệu

- **Real-time query**: Mỗi lần gọi API, hệ thống query trực tiếp OpenAlex bằng filter `cites:W...`. Không lưu cache vì số lượng citations thay đổi liên tục.
- **Sort mapping**: `citations` → `cited_by_count:desc`, `recent` → `publication_date:desc`.
- **Year filter**: Sử dụng OpenAlex filter `from_publication_date` / `to_publication_date`.
- **Cross-reference**: Tương tự References Graph, kiểm tra citing works có tồn tại trong DB local không.

### Không tạo bảng DB mới

Citation Graph không cần bảng mới vì dữ liệu được fetch real-time từ OpenAlex.

---

## So Sánh Hai Chức Năng

| Khía cạnh | References Graph | Citation Graph |
|-----------|------------------|----------------|
| **Hướng quan hệ** | Paper → Cited papers | Citing papers → Paper |
| **Nguồn dữ liệu** | `referenced_works` (cached) | `filter=cites:W...` (real-time) |
| **Lưu DB** | ✅ `paper_references` + `reference_metadata` | ❌ Không lưu |
| **Pre-populate** | ✅ Khi sync | ❌ |
| **Tốc độ lần đầu** | Chậm (lazy fetch + cache) | Phụ thuộc OpenAlex (~1-2s) |
| **Tốc độ lần sau** | Nhanh (cache hit) | Phụ thuộc OpenAlex (~1-2s) |
| **Sort** | Không | `citations` / `recent` |
| **Filters** | `limit` | `sort`, `yearFrom`, `yearTo`, `limit` |
| **Limit mặc định** | 50 | 20 |
| **Limit tối đa** | Không giới hạn | 100 |

---

## Kiến Trúc Tổng Thể

```
┌─────────────────────────────────────────────────────────┐
│                     Frontend                            │
│   Paper Detail → References Tab / Citations Tab         │
└────────────┬─────────────────────┬──────────────────────┘
             │                     │
    GET /references        GET /citations
             │                     │
┌────────────▼─────────────────────▼──────────────────────┐
│              HelixPapersController                       │
└────────────┬─────────────────────┬──────────────────────┘
             │                     │
┌────────────▼─────────────────────▼──────────────────────┐
│            PaperReferenceServiceImpl                     │
│                                                          │
│  getReferences()              getCitations()             │
│  ├── Lookup paper_references  ├── Lookup paper           │
│  ├── Lazy fetch (if empty)    ├── Map sort param         │
│  ├── Batch metadata cache     ├── OpenAlex real-time     │
│  ├── Cross-ref local papers   ├── Cross-ref local papers │
│  └── Return nodes             └── Return nodes           │
└────────────┬─────────────────────┬──────────────────────┘
             │                     │
┌────────────▼─────────────────────▼──────────────────────┐
│                   OpenAlexClient                         │
│                                                          │
│  fetchWorksByIds()     fetchCitingWorks()                 │
│  extractReferencedWorkIds()                              │
└──────────────────────────────────────────────────────────┘
```

---

## Files Liên Quan

### Entities
- `entity/PaperReference.java` — Quan hệ paper → referenced works
- `entity/ReferenceMetadata.java` — Cache metadata referenced works

### Repositories
- `repository/PaperReferenceRepository.java`
- `repository/ReferenceMetadataRepository.java`
- `repository/PaperRepository.java` — Thêm `findBySourceTypeAndSourceIdentifierIn()`

### Service
- `service/PaperReferenceService.java` — Interface
- `service/impl/PaperReferenceServiceImpl.java` — Implementation

### Controller
- `controller/helix/HelixPapersController.java` — 2 endpoints mới

### Integration
- `integration/openalex/OpenAlexClient.java` — 3 methods mới
- `integration/model/ExternalPaperMetadata.java` — Thêm `referencedWorkIds`

### DTOs
- `dto/helix/HelixDtos.java` — `HelixReferenceNode`, `HelixCitationNode`

### Sync
- `service/impl/PaperSyncServiceImpl.java` — Pre-populate references khi sync

---

## Đánh Giá

### Ưu điểm

1. **Hiệu quả về API calls**: References dùng batch fetch + cache, giảm tối đa lượng request tới OpenAlex.
2. **Linh hoạt**: Citations hỗ trợ sort + filter year range, đáp ứng nhiều nhu cầu tìm kiếm.
3. **Cross-reference thông minh**: Tự động nhận diện papers đã có trong hệ thống, cho phép frontend điều hướng trực tiếp.
4. **Backward compatible**: `ExternalPaperMetadata` giữ các constructor cũ, không ảnh hưởng code hiện tại.
5. **Pre-populate khi sync**: Giảm thời gian chờ khi user mở references graph lần đầu.

### Hạn chế hiện tại

1. **Citations không cache**: Mỗi lần mở Citation Graph đều gọi OpenAlex API (~1-2s delay). Có thể cải thiện bằng TTL cache trong tương lai.
2. **Rate limit**: Nếu nhiều user đồng thời mở Citation Graph, có thể chạm ngưỡng 10 req/s của OpenAlex. Cần monitor.
3. **Frontend chưa có**: Backend đã sẵn sàng, cần phía frontend triển khai graph visualization (React Flow / Cytoscape.js).

---

## Cơ Chế Tránh Trùng Lặp & Xung Đột Concurrent (Unique Constraints)

Nhằm đảm bảo tính an toàn dữ liệu dưới các yêu cầu đồng thời (concurrent requests) hoặc dữ liệu đầu vào chứa phần tử trùng lặp:

1. **Deduplication đầu vào**:
   - Mọi danh sách OpenAlex IDs trước khi lưu vào DB đều được loại bỏ trùng lặp bằng `.distinct()` trong Java Stream.
2. **Double check trong Transaction trước khi lưu (Read-before-Write)**:
   - Trước khi lưu danh sách references mới cho một bài báo, hệ thống query cơ sở dữ liệu để tìm những liên kết đã tồn tại và chỉ chèn thêm các liên kết mới.
   - Trước khi lưu metadata của các referenced works (bảng `reference_metadata`), hệ thống thực hiện truy vấn `findByOpenAlexIdIn` để tìm các bản ghi đã được lưu bởi các luồng/yêu cầu song song khác, sau đó loại bỏ chúng ra khỏi danh sách `toSave`.
3. **Cách ly lỗi (Error Isolation)**:
   - Các lệnh chèn/cập nhật dữ liệu vào database được chạy thông qua `TransactionTemplate` được cấu hình `Propagation.REQUIRES_NEW` (giao dịch con độc lập) và được bao bọc bởi khối lệnh `try-catch`.
   - Nếu xảy ra vi phạm ràng buộc unique (do Race Condition cực kỳ sát nhau), chỉ giao dịch con bị rollback. Giao dịch chính bên ngoài không bị đánh dấu lỗi `rollback-only`, cho phép hệ thống tải lại dữ liệu đã lưu trữ thành công từ luồng khác và phục vụ API bình thường mà không gây ra lỗi `Transaction silently rolled back`.

