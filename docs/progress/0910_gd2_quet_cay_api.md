# Báo cáo Giai đoạn 2 — Quét & cây API Backend (09/10/2026)

> **Mục tiêu giai đoạn:** bấm Quét trên giao diện, thấy tiến độ, rồi thấy cây API của dự án demo.
> **Phần đã làm:**
> - **BE** (09/10): Task 2.1 pipeline quét + lưu IR; Task 2.2 bộ phân tích Spring (endpoint, module), API C1.
> - **FE** (10/10, sau khi người dùng duyệt BE): Task 2.3, xem mục 6.
> **Kết quả:**
> - Engine `clean verify` xanh (74 test). Sidecar test xanh, web build được (chưa đổi FE).
> - `check-forbidden-terms --all` sạch.
> - Quét được dự án demo qua API thật.
>
> **Chưa commit**, chờ người dùng review.
> Phần trước đó: [0910_gd1_workspace_import.md](0910_gd1_workspace_import.md).

---

## 1. Task 2.2 — Bộ phân tích Spring: endpoint và module

### 1.1. Pha 1 — trích dữ kiện từng file (JavaParser, chịu lỗi cú pháp)

- **`TypeDeclarationExtractor`:** class / interface / enum / record (kể cả lồng nhau), kế thừa, annotation, Javadoc.
- **`MemberExtractor`:**
  - field, constructor, method (chữ ký, annotation, Javadoc, dòng bắt đầu / kết thúc);
  - giải **hằng chuỗi** `static final String`, kể cả hằng ghép từ hằng khác cùng lớp (`API_V1 + "/categories"`).
- **`MappingExtractor`:**
  - `@RequestMapping` cấp class / method; `@Get/Post/Put/Delete/PatchMapping` (`value` / `path`, nhiều đường dẫn, nhiều phương thức);
  - tham số `@PathVariable` / `@RequestParam` (`required`, `defaultValue`) / `@RequestBody` / `@RequestHeader`;
  - `@Operation` (summary, description, operationId, tags, deprecated), `@Tag`, `@Deprecated`.
- **File sai cú pháp:** không có dữ kiện, chỉ có thông báo lỗi ngắn gọn kèm số dòng.
- **Phạm vi Giai đoạn 2:** lời gọi, throw, mảnh chuỗi, trường DTO và giải kiểu bằng SymbolSolver làm ở Giai đoạn 3. Hiện để rỗng, có ghi chú trong code.

### 1.2. Pha 2 — ghép kết quả

- **`ApplicationConfigReader`:** đọc `server.servlet.context-path` (và chiến lược đặt tên JSON) từ `application.yml/.yaml/.properties` mặc định của **từng module**.
  - Hỗ trợ YAML lồng nhau, dạng phẳng, giá trị có ngoặc kép, chú thích.
  - `${VAR:mặc định}` → lấy giá trị mặc định. `${VAR}` không có mặc định → **không đoán**: bỏ context-path và ghi cảnh báo.
  - Các khóa khác (mật khẩu…) bị bỏ qua ngay khi đọc, có test chứng minh.
- **`EndpointBuilder`:** ghép context-path + đường dẫn lớp + đường dẫn hàm.
  - Chuẩn hóa: bỏ `//`, bỏ `/` cuối, `{id:\d+}` → `{id}`.
  - Giải hằng số trong cùng lớp, theo import hoặc cùng package. Không giải được thì giữ `${Tên.HẰNG}` và đặt `pathResolved = false`, kèm cảnh báo.
  - `@RequestMapping` không khai phương thức → `ANY`.
  - Bóc `ResponseEntity<T>`.
  - Trùng endpoint trong một Source → giữ khai báo đầu tiên, kèm cảnh báo.
- **Mô tả ngắn:** `@Operation(summary)`, nếu không có thì câu đầu của Javadoc, nếu cũng không có thì **để trống**. Luôn ghi kèm `summarySource`.
- **`ModuleNameResolver`:** `@Tag` → tên Controller (bỏ hậu tố `Controller` / `Resource` / `Api`) → tên package; luôn ghi kèm `moduleSource`.
- **SPI `StackAdapter`** thêm 2 hàm:
  - `analyzes(path)`: Spring chỉ phân tích `.java`; Angular trả `false` cho tới Giai đoạn 4;
  - `factsVersion()`: đổi cách trích thì cache dữ kiện cũ tự hết hiệu lực.
- **Mini fixture `fixtures/spring-mini`** (tự viết, miền Shop), có golden test `fixtures/golden/spring-mini.ir.json`. Các ca:
  - có / không `@Tag`;
  - context-path lồng YAML;
  - hằng số ghép, hằng số không giải được;
  - biến đường dẫn có regex;
  - `@RequestMapping` nhiều phương thức / không phương thức;
  - 1 file sai cú pháp.

## 2. Task 2.1 — Pipeline quét và lưu IR

**Pipeline** `DISCOVER → DIFF → EXTRACT → RESOLVE → PERSIST → FINALIZE`. Bước `LINK` làm ở Giai đoạn 5.

- **DISCOVER:**
  - duyệt cây có **cắt tỉa**: bỏ cả nhánh `node_modules`, `target`, `docs/`…;
  - luật dạng `…/**` chỉ áp ở cấp thư mục;
  - đọc và băm các file bộ phân tích xử lý;
  - **file lỗi có lý do cụ thể:** quá kích thước, nhị phân, sai bảng mã UTF-8, không đọc được.
- **DIFF:** so mã băm với `source_files` của lần trước → thêm / sửa / xóa / không đổi.
- **EXTRACT:**
  - song song trên virtual threads (tối đa `flowtracer.scan.workers`);
  - `RESCAN` lấy dữ kiện file không đổi từ **`FactsCache`** (ổ đĩa, theo Workspace + bộ phân tích + phiên bản + mã băm);
  - mỗi file có thời gian tối đa 60 giây;
  - lỗi của bộ phân tích chỉ làm hỏng riêng file đó;
  - **yêu cầu hủy được kiểm tra mỗi 200 ms**, kể cả khi đang chờ một file chạy lâu.
- **RESOLVE:** chẩn đoán mức lỗi gắn với file (lỗi cú pháp) → file đó thành file lỗi.
- **PERSIST:** ghi `source_files` + IR (upsert node theo key, giữ id cũ; xóa node không còn; thay edge) của mọi Source trong **MỘT transaction**. Hủy được kiểm tra lần cuối ngay trước khi ghi.
- **Kết cục:**
  - có file lỗi → `PARTIAL`;
  - hủy → `CANCELLED`;
  - lỗi → `FAILED` (IR cũ giữ nguyên);
  - `RESCAN` không có file đổi → bỏ qua bước 3–5, `noChanges = true`.
- **Thống kê (`scan_runs.stats`) — mọi số đều đếm / đo thật, không có số đặt sẵn:**
  - số file theo loại;
  - số node / edge / endpoint **đọc lại từ CSDL** sau khi lưu;
  - số chẩn đoán **kèm danh sách chẩn đoán** (file, dòng, nội dung; tối đa 100 mục) làm căn cứ;
  - thời gian thật từng giai đoạn đã chạy (danh sách có thứ tự);
  - kết quả từng Source, kèm ghi chú nếu Source không được phân tích.
  - Quét lại không thay đổi: chẩn đoán lấy từ lần phân tích gần nhất, vì IR vẫn là kết quả của lần đó.
- **API:**
  - B1 bắt đầu quét: `202`; Workspace đang quét thì `409`;
  - B2 SSE: `progress` / `done`, client vào muộn nhận ngay sự kiện gần nhất;
  - B3 trạng thái, B4 hủy (lần quét đã xong thì `409`), B5 lịch sử (phân trang), B6 file lỗi;
  - C1 cây `Module → Endpoint`.
  - Tất cả bọc `ApiResponse`, trừ luồng SSE.
- **Engine khởi động lại:** lần quét `PENDING` / `RUNNING` còn trong CSDL được đánh dấu `FAILED` kèm lý do.
- **Workspace:**
  - thẻ / chi tiết có `lastScan` thật (thay cho `lastScanRunId`);
  - xóa Workspace thì xóa cả cache dữ kiện.
- **Sửa phát sinh:**
  - `LocalSourceReader` nhớ kết quả kiểm tra symlink theo thư mục: `toRealPath()` chậm trên Windows, vẫn chặn symlink ra ngoài;
  - Hibernate ghi theo lô (`batch_size = 100`).

## 3. Kết quả kiểm thử

| Lệnh / kiểm thử | Kết quả |
| :--- | :--- |
| `mvnw -f engine/pom.xml clean verify` | ✅ 74 test (ir 4 · core 19 · adapter-spring 14 · app 20 unit/ArchUnit + 17 IT); ArchUnit giữ nguyên luật |
| `SpringGoldenTest` | ✅ `spring-mini` ra đúng 10 endpoint, 2 module (`Danh mục` theo `@Tag`, `Order` theo Controller), 1 file lỗi, 1 cảnh báo hằng số |
| `ScanPipelineIT` (adapter giả, PostgreSQL thật) | ✅ quét lần đầu · quét lại 1 thêm / 1 sửa / 1 xóa (1 lấy từ cache) · quét lại không đổi · file lỗi → `PARTIAL` + B6 · hủy giữa chừng giữ IR cũ · quét chồng `409` · xóa Workspace không đụng file |
| `WorkspaceApiIT` | ✅ thêm ca quét `spring-mini` bằng **bộ phân tích thật**: `PARTIAL`, 6 file, 1 lỗi, 10 endpoint, cây 2 module |
| Sidecar test / web build | ✅ 8 test / build được (web chưa đổi) |
| `check-forbidden-terms --all` | ✅ sạch |

**Chạy thử trên dự án demo qua API thật** (chỉ ghi số liệu tổng hợp):

| Chỉ số | Kết quả |
| :--- | :--- |
| Nguồn mã | 2 (Backend Spring, Frontend Angular — Angular chưa phân tích, có ghi chú) |
| File Java được phân tích | 725, 0 file lỗi |
| Endpoint / module | 244 endpoint, 36 module (13 theo `@Tag`, 23 theo tên Controller); 106 endpoint có mô tả (`@Operation` hoặc Javadoc) |
| Cảnh báo | 3, đều là **trùng endpoint** (cùng phương thức + đường dẫn ở 2 module Maven khác nhau trong cùng Source BE) |
| Quét lần đầu (`IMPORT`) | khoảng 9,5–10 giây (NFR-01: ≤ 3 phút cho khoảng 2.000 file) |
| Quét lại không đổi (`RESCAN`) | khoảng 3–5 giây (NFR-02: ≤ 15 giây) |

**Ghi chú về các số đo:**
- **Đối chiếu số endpoint:** tool tìm được 247 cặp handler–đường dẫn (244 giữ lại + 3 trùng). Phép đếm thô bằng regex của mình cho 240. Phép đếm thô đó không phải đáp án chuẩn: nó bỏ sót vài dạng khai báo. Đối chiếu chính xác với Swagger là API C6 (*Nên có*), chưa làm.
- **Tối ưu trong lúc đo:** IMPORT giảm từ 16,5 xuống 9,5 giây, RESCAN từ 9,1 xuống 3,2 giây, nhờ 3 thay đổi:
  - cắt tỉa luật thư mục;
  - cache kiểm tra symlink;
  - Hibernate ghi theo lô.

## 4. Việc còn dở / cần người dùng quyết định

- **Task 2.3 (FE)** chưa làm: chờ duyệt phần BE.
- **Trùng endpoint giữa các ứng dụng trong cùng Source:**
  - key hiện là `java:endpoint:<METHOD> <path>`, nên 2 ứng dụng Spring trong cùng một Source có endpoint trùng sẽ chỉ giữ 1 (demo có 3 trường hợp, ở module mock);
  - muốn giữ cả hai thì phải đổi quy tắc node key, mà đây là **breaking change** theo `CLAUDE.md` §3.3 → **cần người dùng quyết định**.
- **Context-path chỉ khai trong file profile** (`application-dev.yml`…) chưa được đọc; hiện chỉ đọc file `application.*` mặc định.
- **Angular** chưa được phân tích (Giai đoạn 4). Thống kê ghi rõ "Bộ phân tích 'angular' không xử lý file nào của nguồn này".
- Chưa chạy `spotless:apply` (theo quy ước).

## 5. Vấn đề gặp phải

- **PostgreSQL JSONB tự sắp xếp lại khóa object**, làm mất thứ tự các giai đoạn → đổi `stageDurations` sang danh sách có thứ tự.
- **Ký tự NUL lọt vào mã nguồn** khi vá bằng script (escape `\u0000` bị dịch) → đã quét toàn bộ, thay bằng escape văn bản và bỏ hẳn cách so sánh dùng ký tự này.
- **Hủy quét phải chờ tới 60 giây** khi đang đợi một file chạy lâu → đổi sang chờ theo nhịp 200 ms.
- **Race "quét lại ngay sau khi xong bị `409`"** → mở khóa Workspace trước khi gửi sự kiện `done`.

---

## 6. Task 2.3 — Giao diện quét và cột trái Backend (FE, 10/10/2026)

> Người dùng chưa trả lời câu hỏi về key endpoint trùng (mục 4), nên giữ nguyên cách hiện tại: chỉ giữ một bản và cảnh báo.

### 6.1. Việc đã làm

- **Kiểu API:**
  - sinh lại bằng `gen:api` (nhóm B, C1, `lastScan`);
  - sự kiện SSE `progress` không có trong OpenAPI, nên kiểu TypeScript `ScanProgressDto` được khai báo tay, khớp record Java.
- **`ScanTracker` (dịch vụ dùng chung):**
  - bắt đầu quét (B1), nhận tiến độ qua SSE (B2), hủy (B4);
  - rớt SSE thì hỏi trạng thái qua B3 mỗi giây tới khi xong;
  - mở lại trang khi quét chưa xong thì tự theo dõi tiếp.
- **Khung tiến độ góc phải dưới:**
  - 6 giai đoạn;
  - thanh tiến độ tính từ **giai đoạn + số mục thật đã xử lý** (không ước lượng theo thời gian);
  - thu nhỏ, hủy;
  - khi xong: mỗi giai đoạn hiện **thời gian Engine đo**; giai đoạn không chạy hiện "bỏ qua";
  - tóm tắt số file (+ / ~ / −) và số endpoint;
  - liên kết "Xem N file lỗi" mở danh sách (B6) kèm lý do.
- **Màn hình chính 3 cột:**
  - **Cột trái:**
    - tab Backend; tab Frontend tắt, chờ Giai đoạn 4;
    - lọc nhanh theo đường dẫn / phương thức / mô tả / module, có tô sáng và số "khớp / tổng";
    - module thu / mở, đếm số endpoint;
    - nhãn phương thức có màu;
    - icon cảnh báo khi đường dẫn chưa giải được;
    - kéo đổi độ rộng (nhớ trong trình duyệt), thu gọn.
  - **Canvas:**
    - cụm nút nổi góc trái trên: chấm màu và trạng thái lần quét gần nhất kèm thời điểm, nút Quét / Quét lại;
    - trạng thái rỗng.
  - **Cột phải:** thông tin thật của endpoint đang chọn.
    - Mô tả kèm nguồn (`@Operation` / Javadoc). Không có thì ghi rõ "mã nguồn không có @Operation hay Javadoc".
    - Vị trí file:dòng, cảnh báo, `@Deprecated`.
- **Cài đặt › tab Lịch sử quét:**
  - cột Bắt đầu, Kiểu, Trạng thái, Tổng file, Mới, Thay đổi, Bị xóa, Lỗi (bấm để xem), Endpoint, Thời gian;
  - phân trang;
  - ghi chú cách đếm.
- **Wizard Import** đổi thành "Tạo & quét": tạo xong chuyển sang màn hình chính và quét toàn bộ.
- **Thẻ Workspace** hiện trạng thái và thời điểm lần quét gần nhất thật.

### 6.2. Sửa phát sinh ở BE khi kiểm chứng trên demo

- **Đường dẫn khai bằng thuộc tính cấu hình Spring trong chuỗi** (`"${khóa}"`) trước đó được coi như đã giải, không có cảnh báo, tức một kết quả thiếu căn cứ. Đã sửa:
  - `${khóa:mặc định}` → dùng giá trị mặc định;
  - `${khóa}` → `pathResolved = false`, `pathIssue = PROPERTY`, kèm cảnh báo;
  - cây hiện thông báo riêng cho trường hợp này.
- Thêm 2 ca vào `spring-mini` và sinh lại golden.
- Trên demo: **4 endpoint** dùng thuộc tính không có giá trị mặc định nay được báo. Tổng cảnh báo là 7, gồm 3 trùng endpoint và 4 thuộc tính.

### 6.3. Kết quả kiểm thử

| Lệnh / kiểm thử | Kết quả |
| :--- | :--- |
| `mvnw -f engine/pom.xml clean verify` | ✅ 74 test (golden `spring-mini` nay 12 endpoint) |
| `npm --prefix web run test` / `lint` / `build` | ✅ 23 test (thêm: lọc cây, tô sáng, định dạng, tiến độ, khung tiến độ) / sạch / build được |
| `npm --prefix analyzer-node run test` | ✅ 8 test |
| `check-forbidden-terms --all` | ✅ sạch |

**Kiểm chứng trên giao diện** (Chrome headless, Engine dev + `ng serve`): 15/15 mục đạt.

| # (roadmap) | Thao tác | Kết quả |
| :--- | :--- | :--- |
| 1 | Import demo → Tạo & quét | Toast hiện; quan sát được ≥ 3 giai đoạn đang chạy; xong thì cả 6 giai đoạn có thời gian đo |
| 2 | Mở tab Backend | Cây hiện đúng số module / endpoint của API C1 (36 / 244) |
| 3 | Sửa 1 file Java rồi Quét lại | Làm trên **bản sao của fixture tự viết `spring-mini`**, không sửa mã demo. Lịch sử ghi Mới 0 / Thay đổi 1 / Bị xóa 0 |
| 4 | Hủy giữa chừng (lúc đang "Đọc & trích xuất") | Trạng thái Đã hủy, cây cũ còn nguyên 244 endpoint |

Đã kiểm thêm:
- lọc nhanh có tô sáng;
- chọn endpoint → cột phải;
- quét lại không đổi → 3 giai đoạn "bỏ qua";
- tải lại trang khi đang quét → theo dõi tiếp;
- danh sách file lỗi có lý do;
- lịch sử 3 lần quét.

**Lưu ý:** roadmap so số endpoint với `/v3/api-docs` của demo. Việc này cần chạy chính dự án demo, mà quy định cấm tool chạy dự án được phân tích, nên **người dùng tự đối chiếu**. Mình chỉ kiểm được cây khớp với API C1 của Engine.

### 6.4. Việc còn dở

- Tab Frontend, sequence diagram, Diễn giải: các giai đoạn sau.
- Key endpoint trùng giữa các ứng dụng trong cùng Source: chờ người dùng quyết định (mục 4).

---

## 7. Bổ sung sau review (12/10/2026): giải thuộc tính cấu hình trong đường dẫn, tiêu đề API

### 7.1. Giải `${khóa}` trong đường dẫn (`PropertyLookup`)

Theo đề xuất của người dùng, có chỉnh sửa; xem căn cứ ở mục 7.3.

- **Tra theo yêu cầu:** chỉ khi đường dẫn của `@GetMapping`… chứa `${…}` mới đọc `application.yml` / `.yaml` / `.properties` **mặc định** của module chứa file Java đó (module sâu nhất; `.properties` trước `.yml` như Spring).
- **Thứ tự ưu tiên như Spring:**
  1. giá trị trong file cấu hình;
  2. mặc định viết trong code `${khóa:/mặc định}`;
  3. không có gì thì giữ `${khóa}`, `pathResolved = false`.
- Giá trị trong file cấu hình có thể lại là `${ENV:/x}`: lấy phần mặc định `/x`. Nếu là `${ENV}` không mặc định thì chưa giải được, lý do "lấy từ biến môi trường lúc chạy". Hỗ trợ tham chiếu sang khóa khác và có chặn vòng (tối đa 5 cấp).
- **Lý do chưa giải được luôn cụ thể:** khóa vắng mặt, biến môi trường, chỉ có ở file profile (không biết profile nào chạy nên không dùng), khóa nhạy cảm, giá trị không phải đường dẫn, tham chiếu vòng.
- **Căn cứ của giá trị đã giải** lưu trong `attrs.pathSources`, vd `khóa = /giá-trị (application.yml)` hoặc `(mặc định trong code)`.
- **An toàn:**
  - khóa có đoạn `password / secret / token / credential` bị chặn;
  - giá trị đọc từ file cấu hình phải có dạng đường dẫn (không khoảng trắng, ≤ 200 ký tự);
  - không lưu giá trị nào ngoài kết quả cuối là đường dẫn.
  - `CLAUDE.md` §3.4 đã cập nhật cho khớp.

### 7.2. Sửa `application.yml` thì Quét lại phải nhận ra

- Trước đây chỉ file `.java` được so mã băm, nên sửa `application.yml` sẽ báo "không có thay đổi" và giữ đường dẫn cũ.
- SPI `StackAdapter` thêm `affectsAnalysis(path)`. Spring khai báo `application*.yml / .yaml / .properties` trong `src/main/resources`.
- Các file này được băm và lưu ở `source_files` với `language = 'config'` (không cần migration). Chúng **không** tính vào số file.
- Có file cấu hình đổi thì Quét lại ghép kết quả lại: file `.java` lấy từ cache, chỉ chạy lại bước ghép và lưu.
- Thống kê có thêm `configChanges`. Khung tiến độ ghi "Có N file cấu hình thay đổi… nên kết quả được ghép lại".

### 7.3. Điều chỉnh so với đề xuất

| Đề xuất ban đầu | Làm | Lý do |
| :--- | :--- | :--- |
| Mặc định trong code đứng trước cấu hình | Cấu hình thắng, mặc định trong code đứng sau | Spring dùng mặc định chỉ khi khóa vắng mặt. Bản trước của mình cũng sai chỗ này |
| Chặn khóa chứa `key`, `auth`, `cert` | Chỉ chặn `password / passwd / secret / token / credential(s)` theo từng đoạn | `key`, `auth`, `cert` quá chung, chặn nhầm khóa đường dẫn hợp lệ |
| Chặn mọi khóa có đoạn nhạy cảm | Cho phép khi **đoạn cuối** là `path / paths / endpoint / uri / route` **và** giá trị bắt đầu bằng `/` | Trên demo, khóa dạng `…token-path` là đường dẫn tới chức năng cấp token, bị chặn nhầm. Cần người dùng xác nhận quy tắc này |
| Thêm kiểm tra | Giá trị phải có dạng đường dẫn | An toàn hơn danh sách tên |

### 7.4. Lỗi tìm thấy khi chạy trên demo

- **Mọi file `application.yml` lưu UTF-8 có BOM** (vd tạo bằng Notepad) bị đọc sai tên khóa đầu tiên, nên không tìm thấy khóa gốc.
  - Lỗi này cũng làm bộ đọc `context-path` bỏ sót với các file loại đó.
  - Đã sửa và thêm test.
- Kết quả trên demo:

| | Trước | Sau |
| :--- | :--- | :--- |
| Endpoint có `${…}` trong đường dẫn | 4 (chỉ cảnh báo, không giải được) | **0** (đều giải được từ `application.yml`) |
| Cảnh báo của lần quét | 7 | 3 (đều là trùng endpoint) |
| Quét lần đầu / quét lại không đổi | khoảng 8–11 giây / 3–6 giây | khoảng 7–10 giây / 3–4 giây |

### 7.5. Tiêu đề API: giữ đường dẫn (người dùng quyết định, 12/10/2026)

- Bản vẽ UI mẫu dùng **tên ngắn** làm nhãn trên cây và làm tiêu đề cột phải; Task 2.3 hiển thị **đường dẫn**. Đây là khác biệt so với bản vẽ, không phải quyết định thiết kế.
- Mình đã thử đổi sang tên ngắn, nhưng trên demo chỉ 106/244 endpoint có mô tả (`@Operation` hoặc Javadoc), danh sách sẽ lẫn hai kiểu nhãn.
- **Người dùng quyết định giữ đường dẫn** ở cây và tiêu đề cột phải; mô tả ngắn hiện ở tooltip và mục "Mô tả" (kèm nguồn). Đã hoàn lại.
- **Yêu cầu cho các giai đoạn sau:** đầu ra phải theo UI mẫu; chỗ nào không làm được thì báo lại người dùng, không tự lệch.

### 7.6. Kiểm thử

| Lệnh | Kết quả |
| :--- | :--- |
| `mvnw -f engine/pom.xml clean verify` | ✅ 88 test (adapter-spring 27, app 20 + 18 IT; golden `spring-mini` nay 17 endpoint) |
| `npm --prefix web run test` / `lint` / `build` | ✅ 24 test / sạch / build được |
| `check-forbidden-terms --all` | ✅ sạch |

