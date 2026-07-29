# Business Rules — JournalTrend

Tài liệu tổng hợp **toàn bộ quy tắc nghiệp vụ** đang được hệ thống thực thi, đối chiếu trực tiếp với code
(`journal-trend-be`) và frontend (`journal-trend-fe`).

## Cách đọc tài liệu

| Ký hiệu | Ý nghĩa |
|---|---|
| **BR-04, BR-38, BR-39, BR-42, BR-43, BR-44, BR-05, BR-06, BR-09, BR-10, BR-17, BR-35, BR-50, BR-55, BR-56, BR-57, BR-70, BR-71, BR-97, BR-104, BR-105** | Mã có sẵn trong SRS/PRD gốc, đã được trích dẫn trong code — **giữ nguyên số hiệu**. |
| **BR-2xx trở lên** | Quy tắc đã được cài đặt trong code nhưng **chưa có mã trong tài liệu gốc** — mã bổ sung ở dải riêng để không đụng độ với SRS. |
| 🔧 | Giá trị **cấu hình được** qua `application.yml` / biến môi trường (xem §14). |
| ⚠️ | Điểm lệch giữa tài liệu và code, hoặc quy tắc thực thi chưa đầy đủ (xem §15). |

Trạng thái (enum) tham chiếu trong tài liệu: `UserRole`, `UserStatus`, `PaperStatus`, `PaperReviewStatus`,
`SyncStatus`, `RoleRequestStatus`, `NotificationTriggerType`, `ForecastCategory`.

---

## 1. Tài khoản & Đăng ký

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-201 | Email là định danh duy nhất của tài khoản, so sánh **không phân biệt hoa/thường**; email được chuẩn hoá về chữ thường + trim trước khi lưu. Đăng ký trùng email → `400 Email is already registered`. | `AuthServiceImpl.register` |
| BR-202 | Người dùng tự đăng ký **chỉ được chọn** `STUDENT`, `LECTURER`, `RESEARCHER`. Gửi `ADMIN`/`SUPER_ADMIN`/null → `400`. | `AuthServiceImpl.register` |
| BR-203 | Mật khẩu phải **≥ 8 ký tự, có ≥ 1 chữ hoa và ≥ 1 chữ số** (regex `^(?=.*[A-Z])(?=.*\d).{8,}$`). Họ tên ≤ 150 ký tự. | `PasswordValidator`, `RegisterRequest` |
| BR-204 | Mật khẩu **luôn được hash** (BCrypt) trước khi lưu; không lưu plaintext ở bất kỳ đâu, kể cả bảng lịch sử mật khẩu. | `AuthServiceImpl`, `PasswordHistory` |
| BR-205 | Tài khoản mới tạo ở trạng thái `enabled = false`, `verified = false`, `status = ACTIVE` — **chưa dùng được** cho tới khi xác thực email. | `AuthServiceImpl.register` |
| BR-206 | Người dùng chỉ được tự sửa **họ tên** và 4 tuỳ chọn thông báo (`notifyKeywords`, `notifyAuthors`, `notifyJournals`, `notifyEmail`). Email và role **không tự sửa được**. | `AuthServiceImpl.updateProfile` / `updateNotificationPreferences` |

## 2. Xác thực email

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-207 | Sau khi đăng ký, hệ thống sinh token xác thực dạng UUID và gửi email tự động. | `EmailVerificationServiceImpl.createVerificationToken` |
| BR-208 | 🔧 Token xác thực email hết hạn sau **1440 phút (24 giờ)**. | `app.email-verification-expiration-minutes` |
| BR-209 | Token xác thực là **dùng một lần**: đã dùng → `Mã xác thực này đã được sử dụng`; hết hạn → `Mã xác thực đã hết hạn`. | `EmailVerificationServiceImpl.verifyToken` |
| BR-210 | Xác thực thành công ⇒ `enabled = true` **và** `verified = true`. | `EmailVerificationServiceImpl.verifyToken` |
| BR-211 | Gửi lại email xác thực sẽ **vô hiệu hoá toàn bộ token cũ chưa dùng** rồi mới sinh token mới. Tài khoản đã verified → `400`. | `EmailVerificationServiceImpl.resendVerificationToken` |
| BR-212 | `ADMIN` và `SUPER_ADMIN` **luôn được coi là đã xác thực email**, không bị chặn đăng nhập vì lý do này. | `EmailVerificationServiceImpl.isUserVerified`, `AuthServiceImpl.login` |

## 3. Đăng nhập, phiên và token

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-213 | Thứ tự kiểm tra khi đăng nhập: (1) tồn tại email → (2) đã verified (trừ admin) → (3) `status = ACTIVE` → (4) đúng mật khẩu. Chưa verified trả mã lỗi `EMAIL_NOT_VERIFIED` để FE điều hướng sang màn hình xác thực. | `AuthServiceImpl.login` |
| BR-214 | Tài khoản `status = LOCKED` **không đăng nhập được** (`Account is locked`), kể cả khi mật khẩu đúng. | `AuthServiceImpl.login` |
| BR-215 | 🔧 Access token sống **15 phút**; refresh token sống **7 ngày**. | `app.jwt.*-expiration-ms` |
| BR-216 | 🔧 **Idle timeout 30 phút**: refresh token không được dùng quá 30 phút liên tục sẽ bị thu hồi (`Session expired due to inactivity`). Mỗi lần refresh thành công sẽ "chạm" lại mốc idle → cửa sổ trượt (sliding window). | `AuthServiceImpl.refresh`, `app.jwt.refresh-idle-timeout-ms` |
| BR-217 | Mỗi user chỉ giữ **một refresh token còn hiệu lực**: đăng nhập mới xoá refresh token cũ ⇒ đăng nhập nơi khác sẽ đá phiên cũ ra. | `AuthServiceImpl.buildAuthResponse` |
| BR-218 | Refresh token bị **thu hồi ngay** khi: hết hạn, sai chữ ký, hoặc vượt idle timeout. | `AuthServiceImpl.refresh` |
| BR-219 | Đăng xuất là **idempotent**: token rỗng hoặc không tồn tại vẫn trả về thành công, không báo lỗi. | `AuthServiceImpl.logout` |
| BR-220 | FE tự đăng xuất khi **không thao tác 30 phút** (đồng bộ với BR-216), theo dõi qua `localStorage["helix.last_activity"]`. | `journal-trend-fe/.../auth/idle-timer.ts` |

## 4. Đổi & khôi phục mật khẩu

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-221 | 🔧 Token reset mật khẩu hết hạn sau **30 phút**, **dùng một lần** (`used = true` sau khi đổi). | `AuthServiceImpl.forgotPassword` / `resetPassword` |
| BR-222 | API "quên mật khẩu" **không tiết lộ email có tồn tại hay không** — email lạ vẫn trả về thành công (chống dò tài khoản). | `AuthServiceImpl.forgotPassword` |
| BR-223 | 🔧 **Không được tái sử dụng 3 mật khẩu gần nhất** (gồm mật khẩu hiện tại + 2 mật khẩu cũ). Áp dụng cho **cả** đổi mật khẩu và reset qua email. | `AuthServiceImpl.enforcePasswordHistory`, `app.password-history-count` |
| BR-224 | Lịch sử mật khẩu chỉ giữ đúng `N-1` bản ghi mới nhất; bản cũ hơn bị xoá sau mỗi lần đổi. | `AuthServiceImpl.archiveOldPassword` |
| BR-225 | Đổi mật khẩu (khi đang đăng nhập) bắt buộc nhập đúng **mật khẩu hiện tại**. | `AuthServiceImpl.changePassword` |
| BR-226 | Sau khi đổi/reset mật khẩu, **toàn bộ refresh token của user bị xoá** ⇒ mọi thiết bị phải đăng nhập lại. | `AuthServiceImpl.changePassword` / `resetPassword` |

## 5. Phân quyền (RBAC)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-227 | Hệ thống có 5 vai trò theo thứ tự quyền tăng dần: `STUDENT`, `LECTURER`, `RESEARCHER`, `ADMIN`, `SUPER_ADMIN`. | `UserRole` |
| BR-228 | **Không cần đăng nhập** (public): `/health`, `/api/v1/auth/**`, `/api/auth/**`, `/auth/verify`, Swagger/API docs, và các endpoint **GET** tra cứu: papers, topics, authors, journals, analytics, dashboard, trends, search suggestions. | `SecurityConfig` |
| BR-229 | `/api/v1/admin/**` và `/api/admin/**` yêu cầu `ADMIN` **hoặc** `SUPER_ADMIN`; `/api/v1/super-admin/**` yêu cầu **`SUPER_ADMIN`**; mọi request còn lại phải đăng nhập. | `SecurityConfig` |
| BR-06 | **Chỉ Admin/Super Admin** được kích hoạt đồng bộ dữ liệu thủ công và các thao tác vận hành (reset sync treo, recalculate trend, backfill, repair metadata, kiểm duyệt paper). | `AdminController`, `SecurityConfig` |
| BR-230 | Chỉ `SUPER_ADMIN` được cấp/thu hồi quyền `ADMIN`. Thu hồi quyền admin ⇒ user trở về vai trò `RESEARCHER`. | `SuperAdminServiceImpl.grantAdmin` / `revokeAdmin` |
| BR-231 | **Không ai được đổi vai trò của chính mình**, và không thể thu hồi/hạ cấp tài khoản `SUPER_ADMIN`. | `SuperAdminServiceImpl.updateRole` / `revokeAdmin` |
| BR-232 | **Không được khoá tài khoản `SUPER_ADMIN`**. Khoá = `status → LOCKED`, mở khoá = `status → ACTIVE`. | `AdminServiceImpl.lockUser` / `unlockUser` |
| BR-233 | Hệ thống tự seed tài khoản quản trị lúc khởi động (`admin@research.local` — SUPER_ADMIN, và `admin@helix.io`). ⚠️ Mật khẩu mặc định nằm trong source code — **phải đổi trước khi lên production**. | `DataInitializer` |

## 6. Đơn xin đổi vai trò (Role Upgrade Request)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-234 | User chỉ được xin đổi sang `STUDENT`, `RESEARCHER`, `LECTURER` — **không được xin thẳng lên `ADMIN`/`SUPER_ADMIN`**. | `RoleManagementServiceImpl.submitRequest` |
| BR-235 | Không được xin đúng vai trò đang có, và **mỗi user chỉ có tối đa 1 đơn `PENDING`** tại một thời điểm. | `RoleManagementServiceImpl.submitRequest` |
| BR-236 | Chỉ đơn đang `PENDING` mới được duyệt/từ chối; đơn đã xử lý → `400 This request has already been reviewed`. Đơn được **khoá pessimistic** khi duyệt để chặn 2 admin duyệt cùng lúc (double-approve). | `RoleManagementServiceImpl.getPendingRequest` |
| BR-237 | **Admin không được tự duyệt/từ chối đơn của chính mình.** | `RoleManagementServiceImpl.requireOperator` |
| BR-238 | Khi duyệt: cập nhật role của user + ghi **`RoleChangeLog`** (ai đổi, từ role nào sang role nào, lý do) + gửi thông báo cho user. Mọi thay đổi vai trò đều để lại vết audit. | `RoleManagementServiceImpl.approve` |
| BR-239 | Khi từ chối với lý do `OTHER`, **bắt buộc** nhập lý do tự do; user nhận được thông báo kèm lý do. | `RoleManagementServiceImpl.reject` |
| BR-240 | 🔧 Vòng đời đơn: đơn `PENDING` quá **30 ngày** và đơn đã xử lý quá **7 ngày** sẽ bị **xoá tự động**. | `RoleManagementServiceImpl.purgeExpiredRequests` |

## 7. Thu thập dữ liệu (Sync / OpenAlex)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| **BR-04** | Hệ thống **chỉ lưu metadata** (title, abstract, keywords, năm, tác giả, journal, DOI, số trích dẫn). **Không tải/không cache full-text PDF** vì lý do bản quyền — chỉ lưu *đường dẫn* OA (`pdfUrl`) trỏ về nguồn gốc. | `PaperSyncServiceImpl`, `Paper` |
| **BR-05** | 🔧 Đồng bộ tự động chạy theo cron **02:00 hằng ngày** (`0 0 2 * * *`), có thể bật/tắt toàn cục bằng `scheduler-enabled`. | `DataSyncScheduler`, `app.sync.cron` |
| BR-241 | Đồng bộ lúc khởi động app **mặc định TẮT** (`sync.on-startup = false`). | `app.sync.on-startup` |
| **BR-09 / BR-71** | **Khử trùng lặp** paper theo 2 khoá: `(source_type, source_identifier)` — có unique index — **và** DOI (so sánh lowercase + trim). Việc khử trùng áp dụng cả *giữa các lần sync* lẫn *trong cùng một batch* đang xử lý. | `PaperSyncServiceImpl` |
| BR-242 | Một bản ghi từ nguồn ngoài chỉ được nạp khi có **đủ title + publicationDate + DOI**; thiếu bất kỳ trường nào → bỏ qua. | `PaperSyncServiceImpl` |
| BR-243 | 🔧 Chỉ nạp bài công bố **từ `from-publication-date` trở về sau**, có **cửa sổ chồng lấn 7 ngày** để không sót bài giữa 2 lần chạy. | `app.sync.from-publication-date`, `overlap-days` |
| BR-244 | 🔧 Hạn mức mỗi lần chạy: tối đa **40 trang**/query, **1000 paper**/lần chạy, commit theo lô **25 bài**. | `app.sync.max-pages`, `max-papers-per-run`, `ingest-batch-size` |
| BR-245 | 🔧 **Dừng sớm (early stopping)**: nếu **3 trang liên tiếp** không có bài mới thì ngừng crawl query đó, để lần sync sau tiếp tục. | `PaperSyncServiceImpl`, `early-stop-consecutive-empty-pages` |
| BR-246 | 🔧 Gọi OpenAlex có **retry 3 lần**, timeout kết nối 10s / đọc 30s; luôn gửi `mailto` theo chính sách polite-pool của OpenAlex. | `OpenAlexClient`, `RestClientConfig` |
| BR-247 | 🔧 Mỗi paper chỉ gắn tối đa **10 keyword**, và **chỉ nhận keyword thuộc danh sách domain cho phép** (Computer Science, AI, ML, Robotics, Software Engineering, …) — đúng phạm vi sản phẩm là CS & AI. | `app.sync.max-keywords-per-paper`, `allowed-keyword-domains` |
| BR-248 | Mỗi lần chạy tạo một `SyncLog` với trạng thái `RUNNING → SUCCESS \| FAILED`, ghi lại số bài nạp được. **Không chạy 2 sync song song.** | `PaperSyncServiceImpl` |
| BR-249 | 🔧 Sync kẹt ở `RUNNING` quá **60 phút** bị coi là treo; admin gọi `POST /api/admin/sync/reset-stale` để đánh dấu `FAILED` và mở khoá. | `app.sync.stale-sync-minutes` |
| BR-250 | Sau mỗi lần sync thành công, hệ thống **tự động**: tính lại trend → backfill 12 tháng lịch sử 🔧 → hết hạn các paper chờ duyệt quá SLA. | `PaperSyncServiceImpl`, `trend-backfill-months` |
| BR-251 | Admin có thể **cấu hình nguồn API** (`ApiSourceConfig`) bật/tắt từng nguồn mà không cần deploy lại. ⚠️ Hiện chỉ OpenAlex được cài đặt thật (xem §15). | `ApiSourceServiceImpl`, `DataInitializer.seedApiSources` |
| BR-252 | Metadata thiếu (`publicationDate` hoặc `abstract`) có thể được **vá lại** bằng cách fetch lại theo DOI/sourceIdentifier — chỉ điền chỗ trống, không ghi đè dữ liệu đã có. | `PaperMetadataRepairServiceImpl` |

## 8. Kiểm duyệt & chất lượng metadata

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| **BR-10** | Khi nguồn ngoài trả về bản ghi **đã tồn tại**, hệ thống cập nhật **chỉ các trường đang trống** (abstract, DOI, publicationDate, sourceUrl) — **không ghi đè** dữ liệu đã có. Riêng `citationCount` luôn được cập nhật theo số mới nhất. | `PaperReviewServiceImpl.enrichEmptyFieldsOnly` |
| **BR-17** | Nếu **title mới mâu thuẫn đáng kể** với title hiện tại, paper bị gắn `reviewStatus = PENDING_REVIEW` + lưu lại bản conflict (title/abstract/nguồn) + mốc thời gian flag, **thay vì tự động ghi đè**. | `PaperReviewServiceImpl.applyIncomingMetadata` |
| BR-253 | Hai title được coi là **giống nhau** (không tính là conflict) khi: chuỗi này chứa chuỗi kia, **hoặc** khoảng cách Levenshtein < **25%** độ dài chuỗi dài hơn. | `PaperReviewServiceImpl.isSimilarTitle` |
| **BR-104** | Admin xử lý paper `PENDING_REVIEW` theo 2 hướng: **ACCEPT** (giữ dữ liệu hiện tại) hoặc **OVERRIDE** (ghi đè bằng bản conflict hoặc giá trị admin tự nhập). Cả hai đều đưa paper về `NONE` và **ghi audit** (`PaperReviewAudit`: ai, hành động, ghi chú, thời điểm). | `PaperReviewServiceImpl.accept` / `override` |
| BR-254 | Chỉ paper đang ở `PENDING_REVIEW` mới được accept/override; trạng thái khác → `400`. | `PaperReviewServiceImpl.getPendingPaper` |
| **BR-97** | 🔧 Paper chờ duyệt quá **30 ngày** tự chuyển sang `EXPIRED` (kèm audit `EXPIRED`) và **bị loại khỏi trend/tìm kiếm**. | `PaperReviewServiceImpl.expireStalePendingReviews`, `PaperReviewMaintenanceScheduler` |
| BR-255 | Paper đã `EXPIRED` **không nhận cập nhật metadata** từ các lần sync sau. | `PaperReviewServiceImpl.applyIncomingMetadata` |
| **BR-105** | Admin xem được danh sách paper theo trạng thái kiểm duyệt, lọc theo khoảng thời gian bị flag. | `AdminController.listPapersForReview` |
| BR-256 | **Quy tắc hiển thị chung**: chỉ paper `status = ACTIVE` **và** `reviewStatus = NONE` mới được tính vào trend, gợi ý tìm kiếm và mới được lưu vào collection. | `KeywordTrendServiceImpl`, `SearchSuggestionServiceImpl`, `CollectionServiceImpl` |
| BR-257 | Admin **xoá mềm** paper (`status = DELETED`) — dữ liệu vẫn nằm trong DB phục vụ truy vết, nhưng biến mất khỏi mọi API người dùng. | `AdminServiceImpl.softDeletePaper` |

## 9. Tính toán xu hướng (Trend)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| **BR-38** | **Công thức điểm xu hướng:**  `TrendScore = (SốBàiThángNay − SốBàiThángTrước) / SốBàiThángTrước × 100%`, làm tròn 2 chữ số thập phân. | `KeywordTrendServiceImpl.calculateTrendScore` |
| BR-258 | Trường hợp biên của BR-38: tháng trước **= 0 bài** và tháng này **> 0** ⇒ điểm quy ước **+100%** (tránh chia cho 0); cả hai tháng đều 0 ⇒ điểm **0%**. | `KeywordTrendServiceImpl.calculateTrendScore` |
| BR-259 | Chỉ đếm paper `ACTIVE` + `reviewStatus = NONE` khi tính trend (xem BR-256). | `KeywordTrendServiceImpl.recalculateMonth` |
| BR-260 | Kết quả được lưu sẵn (denormalize) vào `publication_trends`, **upsert** theo khoá duy nhất `(keyword_id, year, month)` — dashboard đọc thẳng bảng này thay vì tính lại. | `PublicationTrend`, `KeywordTrendServiceImpl` |
| BR-261 | Mỗi lần chạy lại, hệ thống tính cho **tháng trước và tháng hiện tại**; `Keyword.trendScore` / `paperCount` chỉ được cập nhật theo **tháng trước** (tháng đã "chốt sổ" đủ dữ liệu). | `KeywordTrendServiceImpl.recalculateAll` / `recalculateMonth` |
| BR-262 | Vì tháng hiện tại chưa đủ dữ liệu, mọi API "xu hướng hiện tại" đều lấy mốc là **tháng liền trước**. | `KeywordTrendServiceImpl.getCurrentMonthTrend`, `resolveTargetMonth` |
| BR-263 | Backfill lịch sử tối đa **36 tháng** một lần gọi. | `KeywordTrendServiceImpl.backfillHistoricalMonths` |
| **BR-44** | **Định nghĩa "Trending Topic"**: keyword có TrendScore ≥ ngưỡng trong N tháng **liên tiếp** gần nhất. | `KeywordTrendServiceImpl.findTrendingKeywords` |
| **BR-39** | 🔧 Ngưỡng trending = **≥ 15%**. | `trending-threshold-percent` |
| **BR-42** | 🔧 Số tháng liên tiếp phải đạt ngưỡng = **3 tháng**. | `trending-consecutive-months` |
| **BR-43** | 🔧 Keyword phải có tối thiểu **5 bài** mới được xếp hạng trending. ⚠️ Hiện chỉ áp dụng ở bảng xếp hạng theo tháng, chưa áp dụng trong `findTrendingKeywords` (xem §15). | `min-keyword-papers`, `findTrendingKeywordResponses` |
| **BR-50** | 🔧 TrendScore **≥ 300%** trong một tháng được gắn nhãn **Anomaly** (tăng bất thường) ở màn hình quản trị. | `anomaly-threshold-percent`, `TrendDemoStatsServiceImpl` |
| BR-264 | Bảng xếp hạng theo tháng sắp theo `deltaPercent` giảm dần, **tie-break bằng số bài**; xếp hạng theo tháng **không** áp dụng luật 3-tháng-liên-tiếp để tháng nào cũng có bảng xếp hạng. | `KeywordTrendServiceImpl.findTrendingKeywordResponses` |
| BR-265 | API "top keyword" giới hạn tối đa **50** mục; nếu chưa có dữ liệu trend nào thì **fallback** sang xếp hạng theo tổng số bài. | `KeywordTrendServiceImpl.findTopByTrendScore` |
| BR-266 | Tham số `year`/`month` phải **truyền cùng nhau**; truyền thiếu hoặc không hợp lệ (vd. `month=13`) → lỗi `400` rõ ràng, không âm thầm bỏ qua filter. | `KeywordTrendServiceImpl.resolveTargetMonth` |

## 10. Dự báo hot topic (Future Trend Forecast)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-267 | 🔧 Job dự báo chạy **04:00 ngày 1 hằng tháng**; **không chạy song song** với chính nó hay với sync (dữ liệu trend đang được ghi dở sẽ cho kết quả sai). | `forecast-cron`, `FutureTrendForecastServiceImpl` |
| BR-268 | 🔧 Keyword phải có **≥ 6 tháng** lịch sử mới được dự báo; dùng tối đa **12 tháng** gần nhất làm cửa sổ hồi quy. | `forecast-min-months`, `forecast-history-window` |
| BR-269 | Keyword có **slope ≤ 0** (không tăng trưởng) **bị loại** khỏi danh sách dự báo. | `FutureTrendForecastServiceImpl` |
| BR-270 | 🔧 **Điểm tiềm năng sTPS** = `(slopeChuẩnHoá × 0.5 + accelerationChuẩnHoá × 0.3 + volumeChuẩnHoá × 0.2) × 100`, các thành phần chuẩn hoá Min-Max trên toàn bộ tập keyword hợp lệ. | `forecast-weight-slope/acc/volume` |
| BR-271 | Số bài dự báo cho từng tháng tương lai tính theo **hồi quy tuyến tính đơn** `y = slope×x + intercept`, chặn dưới tại 0; horizon tối đa **12 tháng** 🔧. | `FutureTrendForecastServiceImpl.buildForecast` |
| BR-272 | **Phân loại dự báo**: `EARLY_BOOM` khi sTPS ≥ 80 **và** gia tốc > 0; `BREAKOUT` khi sTPS ≥ 60; còn lại `STEADY`. | `ForecastCategory.classify` |
| BR-273 | 🔧 Chỉ lưu **top 200** keyword điểm cao nhất; kết quả của lần chạy trước bị xoá để bảng luôn phản ánh lần chạy mới nhất. | `forecast-max-keywords` |

## 11. Cá nhân hoá: Follow, Collection, Lịch sử tìm kiếm

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| **BR-55** | 🔧 Mỗi user follow tối đa **20 keyword**. | `FollowKeywordServiceImpl`, `max-follow-keywords-per-user` |
| **BR-56** | 🔧 Mỗi user follow tối đa **10 journal**. | `FollowJournalServiceImpl`, `max-follow-journals-per-user` |
| BR-274 | 🔧 Mỗi user follow tối đa **20 tác giả**. | `FollowAuthorServiceImpl`, `max-follow-authors-per-user` |
| BR-275 | **Không follow trùng** một đối tượng (→ `400 Already following…`); **unfollow là idempotent** (chưa follow vẫn trả thành công). | `Follow*ServiceImpl` |
| **BR-57** | 🔧 Mỗi user lưu tối đa **200 bài (distinct)** trên **tất cả** collection cộng lại — đếm theo paper duy nhất, không phải theo dòng. | `CollectionServiceImpl.addPaper`, `max-bookmark-papers-per-user` |
| BR-276 | Tên collection **bắt buộc** và **không trùng nhau trong cùng một user** (không phân biệt hoa/thường); tên khác user thì được trùng. | `CollectionServiceImpl.validateUniqueName` |
| BR-277 | Collection là **dữ liệu riêng tư**: mọi thao tác đọc/sửa/xoá đều lọc theo `userId`; truy cập collection của người khác → `404` (không tiết lộ sự tồn tại). | `CollectionServiceImpl.getOwnedCollection` |
| BR-278 | Chỉ paper `ACTIVE` + `reviewStatus = NONE` mới được thêm vào collection; thêm lại bài đã có trong collection **không báo lỗi và không tính thêm quota**. | `CollectionServiceImpl.addPaper` |
| BR-279 | Xoá collection sẽ xoá toàn bộ liên kết bài trong đó; **bản thân paper không bị xoá**. | `CollectionServiceImpl.delete` |
| BR-280 | Danh sách bài trong collection **tự lọc bỏ** paper đã bị xoá mềm. | `CollectionServiceImpl.listPapers` |
| BR-281 | Lịch sử tìm kiếm **gộp trùng** theo `(user, loại tìm kiếm, từ khoá)` — tìm lại cùng một thứ chỉ cập nhật thời điểm để đẩy lên đầu, không tạo dòng mới. Query rỗng không được ghi. | `SearchHistoryServiceImpl.recordSearch` |
| BR-282 | Chỉ trả về **10 lượt tìm kiếm gần nhất**. | `SearchHistoryServiceImpl` (`RECENT_SEARCH_LIMIT = 10`) |
| **BR-35** | Gợi ý autocomplete gộp 3 loại (**keyword / paper / author**) và **xen kẽ** để danh sách đa dạng, mỗi loại lấy tối thiểu 3 mục; query rỗng trả về danh sách rỗng. | `SearchSuggestionServiceImpl` |

## 12. Thông báo (Notification)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-283 | Thông báo chỉ được tạo cho user **đang follow** đối tượng liên quan **và** đã **bật đúng loại thông báo** (`notifyKeywords` / `notifyAuthors` / `notifyJournals`); email chỉ gửi khi bật `notifyEmail`. | `NotificationServiceImpl` |
| **BR-71** | **Không gửi trùng**: đã có thông báo cho cặp `(user, keyword, triggerType)` thì bỏ qua, không thông báo lại cho cùng một sự kiện. | `NotificationServiceImpl.notifyTrendingForFollowedKeywords` |
| BR-284 | User chỉ đọc/đánh dấu đã đọc thông báo **của chính mình**; thông báo của người khác → `404`. | `NotificationServiceImpl.markAsRead` |
| BR-285 | Danh sách thông báo luôn sắp **mới nhất trước** và có phân trang. | `NotificationServiceImpl.listForCurrentUser` |
| BR-286 | Hệ thống thông báo cho user khi đơn xin đổi role được **duyệt** hoặc bị **từ chối** (kèm lý do). | `NotificationServiceImpl.notifyRoleRequest*` |
| **BR-70** | 🔧 Thông báo được **giữ 90 ngày**, sau đó bị xoá tự động bởi job dọn dẹp. | `NotificationPurgeScheduler`, `notification-retention-days` |

## 13. Phân tích AI (Groq)

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-287 | Mọi tính năng AI **yêu cầu `GROQ_API_KEY`**; chưa cấu hình → lỗi rõ ràng thay vì gọi ra ngoài. 🔧 Model mặc định `llama-3.3-70b-versatile`, giới hạn output 1024 token. | `AiAnalysisServiceImpl`, `app.groq.*` |
| BR-288 | Chỉ **metadata** được gửi cho AI (title, abstract, năm, journal, citations, keywords, authors) — nhất quán với BR-04. | `AiAnalysisServiceImpl.buildCollectionPrompt` |
| BR-289 | Phân tích 1 keyword yêu cầu keyword **đã có dữ liệu trend**; chưa có → `400`. Kết quả gồm `verdict` (GROWING/STABLE/DECLINING) + `feasibilityScore` 0–100 + khuyến nghị. | `AiAnalysisServiceImpl.analyzeTrend` |
| BR-290 | Phân tích "top trends" không truyền keyword ⇒ **mặc định lấy 10 keyword trending đầu tiên**; không có keyword trending nào → `400`. | `AiAnalysisServiceImpl.analyzeTopTrends` |
| BR-291 | Phân tích collection: **chỉ chủ sở hữu** được gọi; collection rỗng → `400`. | `AiAnalysisServiceImpl.analyzeCollection` |
| BR-292 | 🔧 Số bài tối đa cho một lượt phân tích collection = **30** (mặc định), **admin chỉnh được lúc chạy** qua bảng `ai_collection_analysis_settings` (1 dòng, có ghi nhận admin sửa gần nhất) mà không cần deploy lại. | `AiCollectionAnalysisSettingService`, `max-papers-for-collection-analysis` |
| BR-293 | Chọn nhiều hơn hạn mức → `400`; các bài được chọn **phải thuộc collection đó**; không truyền danh sách ⇒ tự lấy N bài lưu gần nhất. | `AiAnalysisServiceImpl.analyzeCollection` |
| BR-294 | Nhà cung cấp AI trả `429` ⇒ lỗi nghiệp vụ riêng `AiQuotaExhaustedException` với thông điệp "thử lại sau vài phút", **không** trả 500. | `AiAnalysisServiceImpl.callGroq` |
| BR-295 | AI trả JSON sai định dạng ⇒ hệ thống **không ném lỗi** mà trả về kết quả fallback an toàn (verdict mặc định + nội dung thô) để user vẫn xem được. | `AiAnalysisServiceImpl.parse*` |
| BR-296 | Lưu lịch sử phân tích AI là **non-fatal**: lỗi khi lưu history không được làm hỏng kết quả trả về cho user. | `AiAnalysisServiceImpl.safeSaveHistory` |
| BR-297 | ID paper do AI "bịa" ra bị **lọc bỏ**; tiêu đề hiển thị luôn lấy từ DB chứ không lấy từ output của AI. | `AiAnalysisServiceImpl.analyzeCollection` |

## 14. Xuất báo cáo, quản trị & giới hạn hệ thống

| Mã | Quy tắc | Thực thi tại |
|---|---|---|
| BR-298 | Xuất CSV xu hướng: **top 50 keyword** theo trend score. | `ReportExportServiceImpl.exportTopicTrendsCsv` |
| BR-299 | Xuất CSV danh sách paper: chỉ bài `ACTIVE`, sắp theo số trích dẫn giảm dần, **giới hạn kẹp trong khoảng 1–1000 dòng**. | `ReportExportServiceImpl.exportPapersCsv` |
| BR-300 | Giá trị CSV được escape đúng chuẩn (bọc ngoặc kép, nhân đôi dấu `"` bên trong) để không vỡ file khi tiêu đề chứa dấu phẩy. | `ReportExportServiceImpl.csv` |
| BR-301 | 🔧 **Rate limit 60 request/phút/IP** (Bucket4j) cho toàn bộ API — chống brute-force và lạm dụng. | `RateLimitFilter`, `app.rate-limit-per-minute` |
| BR-302 | 🔧 **CORS** chỉ mở cho các origin trong danh sách cấu hình (mặc định `localhost:5173`, `5174`). | `SecurityConfig`, `app.cors-allowed-origins` |
| BR-303 | API dùng **JWT stateless** (không session server-side); mọi request đọc Bearer token qua `JwtAuthenticationFilter`. | `SecurityConfig`, `JwtAuthenticationFilter` |
| BR-304 | Nhóm API `/api/helix/**` là **API nội bộ**, bị ẩn khỏi Swagger public (`@Hidden`). | `controller/helix/*` |
| BR-305 | Mọi response đều bọc trong `ApiResponse<T>`; lỗi được chuẩn hoá tập trung tại `GlobalExceptionHandler` (không lộ stacktrace ra client). | `ApiResponse`, `GlobalExceptionHandler` |

### Bảng tham số cấu hình ↔ Business Rule

| Tham số (`app.*`) | Mặc định | BR liên quan |
|---|---|---|
| `jwt.access-expiration-ms` | 900 000 (15 phút) | BR-215 |
| `jwt.refresh-expiration-ms` | 604 800 000 (7 ngày) | BR-215 |
| `jwt.refresh-idle-timeout-ms` | 1 800 000 (30 phút) | BR-216, BR-220 |
| `password-reset-expiration-minutes` | 30 | BR-221 |
| `email-verification-expiration-minutes` | 1440 (24 giờ) | BR-208 |
| `password-history-count` | 3 | BR-223 |
| `rate-limit-per-minute` | 60 | BR-301 |
| `sync.cron` | `0 0 2 * * *` | BR-05 |
| `sync.min-keyword-papers` | 5 | BR-43 |
| `sync.trending-threshold-percent` | 15 | BR-39 |
| `sync.trending-consecutive-months` | 3 | BR-42 |
| `sync.anomaly-threshold-percent` | 300 | BR-50 |
| `sync.max-follow-keywords-per-user` | 20 | BR-55 |
| `sync.max-follow-journals-per-user` | 10 | BR-56 |
| `sync.max-follow-authors-per-user` | 20 | BR-274 |
| `sync.max-bookmark-papers-per-user` | 200 | BR-57 |
| `sync.pending-review-expiry-days` | 30 | BR-97 |
| `sync.notification-retention-days` | 90 | BR-70 |
| `sync.role-request-pending-retention-days` | 30 | BR-240 |
| `sync.role-request-reviewed-retention-days` | 7 | BR-240 |
| `sync.max-pages` / `max-papers-per-run` / `ingest-batch-size` | 40 / 1000 / 25 | BR-244 |
| `sync.early-stop-consecutive-empty-pages` | 3 | BR-245 |
| `sync.max-keywords-per-paper` / `allowed-keyword-domains` | 10 / danh sách CS-AI | BR-247 |
| `sync.stale-sync-minutes` | 60 | BR-249 |
| `sync.trend-backfill-months` | 12 | BR-250, BR-263 |
| `sync.forecast-*` | xem §10 | BR-267 → BR-273 |
| `sync.max-papers-for-collection-analysis` | 30 | BR-292 |
| `groq.model` / `max-output-tokens` | llama-3.3-70b-versatile / 1024 | BR-287 |

---

## 15. ⚠️ Khoảng trống & điểm lệch giữa tài liệu và code

Những điểm dưới đây được phát hiện khi đối chiếu PRD/README với code hiện tại. Đây là **ghi nhận hiện trạng**,
cần quyết định: sửa code cho khớp tài liệu, hoặc cập nhật tài liệu cho khớp code.

| # | Nội dung | Hiện trạng |
|---|---|---|
| 1 | **BR-43 (tối thiểu 5 bài) chưa áp dụng đồng nhất.** `findTrendingKeywords` (nguồn dữ liệu cho thông báo trending và cho AI top-trends) chỉ kiểm tra ngưỡng % × số tháng liên tiếp, **không lọc theo số bài tối thiểu**. Bộ lọc `min-keyword-papers` chỉ có ở `findTrendingKeywordResponses`, và lọc theo **số bài trong tháng** chứ không phải tổng số bài của keyword. | Lệch so với định nghĩa "Trending Topic" trong PRD |
| 2 | **Xuất PDF chưa được cài đặt.** PRD yêu cầu xuất CSV *hoặc* PDF; `ReportExportServiceImpl` chỉ sinh CSV. | Thiếu tính năng |
| 3 | **Chỉ có OpenAlex.** PRD/README nêu Semantic Scholar + OpenAlex + Crossref; code chỉ có `OpenAlexClient`. `ApiSourceConfig` cho phép khai báo nguồn nhưng không có client tương ứng. | Thiếu tính năng |
| 4 | **BR-38 chưa định nghĩa trường hợp chia cho 0.** Code quy ước +100% khi tháng trước = 0 (BR-258) — hệ quả: keyword mới xuất hiện luôn đạt ngưỡng 15%, dễ lọt vào danh sách trending. | Quy tắc ngầm, nên đưa vào SRS |
| 5 | **`PaperReviewStatus.RESOLVED` không được dùng.** Sau khi admin accept/override, code đặt trạng thái về `NONE` chứ không phải `RESOLVED` ⇒ không phân biệt được "chưa từng có conflict" và "đã xử lý conflict". | Enum thừa / mất thông tin audit |
| 6 | **Tài khoản seed có mật khẩu hard-code** trong `DataInitializer` (`Admin@12345`, `admin12345`). | Rủi ro bảo mật khi lên production |
| 7 | **Hạn mức bookmark (BR-57) chỉ kiểm tra khi thêm bài vào collection.** Không có kiểm tra/đối soát ở luồng nào khác, và khi hạ `max-bookmark-papers-per-user` xuống thì user đang vượt hạn mức vẫn giữ nguyên dữ liệu cũ. | Quy tắc chỉ chặn chiều tăng |
| 8 | **Không có khoá chống đăng nhập sai nhiều lần.** PRD nói chống brute-force, nhưng cơ chế duy nhất là rate limit 60 req/phút/IP (BR-301); không có đếm số lần sai / khoá tạm tài khoản. | Thiếu lớp bảo vệ |

---

## Liên kết

- Yêu cầu sản phẩm: [PRD.md](PRD.md)
- Kiến trúc: [ARCHITECTURE.md](ARCHITECTURE.md) · [docs/backend-architecture.md](docs/backend-architecture.md)
- Luồng tính năng chi tiết: [docs/backend-features.md](docs/backend-features.md)
- Cấu hình: [docs/backend-config.md](docs/backend-config.md)
- Bảo mật: [docs/backend-security.md](docs/backend-security.md)
- Thực thể/DB: [docs/backend-entities.md](docs/backend-entities.md) · [DATABASE.md](DATABASE.md)
