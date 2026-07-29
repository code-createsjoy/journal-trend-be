  # Tài Liệu Công Thức — Dự Báo Hot Topic Tương Lai

> **Dự án:** TrendSpark / JournalTrend  
> **Mục tiêu:** Với mỗi keyword, dùng **6 tháng lịch sử đã chốt sổ** để tính điểm tiềm năng và dự báo số bài báo cho **tối đa 12 tháng tới**.  
> **Lưu ý cửa sổ dữ liệu:** Chuỗi hồi quy chỉ dùng các tháng đã hoàn chỉnh — **tháng đang chạy bị loại** (khuyết ngày → điểm thấp giả, kéo lệch slope).  
> **Lưu ý link:** Tất cả link Wikipedia và PDF truy cập miễn phí, không cần đăng nhập.

---

## Tổng Quan: Cần 5 Công Thức

```
[1] OLS Slope        →  keyword có đang tăng trưởng không?
[2] Acceleration     →  tốc độ tăng trưởng đang nhanh lên hay chậm lại?
[3] Volume Score     →  keyword có đủ lớn để đáng dự báo không?
[4] sTPS Score       →  xếp hạng tổng hợp (điểm tiềm năng 0-100)
[5] Forecast         →  dự báo số bài báo cho từng tháng (tối đa 12 tháng tới)
```

---

## Đầu Vào Chung

```
Y   = [y_1, y_2, ..., y_6]    tối đa 6 tháng lịch sử ĐÃ CHỐT SỔ từ bảng publication_trends
n   = 6                        số tháng (tháng đang chạy đã bị loại)
x_i = i - 1                   chỉ số tháng: 0, 1, 2, ..., 5
```

> Cấu hình: `forecast-history-window = 6`, `forecast-min-months = 4` (keyword có < 4 tháng
> dữ liệu sẽ không được dự báo). `n` có thể < 6 với keyword mới thêm — các công thức bên dưới
> dùng `n` thực tế, các hằng số minh họa (S_xx…) tính theo n = 6.

---

## Công Thức 1 — OLS Slope (Tốc Độ Tăng Trưởng)
<!-- Ý nghĩa: Tốc độ tăng trưởng trung bình của số bài báo qua 6 tháng. Là độ dốc của đường hồi quy tuyến tính (OLS) vẽ qua chuỗi lịch sử. -->

**Mục đích:** Đo tốc độ tăng trưởng trung bình của số bài báo trên 6 tháng.  
**Lọc:** Nếu `Slope <= 0` → keyword đang giảm hoặc đứng yên → **loại, không dự báo**.

```
x_bar = (0 + 1 + 2 + 3 + 4 + 5) / 6  =  2.5
y_bar = (y_1 + y_2 + ... + y_6) / 6

S_xx  = sum[(x_i - x_bar)^2]           =  17.5   (cố định khi n=6)
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
<!-- Ý nghĩa: So sánh tốc độ tăng 6 tháng cuối với 6 tháng đầu. Phát hiện keyword đang bùng nổ gần đây, không chỉ tăng đều. -->

**Mục đích:** Phát hiện keyword đang **bùng nổ nhanh gần đây** — tốc độ tăng trưởng 3 tháng cuối cao hơn 3 tháng đầu.

**Cách tính:** Chia chuỗi 6 tháng làm 2 nửa bằng nhau (3/3), tính slope riêng từng nửa.

```
--- Nửa trước: tháng 1-3 ---
Y_prior      = [y_1, y_2, y_3]
x_prior      = [0, 1, 2]

x_bar_prior  = 1.0
S_xx_prior   = 2.0   (cố định: n=3, S_xx = 3*(9-1)/12 = 2.0)
S_xy_prior   = sum[(x_i - 1.0) * (y_i - y_bar_prior)]  với i = 0..2

Slope_prior  = S_xy_prior / 2.0


--- Nửa sau: tháng 4-6 ---
Y_recent     = [y_4, y_5, y_6]
x_recent     = [0, 1, 2]   (đặt lại chỉ số từ 0)

x_bar_recent = 1.0
S_xx_recent  = 2.0   (cố định)
S_xy_recent  = sum[(x_i - 1.0) * (y_i - y_bar_recent)]  với i = 0..2

Slope_recent = S_xy_recent / 2.0


--- Gia tốc ---
Acc = Slope_recent - Slope_prior
```

> **Cảnh báo độ tin cậy:** với cửa sổ 6 tháng, mỗi nửa chỉ còn 3 điểm → slope từng nửa nhạy
> nhiễu hơn. Vì vậy trọng số của Acc trong sTPS đã giảm còn **0.2** (xem Công thức 4), dồn
> về Slope (0.6) ổn định hơn.

**Giải thích:**
- `Acc > 0` → keyword đang tăng nhanh hơn → tiềm năng cao
- `Acc < 0` → keyword đang tăng chậm lại → tiềm năng giảm

**Tại sao chia 3/3?**  
Chia đôi cân bằng (50/50) là nguyên tắc khách quan nhất.  
Chia lệch (vd 4/2) khiến một nửa quá ít điểm, càng dễ nhiễu. Với cửa sổ 6 tháng, 3/3 là
lựa chọn cân bằng duy nhất hợp lý.

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Simple Linear Regression | https://en.wikipedia.org/wiki/Simple_linear_regression | Mục **"Formulation and computation"** — dùng lại cùng công thức OLS Slope, áp dụng cho từng nửa chuỗi |
| Chen, C. (2006). *CiteSpace II: Detecting and visualizing emerging trends*. JASIST, 57(3), 359–377. | **PDF miễn phí:** http://cluster.ischool.drexel.edu/~cchen/citespace/doc/jasist2006.pdf | **Section 3** *"Detecting emerging trends and transient patterns"* — phương pháp phát hiện "bùng nổ" theo khoảng thời gian trong bibliometric data |
| Chen (2006) — Semantic Scholar | https://www.semanticscholar.org/paper/CiteSpace-II%3A-Detecting-and-visualizing-emerging-in-Chen/bf38bc0f0764485c18ae4fb1795ff03efcbc7a9c | Link thay thế nếu PDF trên bị lỗi |

<!-- Nó là công thức do nhóm tự thiết kế, chỉ lấy cảm hứng từ ý tưởng "phát hiện burst" của Chen. -->

<!-- Công thức gia tốc là thiết kế của nhóm: em lấy hiệu hai độ dốc OLS của nửa sau và nửa đầu chuỗi, tương đương một xấp xỉ đạo hàm bậc hai — nếu slope là vận tốc thì hiệu slope là gia tốc. Nền tảng toán là hồi quy tuyến tính (Wikipedia). Về mặt ý tưởng, việc phát hiện chủ đề bùng nổ bằng cách so sánh các cửa sổ thời gian là hướng tiếp cận đã có trong bibliometrics — tiêu biểu là Chen (2006), CiteSpace II và gốc là Kleinberg (2002). Em chọn cách đơn giản hóa vì dữ liệu chỉ ~12 điểm/keyword, không đủ để chạy mô hình burst đầy đủ của Kleinberg." -->

<!-- "Kleinberg's (2002) burst-detection algorithm can be adapted for detecting sharp increases of interest in a specialty. Although Kleinberg's original algorithm was developed to detect the bursts of single words, the algorithm is generic enough to be applied to a time series of multiword terms..." -->

---

## Công Thức 3 — Volume Score (Điểm Quy Mô)

 <!-- Tổng số bài báo của keyword. Dùng để tránh nhiễu từ keyword quá nhỏ (5 bài tăng thành 8 bài trông "tăng 60%" nhưng vô nghĩa). -->
 <!-- Volume dựa trên paperCount — tổng số bài báo tích lũy của keyword đó. -->

 <!-- "ln(count+1) là phép biến đổi logarit (log1p) — kỹ thuật chuẩn để xử lý dữ liệu đếm lệch phải, có mặt khắp thống kê. Dạng chính xác log(1 + count) trùng với log normalization của term frequency trong Tf–idf (Wikipedia). Em dùng nó để nén thang quy mô cho keyword không chênh lệch quá mạnh, và +1 để tránh ln(0). Việc chọn log cho tiêu chí Volume là quyết định thiết kế của nhóm, dựa trên kỹ thuật chuẩn này." -->

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

<!-- Lý do toán học: đổi cơ số logarit chỉ là nhân với một hằng số:


log₁₀(x) = ln(x) / ln(10) = ln(x) × 0.4343
Tức log₁₀ và ln chỉ khác nhau một hệ số cố định (0.4343).

Mà bước tiếp theo là Min-Max normalization (dòng 121):


Vol_norm = (Vol − Vol_min) / (Vol_max − Vol_min)
→ Khi cả tử và mẫu đều bị nhân cùng hằng số 0.4343, nó triệt tiêu. Kết quả Vol_norm giống hệt dù dùng ln hay log₁₀ hay log₂.

Ví dụ chứng minh:


Dùng ln:    A→9.21, B→4.62, min=0    Vol_norm(B) = 4.62/9.21 = 0.502
Dùng log₁₀: A→4.00, B→2.00, min=0    Vol_norm(B) = 2.00/4.00 = 0.500
                                       → cùng ~0.50, thứ hạng không đổi
Vậy tại sao code chọn ln? Chỉ vì tiện lập trình: Java Math.log() mặc định là ln (cơ số e). Không có lý do toán học nào bắt buộc — thuần quy ước.

→ Trả lời thầy: "Dùng ln hay log₁₀ cho ra sTPS giống hệt nhau vì Min-Max normalization triệt tiêu hằng số đổi cơ số. Em chọn ln vì Math.log() trong Java mặc định là ln — thuần tiện lợi, không ảnh hưởng kết quả." -->

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
sTPS(i) = ( Slope_norm(i) * 0.60
          + Acc_norm(i)   * 0.20
          + Vol_norm(i)   * 0.20 ) * 100
```

**Trọng số:**

```
Slope = 60%  →  tốc độ tăng trưởng dài hạn (quan trọng nhất, ổn định nhất trên cửa sổ ngắn)
Acc   = 20%  →  đà tăng trưởng gần đây (giảm từ 30% vì 3/3 nhiễu hơn)
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

## Công Thức 5 — Dự Báo (Linear Forecast, tối đa 12 tháng)

**Mục đích:** Từ Slope và Intercept đã tính ở Công thức 1, ngoại suy tối đa 12 tháng tới
(`forecast-horizon = 12`).

> ⚠️ **Lưu ý ngoại suy:** lịch sử chỉ 6 tháng (n = 6) nhưng dự báo tới 12 tháng → các tháng
> xa (+7…+12) là ngoại suy vượt gấp đôi khoảng quan sát, độ tin cậy giảm dần. Nên coi các
> tháng xa là ước lượng sơ bộ.

```
--- Dự báo tháng thứ m (m = 1, 2, ..., 12) ---

x_future(m) = (n - 1) + m  =  5 + m      (với n = 6)

y_hat(m) = Slope * x_future(m) + Intercept

y_hat(m) = max(0, y_hat(m))    -- không cho kết quả âm
y_hat(m) = round(y_hat(m))     -- làm tròn về số nguyên
```
<!-- Bước 1 — 6 tháng lịch sử được đánh số từ 0, không phải từ 1
Khi tính hồi quy OLS, mỗi tháng lịch sử được gán một chỉ số x, bắt đầu từ 0:


Tháng lịch sử:   T1   T2   T3   T4   T5   T6
Chỉ số x:         0    1    2    3    4    5 -->

<!-- x_future(m) là chỉ số (vị trí) của tháng tương lai thứ m trên trục thời gian — chính là giá trị x mà bạn thay vào phương trình đường thẳng để tính ra số bài báo.

Định nghĩa

x_future(m) = (n − 1) + m
m = tháng dự báo thứ mấy (m = 1, 2, ..., 12)
n = số tháng lịch sử (ví dụ n = 6)
x_future(m) = tọa độ x của tháng đó trên trục thời gian đã đánh số từ 0 -->

**Ví dụ với Slope = 3.5, Intercept = 10 (n = 6):**

```
Tháng +1:  x=6   →  y_hat = 3.5 * 6  + 10 = 31  bài
Tháng +2:  x=7   →  y_hat = 3.5 * 7  + 10 = 35  bài
Tháng +3:  x=8   →  y_hat = 3.5 * 8  + 10 = 38  bài
Tháng +4:  x=9   →  y_hat = 3.5 * 9  + 10 = 42  bài
Tháng +5:  x=10  →  y_hat = 3.5 * 10 + 10 = 45  bài
Tháng +6:  x=11  →  y_hat = 3.5 * 11 + 10 = 49  bài
...  (tiếp tục tới Tháng +12: x=17 → y_hat = 70 bài)
```

### Nguồn

| Nguồn | Link (truy cập miễn phí) | Vị trí chính xác |
|-------|--------------------------|------------------|
| Wikipedia — Simple Linear Regression | https://en.wikipedia.org/wiki/Simple_linear_regression | Mục **"Formulation and computation"** → dùng lại phương trình `y = Slope * x + Intercept` để dự báo với x = giá trị tương lai |
| Hyndman & Athanasopoulos — *Forecasting: Principles and Practice* 3rd ed. (miễn phí) | https://otexts.com/fpp3/regression-intro.html | Chapter 7 *"Time series regression models"* — mục **"Forecasting with regression"** |

---

## Luồng Tính Toán (Tóm Tắt)

```
INPUT: Y = [y_1..y_6]  (6 tháng đã chốt sổ, bỏ tháng đang chạy) từ publication_trends

 ┌─────────────────────────────────────────────────────────┐
 │  VỚI TỪNG KEYWORD                                       │
 │                                                         │
 │  [1] Tính Slope từ toàn bộ 6 tháng                     │
 │       → Nếu Slope <= 0: LOẠI keyword                   │
 │                                                         │
 │  [2] Tính Slope_prior  (tháng 1-3)                     │
 │      Tính Slope_recent (tháng 4-6)                     │
 │      Acc = Slope_recent - Slope_prior                   │
 │                                                         │
 │  [3] VolumeScore = ln(TotalPapers + 1)                 │
 └─────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────┐
 │  SAU KHI XỬ LÝ TẤT CẢ KEYWORD (tìm min/max toàn tập) │
 │                                                         │
 │  [4] Chuẩn hóa Min-Max cho Slope, Acc, Volume          │
 │      sTPS = (Slope_norm*0.6 + Acc_norm*0.2             │
 │            + Vol_norm*0.2) * 100                       │
 └─────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────┐
 │  VỚI TỪNG KEYWORD ĐÃ QUA LỌC                          │
 │                                                         │
 │  [5] y_hat(m) = Slope*(5+m) + Intercept , m=1..12     │
 │      predicted_total = sum(y_hat(1..12))               │
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
