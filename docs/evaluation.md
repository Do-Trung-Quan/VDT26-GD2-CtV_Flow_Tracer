# Đo lường MVP (NFR-01 → NFR-09)

> Chỉ ghi **số liệu tổng hợp** (CLAUDE.md §3.5): không có tên API, màn hình, đường dẫn hay mã của dự án demo công ty.
> Cách đo: `node scripts/benchmark.mjs` (NFR-01 → NFR-04, NFR-06) và `node scripts/evaluate-accuracy.mjs` (NFR-07 → NFR-09). Xem `scripts/README.md`.

Máy đo: Windows 11, Engine chạy bằng `spring-boot:run` (profile `dev`), PostgreSQL trong Docker, sidecar Angular chạy bằng Node. Mỗi số lấy từ 1–2 lần chạy liên tiếp (ghi khoảng khi hai lần chênh nhau).

## 1. Hiệu năng

### 1.1. Dự án demo (1.404 file nguồn, Backend + Frontend)

Số đo **sau Task 8.2** (Engine chạy trực tiếp trên máy; số "trước" lấy từ lần đo đầu của Task 8.1):

| Chỉ tiêu | Trước 8.2 | Sau 8.2 | Mục tiêu | Đạt |
| :--- | :--- | :--- | :--- | :---: |
| NFR-01 Quét lần đầu | 35–43 giây | **25–33 giây** (dự phóng ~2.000 file ≈ 40 giây; số file ít hơn 2.000 nên chỉ tham khảo) | ≤ 3 phút | ✅ |
| NFR-06 RAM Engine (đỉnh khi quét) | 690–810 MB | 690–720 MB | ≤ 2 GB | ✅ |
| NFR-02 Quét lại, không file nào đổi | 8,7–10,5 giây | **3,1–4,3 giây** | ≤ 15 giây | ✅ |
| NFR-02 Quét lại, **20 file đổi** | 27–42 giây | **10,4 giây** (DISCOVER 2,6 · EXTRACT 2,9 · RESOLVE 3,7 · PERSIST 1,0 · LINK 0,2) | ≤ 15 giây | ✅ |
| NFR-03 Tải cây Backend (244 API) | p95 29 ms | p95 41 ms | ≤ 1 giây | ✅ |
| NFR-03 Tải cây Frontend (1.030 phần tử) | p95 328 ms | p95 326 ms | ≤ 1 giây | ✅ |
| NFR-03 Tìm kiếm | p95 70 ms | p95 70–163 ms | ≤ 1 giây | ✅ |
| NFR-04 Dò vết ≤ 50 bước (30 luồng) | p95 55–79 ms | p95 77 ms (max 229 ms) | ≤ 2 giây | ✅ |
| NFR-04 (tham khảo) luồng > 50 bước (15 luồng) | 25–116 ms | 27–122 ms | — | — |

Chất lượng luồng đã dò (45 luồng mẫu): 1.816 bước, 19 bước chưa xác định (1,0%) — không đổi sau tối ưu.

**Các thay đổi của 8.2 để đạt NFR-02** (số liệu từ log thời gian từng giai đoạn, không đoán):
1. **LINK: 6,4–9,4 giây → 0,06–0,14 giây** cho 213 lời gọi × 244 API. Nguyên nhân là `UrlPattern.parse(...)` (4–5 biểu thức chính quy mỗi lần) được gọi trong vòng lặp trong cùng của `Linker` — hàng triệu lần. Nay phân tích mỗi đường dẫn một lần và biên dịch sẵn các biểu thức chính quy; kết quả liên kết không đổi (golden test và test của Linker vẫn qua). Phần này cũng rút ngắn lần quét đầu (LINK 7 s → 0,3 s).
2. **EXTRACT lúc quét lại**: đọc cache dữ kiện của ~1.400 file chưa đổi từ tuần tự sang song song (thứ tự dữ kiện giữ nguyên nên kết quả vẫn tất định).
3. **DISCOVER**: đọc + băm file song song (thứ tự duyệt giữ nguyên). Phần còn lại của DISCOVER (~2,6 s) là duyệt ~33.000 file của cây thư mục trên ổ đĩa — giới hạn bởi hệ điều hành.
4. **PERSIST: 6–9 giây → ~1 giây** khi quét lại: (a) không đọc–ghi lại ~1.400 bản ghi file không đổi (đánh dấu lần quét bằng một lệnh cập nhật hàng loạt); (b) chỉ ghi node / edge **mới hoặc đã đổi** (so với bản đã lưu) thay vì xóa và ghi lại toàn bộ. Kèm lợi ích: id của node và edge không đổi được giữ qua các lần quét (liên kết `edgeRef` và popover không còn "hết hạn" sau mỗi lần quét lại).

**Kiểm chứng tính đúng của phần ghi chênh lệch** (so vi phân): sửa mã trong bản sao (thêm endpoint, bỏ một lời gọi, bỏ một nút) → Workspace A quét lại, Workspace B import mới trên cùng bản sao. Trên fixture và trên demo: cùng số API, phần tử, bước, node, edge, **cùng mã băm của toàn bộ luồng dò vết** và cây BE / FE giống hệt từng ký tự (sau khi bỏ id). Test tích hợp `ScanPipelineIT` thêm ca kiểm tra id node không đổi khi quét lại.

**Việc còn lại cho hiệu năng** (không bắt buộc cho chỉ tiêu): lần quét đầu còn DISCOVER 5 s (duyệt cây thư mục), EXTRACT 8 s, RESOLVE 8 s, PERSIST 5 s; quét lại ≤ 20 file đổi còn RESOLVE 3,7 s và EXTRACT 2,9 s (chủ yếu đọc cache dữ kiện của các file không đổi). Đo trong Docker trên Windows sẽ chậm hơn (xem mục 4).

### 1.2. Fixture (64 file, tự viết) — chỉ để kiểm tra script

Quét lần đầu 4,9 s; RAM đỉnh 328 MB; quét lại không đổi 0,3 s; quét lại 20 file đổi 3,2 s; tải cây ≤ 52 ms; tìm kiếm ≤ 43 ms; dò vết ≤ 41 ms. Mọi chỉ tiêu đạt (số file quá ít nên không có ý nghĩa so với ngưỡng 2.000 file).

## 2. Độ chính xác

### 2.1. Dự án demo — ground truth 30 luồng (do agent bên dự án demo lập từ mã nguồn và tài liệu, ngoài repo)

Chạy `evaluate-accuracy.mjs` trên Workspace của demo (đã quét trong container). **Lưu ý khi đọc số:** ground truth cố ý bỏ ~15 ứng viên "không đủ chắc", nên mẫu thiên về luồng rõ ràng (không có luồng truy vấn native hay interface nhiều lớp hiện thực); số liệu là **trên mẫu 30 luồng**, không phải toàn bộ demo.

| Chỉ tiêu | Lần đo đầu | Sau sửa (8.2) | Mục tiêu | Đạt |
| :--- | :--- | :--- | :--- | :---: |
| NFR-07 Endpoint so với OpenAPI | không đo được | không đo được | ≥ 98% | ➖ |
| NFR-08 precision (trên phần tử xác định được) | 100% (25/25) | **100%** (28/28; 27/30 phần tử xác định được) | ≥ 95% | ✅ |
| NFR-08 recall (trên phần tử xác định được) | 100%\* | **96,6%** (thiếu 1 API) | ≥ 85% | ✅ |
| NFR-08 recall **nghiêm ngặt** (phần tử không xác định được = bỏ sót) | 75,8% | **84,8%** | ≥ 85% | ❌ (sát ngưỡng) |
| NFR-09 trace tới đủ bảng **và** không bước chưa xác định | 90,0% (27/30) | **96,7%** (29/30) | ≥ 90% | ✅ |

\* lần đầu 8 phần tử bị tính là "không tìm thấy" do script ghép tên chưa tốt (xem dưới), không phải do tool sai.

**NFR-07 chưa đo được:** demo không có file OpenAPI trong mã nguồn (Swagger sinh lúc chạy). Cách đo: lưu `/v3/api-docs` của Backend đang chạy thành file JSON, phục vụ qua một địa chỉ cục bộ rồi chạy `evaluate-accuracy.mjs --openapi <url>` (Engine trong Docker truy cập máy chủ bằng `host.docker.internal`). Cần người dùng cung cấp file đó.

**Phân tích các mục chưa đạt (3/30 phần tử không xác định được, 1 luồng còn bước chưa xác định):**
- 2 phần tử **mơ hồ**: cùng nhãn xuất hiện ở hai màn hình trùng tên, một bản có API một bản không. Đây là điểm yếu của cách xác định phần tử trong ground truth (tên màn hình trùng), không chắc là lỗi của tool.
- 1 phần tử **không có trong cây**: ground truth gọi một "phần tử" là tiêu đề cột bảng khai báo trong file TypeScript; tool chỉ trích phần tử tương tác trong template HTML. Ngoài phạm vi trích xuất hiện tại.
- 1 luồng còn **bước chưa xác định** ở lớp cache: lời gọi `get` / `put` của một lớp cache kế thừa hàm từ thư viện không có mã nguồn.
- 1 phần tử: ground truth ghi nhãn rút gọn và tên màn hình không có trong cây (một nút chỉ có biểu tượng, nhãn lấy từ thuộc tính `aria-label`) — cần agent bên demo sửa lại cho khớp nhãn thật.

**Những lỗi thật của tool tìm ra nhờ ground truth và đã sửa ở 8.2:**
1. `Optional.ifPresent(e -> { throw … })`: lệnh `throw` trong lambda bị coi là kết thúc vô điều kiện của cả hàm → mọi bước sau đó (ví dụ ghi sang bảng thứ hai) biến mất khỏi luồng. Nay là nhánh có điều kiện (`ifPresent` / `ifPresentOrElse` / `orElseGet` / `forEach`). Luồng thiếu bảng → đủ bảng.
2. Hàm nạp chồng cùng tên, cùng số tham số (vd `delete(String)` / `delete(Collection)`) bị báo "chưa xác định": nay chọn theo kiểu đối số khi chắc chắn (kiểu biến cục bộ / tham số, literal, `new X()`), vẫn giữ `UNRESOLVED` khi chưa chắc.
3. Nhãn nút dạng `{{ đang_gửi ? t('đang…') : t('tên_nút') }}` không được nhận ra (nút gửi biểu mẫu có gọi API mất nhãn): nay lấy nhánh không phải trạng thái "đang xử lý".
4. Script đo ghép tên chưa tốt (nhãn có dấu `*`, tên màn hình chứa trong tên hộp thoại khác, nhiều phần tử cùng nhãn cùng tập API): đã chỉnh và báo cáo tách riêng "xác định được / mơ hồ / không có trong cây".

### 2.2. Fixture (`fixtures/ground-truth/spring-angular-mini.json`, tự viết) — chỉ để kiểm tra script

| Chỉ tiêu | Đo được | Ghi chú |
| :--- | :--- | :--- |
| NFR-07 | 75% (3/4) | Cố ý: `openapi.yaml` mẫu có 1 API mà code không có, và thiếu 3 API |
| NFR-08 precision / recall | 100% / 100% (9 phần tử) | |
| NFR-09 | 57% (4/7 luồng) | 3 luồng có cạnh `UNRESOLVED` (lớp hiện thực trùng, không có `@Primary`) — **cố ý** của fixture |

### 2.3. Dự án mã nguồn mở công khai — `spring-petclinic-rest` + `spring-petclinic-angular` (tải về ngoài repo, 198 file, đã xóa Workspace thử)

Quét `SUCCEEDED` trong 2,9 giây, 0 lỗi file — nhưng **kết quả rất thấp**, cho thấy tool chưa tổng quát hóa:

| Chỉ tiêu | Đo được | Nguyên nhân |
| :--- | :--- | :--- |
| NFR-07 Endpoint so với OpenAPI | **0% (0/37)** — tool thấy 1 API, đặc tả có 37 | Backend theo kiểu **OpenAPI-first**: Controller `implements XxxApi`, trong đó các interface `XxxApi` mang `@RequestMapping` được **sinh lúc build** từ `openapi.yml` (openapi-generator) nên không có trong mã nguồn → phân tích tĩnh không thấy mapping |
| Cây Frontend | 3 màn hình (`/`, `/welcome`, `/**`), **0 phần tử**; thực tế còn hàng chục route / màn hình (owners, pets, vets, visits…) | Ứng dụng dùng **NgModule nạp sớm**: `AppModule` import `OwnersModule` → `OwnersRoutingModule` → `RouterModule.forChild(routes)`. Tool chỉ gom route từ `forRoot`, `children`, `loadChildren`, chưa gom route của các module con được import trực tiếp |
| NFR-08 / NFR-09 | không đo được | không có API và phần tử để so |

Hướng xử lý: (1) **đã sửa ở Task 8.2** — gom route từ `RouterModule.forChild` của các NgModule được import trực tiếp; quét lại petclinic: **23 màn hình, 96 phần tử, 26 phần tử gọi API** (trước: 3 / 0 / 0; 61/96 phần tử là ô nhập không có nhãn hiển thị — tool đặt tên theo `name`); (2) Backend OpenAPI-first cần dựng endpoint từ file `openapi.yml` mà `pom.xml` trỏ tới — **tính năng mới ngoài tài liệu đã chốt**, chờ người dùng quyết định.

## 3. Chưa đo bằng script

- **NFR-05** (chữ AI đầu tiên ≤ 5 giây / ≤ 1 giây khi đã lưu): phụ thuộc nhà cung cấp AI; xem nhật ký GĐ7 (Gemini: dữ liệu mẫu ~1,8 s, kịch bản ~4,4 s).
- **NFR-10** (tất định): đã có golden test trong CI của Engine và sidecar.

## 4. Đo trong Docker (Windows, Docker Desktop) — dự án demo 1.404 file

Cùng máy, Engine chạy trong container, mã nguồn gắn từ ổ `D:` (bind-mount chỉ đọc):

| Chỉ tiêu | Engine chạy trực tiếp | Container, quét nguyên cây | Container, **loại thư mục PHP cũ** | Mục tiêu |
| :--- | :--- | :--- | :--- | :--- |
| NFR-01 Quét lần đầu | 25–33 s | 62–67 s ✅ | **23,7 s** ✅ | ≤ 3 phút |
| NFR-06 RAM | 690–720 MB | 990 MB ✅ | 1.080 MB ✅ | ≤ 2 GB |
| NFR-02 Quét lại, không file nào đổi | 3,1–4,3 s | **52 s ❌** | 7,7 s ✅ | ≤ 15 s |
| NFR-02 Quét lại, 20 file đổi | 10,4 s | **53 s ❌** | 10,8 s ✅ | ≤ 15 s |
| NFR-03 / NFR-04 | đạt | đạt (p95 ≤ 180 ms / ≤ 40 ms) | đạt | ≤ 1 s / ≤ 2 s |

**Nguyên nhân quét chậm trong container:** `DISCOVER` chiếm 46–51 giây. Đo bằng chính lệnh `find` trong container (không dính mã của tool): đi qua ~34.600 file của cây thư mục mất 19–23 giây — tốc độ hệ tệp của bind-mount ổ Windows (~0,6 ms mỗi file), không phải mã Engine. Backend của demo có một thư mục chứa ~31.000 file (phần lớn mẫu giao diện và ảnh của mã PHP cũ) mà MVP không phân tích.

**Cách xử lý (người dùng làm được ngay):** khai báo thư mục đó ở **Loại trừ** (`excludeGlobs`) của Source — tool bỏ qua cả nhánh, kết quả phân tích **giống hệt** (244 API, 5.874 node, 13.422 edge) nhưng quét lại nhanh gấp 5–7 lần. Các lựa chọn khác: đặt mã nguồn trong hệ tệp WSL2 (ext4) hoặc chạy Engine trực tiếp (không qua Docker) cho dự án rất lớn. Đã ghi vào README. Hướng cải tiến Engine (chưa làm): gợi ý tự động thư mục có quá nhiều file không phân tích.
