# Tài Liệu Công Thức — Dự Báo Hot Topic Tương Lai (6 Tháng)

> **Dự án:** TrendSpark / JournalTrend  
> **Mục tiêu:** Với mỗi keyword, tính điểm tiềm năng và dự báo số bài báo cho 6 tháng tới.  
> **Lưu ý link:** Tất cả link Wikipedia và PDF truy cập miễn phí, không cần đăng nhập.

---

## Tổng Quan: Cần 5 Công Thức

```
[1] OLS Slope        →  keyword có đang tăng trưởng không?
[2] Acceleration     →  tốc độ tăng trưởng đang nhanh lên hay chậm lại?
[3] Volume Score     →  keyword có đủ lớn để đáng dự báo không?
[4] sTPS Score       →  xếp hạng tổng hợp (điểm tiềm năng 0-100)
[5] Forecast 6M      →  dự báo số bài báo cho từng tháng trong 6 tháng tới
```

---

## Đầu Vào Chung

```
Y   = [y_1, y_2, ..., y_12]   12 tháng lịch sử từ bảng publication_trends
n   = 12                       số tháng
x_i = i - 1                   chỉ số tháng: 0, 1, 2, ..., 11
```

---

## Công Thức 1 — OLS Slope (Tốc Độ Tăng Trưởng)

**Mục đích:** Đo tốc độ tăng trưởng trung bình của số bài báo trên 12 tháng.  
**Lọc:** Nếu `Slope <= 0` → keyword đang giảm hoặc đứng yên → **loại, không dự báo**.

```
x_bar = (0 + 1 + 2 + ... + 11) / 12  =  5.5
y_bar = (y_1 + y_2 + ... + y_12) / 12

S_xx  = sum[(x_i - x_bar)^2]           =  143   (cố định khi n=12)
S_xy  = sum[(x_i - x_bar) * (y_i - y_bar)]

Slope     = S_xy / S_xx
Intercept = y_bar - Slope * x_bar
```

**Ví dụ:** Slope = 3.5 → trung bình mỗi tháng keyword tăng thêm 3.5 bài báo.

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Simple Linear Regression | https://en.wikipedia.org/wiki/Simple_linear_regression | Mục **"Formulation and computation"** → phần đầu mục, công thức slope dưới heading *"Expanded formulas"* |
| Hyndman & Athanasopoulos — *Forecasting: Principles and Practice* (3rd ed., miễn phí) | https://otexts.com/fpp3/regression-intro.html | Chapter 7 *"Time series regression models"* — phần nền tảng cho OLS |

---

## Công Thức 2 — Acceleration (Gia Tốc Tăng Trưởng)

**Mục đích:** Phát hiện keyword đang **bùng nổ nhanh gần đây** — tốc độ tăng trưởng 6 tháng cuối cao hơn 6 tháng đầu.

**Cách tính:** Chia chuỗi 12 tháng làm 2 nửa bằng nhau (6/6), tính slope riêng từng nửa.

```
--- Nửa trước: tháng 1-6 ---
Y_prior      = [y_1, y_2, y_3, y_4, y_5, y_6]
x_prior      = [0, 1, 2, 3, 4, 5]

x_bar_prior  = 2.5
S_xx_prior   = 17.5   (cố định: n=6, S_xx = 6*(36-1)/12 = 17.5)
S_xy_prior   = sum[(x_i - 2.5) * (y_i - y_bar_prior)]  với i = 0..5

Slope_prior  = S_xy_prior / 17.5


--- Nửa sau: tháng 7-12 ---
Y_recent     = [y_7, y_8, y_9, y_10, y_11, y_12]
x_recent     = [0, 1, 2, 3, 4, 5]   (đặt lại chỉ số từ 0)

x_bar_recent = 2.5
S_xx_recent  = 17.5   (cố định)
S_xy_recent  = sum[(x_i - 2.5) * (y_i - y_bar_recent)]  với i = 0..5

Slope_recent = S_xy_recent / 17.5


--- Gia tốc ---
Acc = Slope_recent - Slope_prior
```

**Giải thích:**
- `Acc > 0` → keyword đang tăng nhanh hơn → tiềm năng cao
- `Acc < 0` → keyword đang tăng chậm lại → tiềm năng giảm

**Tại sao chia 6/6?**  
Chia đôi cân bằng (50/50) là nguyên tắc khách quan nhất.  
Chia 9/3 cho nửa sau quá ít điểm (3 tháng), dễ bị nhiễu ngắn hạn.

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Simple Linear Regression | https://en.wikipedia.org/wiki/Simple_linear_regression | Mục **"Formulation and computation"** — dùng lại cùng công thức OLS Slope, áp dụng cho từng nửa chuỗi |
| Chen, C. (2006). *CiteSpace II: Detecting and visualizing emerging trends*. JASIST, 57(3), 359–377. | **PDF miễn phí:** http://cluster.ischool.drexel.edu/~cchen/citespace/doc/jasist2006.pdf | **Section 3** *"Detecting emerging trends and transient patterns"* — phương pháp phát hiện "bùng nổ" theo khoảng thời gian trong bibliometric data |
| Chen (2006) — Semantic Scholar | https://www.semanticscholar.org/paper/CiteSpace-II%3A-Detecting-and-visualizing-emerging-in-Chen/bf38bc0f0764485c18ae4fb1795ff03efcbc7a9c | Link thay thế nếu PDF trên bị lỗi |

---

## Công Thức 3 — Volume Score (Điểm Quy Mô)

**Mục đích:** Tránh nhiễu từ keyword quá nhỏ và tránh keyword quá lớn áp đảo.

```
VolumeScore = ln(TotalPapers + 1)
```

`TotalPapers` = tổng số bài báo của keyword (`keyword.paper_count`).  
`+1` để tránh ln(0) khi keyword chưa có bài nào.

**Ví dụ:**

```
Keyword A: 10,000 bài  →  ln(10001) = 9.21
Keyword B:    100 bài  →  ln(101)   = 4.62
Keyword C:      5 bài  →  ln(6)     = 1.79

Không dùng log: A gấp 100x B  →  A áp đảo hoàn toàn
Dùng log:       A gấp   2x B  →  cân bằng hơn nhiều
```

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Tf–idf | https://en.wikipedia.org/wiki/Tf%E2%80%93idf | Mục **"Definition"** → subsection **"Term frequency"** → heading **"Log normalization"** → công thức `f(t,d) = log(1 + tf_t,d)` — cùng ý tưởng log-plus-one cho dữ liệu đếm |
| Aria, M. & Cuccurullo, C. (2017). *bibliometrix: An R-tool for comprehensive science mapping analysis*. Journal of Informetrics, 11(4), 959–975. | **Semantic Scholar (miễn phí):** https://www.semanticscholar.org/paper/bibliometrix%3A-An-R-tool-for-comprehensive-science-Aria-Cuccurullo/aa59bd28fb4ca88a8c5ad1ce81943b385090cd77 | **Section 2** *"The bibliometrix framework"* — chuẩn hóa publication count; **Section 3** — log transformation trong phân tích bibliometric |

---

## Công Thức 4 — sTPS Score (Điểm Tiềm Năng Tổng Hợp)

**Mục đích:** Kết hợp 3 yếu tố thành 1 điểm từ 0 đến 100 để xếp hạng.

### Bước 4a: Chuẩn hóa Min-Max

```
Slope_norm(i) = (Slope(i) - Slope_min) / (Slope_max - Slope_min)
Acc_norm(i)   = (Acc(i)   - Acc_min)   / (Acc_max   - Acc_min)
Vol_norm(i)   = (Vol(i)   - Vol_min)   / (Vol_max   - Vol_min)
```

**Edge case:** Nếu `max = min` (tất cả keyword cùng giá trị) → gán = **0.5**

### Bước 4b: Điểm tổng hợp SAW (Simple Additive Weighting)

```
sTPS(i) = ( Slope_norm(i) * 0.50
          + Acc_norm(i)   * 0.30
          + Vol_norm(i)   * 0.20 ) * 100
```

**Trọng số:**

```
Slope = 50%  →  tốc độ tăng trưởng dài hạn (quan trọng nhất)
Acc   = 30%  →  đà tăng trưởng gần đây
Vol   = 20%  →  quy mô nền tảng (tránh keyword quá nhỏ)
```

**Phân loại:**

```
sTPS >= 80  →  Bùng nổ sớm       (badge đỏ cam)
sTPS 60-79  →  Tăng trưởng vượt bậc
sTPS 40-59  →  Tăng trưởng ổn định
sTPS <  40  →  Tiềm năng thấp    (ẩn khỏi danh sách)
```

### Nguồn — Min-Max Normalization

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Feature Scaling | https://en.wikipedia.org/wiki/Feature_scaling | Mục **"Methods"** → subsection **"Rescaling (min-max normalization)"** → **công thức đầu tiên** trong subsection: `x' = (x − min(x)) / (max(x) − min(x))` |

### Nguồn — SAW Weighted Sum

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Weighted Sum Model | https://en.wikipedia.org/wiki/Weighted_sum_model | Mục **"Description"** → **công thức đầu tiên** trong mục: `A_i^WSM = sum(w_j * a_ij)` — chính là sTPS nhân 100 |
| Hwang, C.L. & Yoon, K. (1981). *Multiple Attribute Decision Making*. Springer. Vol.186. | **Google Books (xem được mục lục):** https://books.google.com/books?id=X-wYAQAAIAAJ | **Chapter 3** *"Methods for Multiple Attribute Decision Making"* → phần SAW |
| Hwang & Yoon (1981) — Chương 3 trực tiếp | https://link.springer.com/chapter/10.1007/978-3-642-48318-9_3 | **Equation ở đầu Chapter 3** — định nghĩa SAW và ví dụ áp dụng |

---

## Công Thức 5 — Dự Báo 6 Tháng (Linear Forecast)

**Mục đích:** Từ Slope và Intercept đã tính ở Công thức 1, ngoại suy 6 tháng tới.

```
--- Dự báo tháng thứ m (m = 1, 2, 3, 4, 5, 6) ---

x_future(m) = (n - 1) + m  =  11 + m

y_hat(m) = Slope * x_future(m) + Intercept

y_hat(m) = max(0, y_hat(m))    -- không cho kết quả âm
y_hat(m) = round(y_hat(m))     -- làm tròn về số nguyên
```

**Ví dụ với Slope = 3.5, Intercept = 10:**

```
Tháng +1:  x=12  →  y_hat = 3.5 * 12 + 10 = 52  bài
Tháng +2:  x=13  →  y_hat = 3.5 * 13 + 10 = 56  bài
Tháng +3:  x=14  →  y_hat = 3.5 * 14 + 10 = 59  bài
Tháng +4:  x=15  →  y_hat = 3.5 * 15 + 10 = 63  bài
Tháng +5:  x=16  →  y_hat = 3.5 * 16 + 10 = 66  bài
Tháng +6:  x=17  →  y_hat = 3.5 * 17 + 10 = 70  bài

Tổng 6 tháng dự báo = 366 bài
```

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Simple Linear Regression | https://en.wikipedia.org/wiki/Simple_linear_regression | Mục **"Formulation and computation"** → dùng lại phương trình `y = Slope * x + Intercept` để dự báo với x = giá trị tương lai |
| Hyndman & Athanasopoulos — *Forecasting: Principles and Practice* 3rd ed. (miễn phí) | https://otexts.com/fpp3/regression-intro.html | Chapter 7 *"Time series regression models"* — mục **"Forecasting with regression"** |

---

## Luồng Tính Toán (Tóm Tắt)

```
INPUT: Y = [y_1..y_12]  từ bảng publication_trends

 ┌─────────────────────────────────────────────────────────┐
 │  VỚI TỪNG KEYWORD                                       │
 │                                                         │
 │  [1] Tính Slope từ toàn bộ 12 tháng                    │
 │       → Nếu Slope <= 0: LOẠI keyword                   │
 │                                                         │
 │  [2] Tính Slope_prior  (tháng 1-6)                     │
 │      Tính Slope_recent (tháng 7-12)                    │
 │      Acc = Slope_recent - Slope_prior                   │
 │                                                         │
 │  [3] VolumeScore = ln(TotalPapers + 1)                 │
 └─────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────┐
 │  SAU KHI XỬ LÝ TẤT CẢ KEYWORD (tìm min/max toàn tập) │
 │                                                         │
 │  [4] Chuẩn hóa Min-Max cho Slope, Acc, Volume          │
 │      sTPS = (Slope_norm*0.5 + Acc_norm*0.3             │
 │            + Vol_norm*0.2) * 100                       │
 └─────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────┐
 │  VỚI TỪNG KEYWORD ĐÃ QUA LỌC                          │
 │                                                         │
 │  [5] y_hat(m) = Slope*(11+m) + Intercept , m=1..6     │
 │      predicted_total = sum(y_hat(1..6))                │
 └─────────────────────────────────────────────────────────┘

OUTPUT cho mỗi keyword:
  sTPS              (điểm 0-100)
  predicted_total   (tổng bài dự báo 6 tháng)
  growth_rate       (predicted_total / TotalPapers * 100%)
  forecast_reason   (phân loại từ sTPS và Acc)
  forecast_months   [{month, year, paper_count}] x6
```

---

## Bảng Tổng Hợp Tất Cả Nguồn

> Tất cả link bên dưới đã được kiểm tra thực tế. Cột "Trạng thái" cho biết cách truy cập.

| # | Dùng cho | Tên nguồn | Trạng thái | Link |
|---|----------|-----------|------------|------|
| 1 | Công thức 1, 2, 5 | Wikipedia — Simple Linear Regression | Miễn phí | https://en.wikipedia.org/wiki/Simple_linear_regression |
| 2 | Công thức 1, 5 | Hyndman & Athanasopoulos — FPP3 (sách giáo khoa miễn phí) | Miễn phí | https://otexts.com/fpp3/regression-intro.html |
| 3 | Công thức 2 | Chen (2006) CiteSpace II — PDF gốc từ Drexel University | PDF miễn phí | http://cluster.ischool.drexel.edu/~cchen/citespace/doc/jasist2006.pdf |
| 4 | Công thức 2 | Chen (2006) — Semantic Scholar (link thay thế) | Miễn phí | https://www.semanticscholar.org/paper/CiteSpace-II%3A-Detecting-and-visualizing-emerging-in-Chen/bf38bc0f0764485c18ae4fb1795ff03efcbc7a9c |
| 5 | Công thức 3 | Wikipedia — Tf-idf | Miễn phí | https://en.wikipedia.org/wiki/Tf%E2%80%93idf |
| 6 | Công thức 3 | Aria & Cuccurullo (2017) bibliometrix — Semantic Scholar | Miễn phí | https://www.semanticscholar.org/paper/bibliometrix%3A-An-R-tool-for-comprehensive-science-Aria-Cuccurullo/aa59bd28fb4ca88a8c5ad1ce81943b385090cd77 |
| 7 | Công thức 4 (Min-Max) | Wikipedia — Feature Scaling | Miễn phí | https://en.wikipedia.org/wiki/Feature_scaling |
| 8 | Công thức 4 (SAW) | Wikipedia — Weighted Sum Model | Miễn phí | https://en.wikipedia.org/wiki/Weighted_sum_model |
| 9 | Công thức 4 (SAW) | Hwang & Yoon (1981) — Google Books | Xem mục lục miễn phí | https://books.google.com/books?id=X-wYAQAAIAAJ |
| 10 | Công thức 4 (SAW) | Hwang & Yoon (1981) — Springer Chapter 3 trực tiếp | Cần đăng nhập thư viện | https://link.springer.com/chapter/10.1007/978-3-642-48318-9_3 |

---

## Ghi Chú Về Các Tên Mục Trên Wikipedia

Để tìm đúng công thức khi mở link Wikipedia, tìm đúng tên mục sau:

| Trang Wikipedia | Tên mục chứa công thức | Ghi chú |
|-----------------|------------------------|---------|
| Simple Linear Regression | **"Formulation and computation"** | Mục đầu tiên của trang, có công thức slope |
| Feature Scaling | **"Methods"** → **"Rescaling (min-max normalization)"** | Công thức đầu tiên trong submenu |
| Weighted Sum Model | **"Description"** | Công thức đầu tiên trong mục |
| Tf-idf | **"Definition"** → **"Term frequency"** → **"Log normalization"** | Nằm sâu trong mục Definition |

---

*Cập nhật: 2026-06-28. Tất cả link đã được kiểm tra thực tế.*
