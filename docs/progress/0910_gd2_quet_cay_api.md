# Báo cáo Giai đoạn 2 — Quét & cây API Backend (09/10/2026)

> **Mục tiêu giai đoạn:** bấm Quét trên giao diện, thấy tiến độ, rồi thấy cây API của dự án demo.
> **Phần đã làm:** **BE** (Task 2.1 pipeline quét + lưu IR, Task 2.2 bộ phân tích Spring: endpoint và module, API C1).
> **Kết quả:**
> - Engine `clean verify` xanh (74 test). Sidecar test xanh, web build được (chưa đổi FE).
> - `check-forbidden-terms --all` sạch.
> - Quét được dự án demo qua API thật.
>
> **Chưa commit**, chờ người dùng duyệt phần BE rồi mới làm FE (Task 2.3).
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
