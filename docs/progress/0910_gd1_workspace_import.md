# Báo cáo Giai đoạn 1 — Workspace & Import dự án (09/10/2026)

> **Mục tiêu:** import được dự án demo trên giao diện thật, thấy nguồn mã được tự nhận diện (roadmap, Giai đoạn 1).
> **Kết quả:**
> - Engine: API A1–A10, I4–I5 chạy được.
> - Nhận diện thư mục đủ 4 trường hợp, chạy đúng trên dự án demo.
> - Web có header, thanh icon, trang danh sách, wizard Import 3 bước, trang Cài đặt › Nguồn mã.
> - Engine `clean verify`, sidecar test/lint, web build/test/lint đều xanh; `check-forbidden-terms --all` sạch.
>
> **Chưa commit**, chờ người dùng review.
> Phần trước đó: [0910_add_roadmap_&_adjust_UI.md](0910_add_roadmap_&_adjust_UI.md).

---

## 1. Chuẩn bị môi trường

- **`.env`** (gitignored):
  - `SOURCE_ROOT=D:/`, vì dự án demo và repo này cùng nằm ở ổ D;
  - `LLM_PROVIDER=none`.
- **Cổng PostgreSQL:**
  - máy đã có PostgreSQL cài sẵn chiếm cổng 5432 (service Windows);
  - compose giờ mở cổng theo biến `DB_PORT` (mặc định 5432); `.env` đặt `DB_PORT=5433` và `DB_URL=…:5433/…`;
  - `.env.example` có thêm `DB_PORT`; roadmap ghi chú cách xử lý.
- **Profile `dev`** của Engine:
  - tự đọc `.env` ở gốc repo (`spring.config.import`);
  - trỏ sidecar tới `../../analyzer-node/dist/cli.js`.
- **`.forbidden-terms`** (gitignored): 7 từ khóa nội bộ lấy từ tên package/module của dự án demo. **Bạn nên rà lại và bổ sung.**

## 2. Task 1.1 — Nhận diện thư mục và API Workspace (BE)

### 2.1. Nhận diện thư mục

Lõi không chứa logic riêng của công nghệ nào.

- **SPI:** `StackAdapter` thêm `rootMarkers()`, tức tên file đặc trưng ở gốc dự án.
- **`core/detection/FolderDetector`:**
  - duyệt theo chiều rộng tối đa 4 tầng, sắp xếp ổn định;
  - bỏ qua `node_modules`, `dist`, `target`, `build`, `vendor` và thư mục ẩn;
  - giới hạn 5.000 thư mục.
- **Kết quả trả về:**
  - `VALID` kèm các gốc dự án. Cùng một công nghệ thì chỉ giữ gốc ở cấp cao nhất (Maven nhiều module lấy `pom.xml` gốc).
  - `INSIDE_PROJECT` kèm gốc gợi ý. Bộ nhận diện dò ngược lên thư mục cha; chọn một module con thì gợi ý `pom.xml` gốc.
  - `NOT_FOUND`.
- **Gợi ý mã cũ (chỉ báo):**
  - gom đuôi file theo ngôn ngữ (`php` + `tpl`), duyệt sâu tới 8 tầng;
  - cần ít nhất 10 file `.php` và tỉ lệ ≥ 50%;
  - tự đi xuống thư mục chứa toàn bộ mã đó.
- **Spring** (`adapter-spring/SpringDetector`):
  - `pom.xml` / `build.gradle(.kts)` có spring-boot;
  - đọc phiên bản từ parent, BOM hoặc `${property}`;
  - Maven `<modules>` và Gradle `include` cho dự án nhiều module.
- **Angular** (sidecar, lệnh `detect` mới):
  - Engine gửi **nội dung** `angular.json` và `package.json`; sidecar không tự đọc file;
  - contracts mới: `detect-request.schema.json`, `detect-result.schema.json`; `sidecar-protocol.md` có mục lệnh `detect`;
  - phía Engine: `scan/adapter/SidecarStackAdapter` và `SidecarRunner`;
  - sidecar lỗi hoặc chưa build thì trả cảnh báo, không làm hỏng phần còn lại.

### 2.2. Package `workspace`

- **Service:**
  - `WorkspaceService`: A1–A5, I4–I5. Trùng tên → `409 WORKSPACE_NAME_TAKEN`; đang quét → `409 SCAN_IN_PROGRESS`.
  - `SourceService`: A7–A9. Thư mục không phải gốc dự án → `422 NO_PROJECT_ROOT`, kèm `suggestedRoot`; trùng → `409 SOURCE_ALREADY_EXISTS`.
  - `NestedSourceRules`: Source cha tự loại thư mục con. Luật này được giữ lại khi người dùng sửa danh sách loại trừ, và được gỡ khi Source con bị xóa.
  - `StackDetectionService`: A10.
  - `FolderBrowseService`: A6.
- **Controller (mỏng):** `WorkspaceController`, `SourceController`, `DetectionController`, `FolderBrowseController`, `NoiseFilterController`.
- **`scan/service/RunningScanRegistry`:** sổ "đang quét", để chặn xóa/sửa khi đang quét. Giai đoạn 2 sẽ nối với `ScanService`.
- **Phụ lục B** của `project_structure.md` thêm `RunningScanRegistry`, `AdapterRegistry`: package `workspace` được gọi hai thành phần này.

### 2.3. An toàn đường dẫn

- `LocalSourceReader` chặn ngay mọi đường dẫn có đoạn `..` hoặc là đường dẫn tuyệt đối.
- Trước đây, khi `SOURCE_ROOT` là gốc ổ đĩa, `../..` không bị chặn: trên Windows nó vẫn trỏ về `D:\`, tức không thoát ra ngoài thật, nhưng yêu cầu là phải trả `400`.

### 2.4. Sửa phát sinh

- `WebConfig` chỉ gắn tiền tố `/api` cho controller của tool. Trước đó springdoc bị đẩy sang `/api/v3/api-docs`, làm `gen:api` lỗi.
- `GlobalExceptionHandler` trả `400` cho JSON sai, UUID sai, thiếu tham số; trả `404` cho đường dẫn không tồn tại. Trước đây các trường hợp này đều thành `500`.

### 2.5. Script, fixture và test

- **`scripts/check-forbidden-terms.mjs`:**
  - mặc định quét bản trong index của các file đã `git add`; `--all` quét file đã theo dõi + file mới không bị ignore;
  - kiểm tra cả tên file;
  - mã thoát: 0 sạch, 1 có vi phạm, 2 lỗi chạy.
- **Mini fixture `fixtures/detection/`:**
  - `shop-monorepo` (BE + FE);
  - `shop-fullstack` (FE nằm trong repo BE);
  - `unrelated`.
- **Test mới:**

  | Test | Số ca | Nội dung |
  | :--- | :--- | :--- |
  | `FolderDetectorTest` | 16 | cây thư mục giả trong bộ nhớ |
  | `SpringDetectorTest` | 7 | |
  | `NestedSourceRulesTest` | 6 | |
  | `LocalSourceReaderTest` | 2 | |
  | `WorkspaceApiIT` | 8 | PostgreSQL thật, gồm cả `GET /api/fs/browse?path=../..` → 400 |
  | `angular-detect.spec.ts` | 5 | có kiểm tra khớp schema |

## 3. Task 1.2 — Khung web và trang Workspace (FE)

- **Không cài thêm thư viện.**
  - Chưa cài Angular Material; style viết bằng CSS thuần theo `docs/UI/UI_offline.html` (màu, nút, nhãn, thẻ, bảng, modal, toast, trạng thái rỗng/đang tải).
  - Khi cần CDK cho canvas ở giai đoạn sau sẽ hỏi trước khi cài.
- **Khung:**
  - header: chọn Workspace, ô tìm kiếm ở giữa (chưa hoạt động), đèn "AI tắt", nút Import;
  - thanh icon: Danh sách / Màn hình chính / Cài đặt / Cấu hình AI;
  - dùng chung: toast, modal (Esc chỉ đóng modal trên cùng), hộp xác nhận, icon SVG.
- **Kiểu API:**
  - `core/api/generated/schema.d.ts` sinh bằng `gen:api` và được **commit**, để web build được khi Engine không chạy;
  - `WorkspaceApi` bọc A1–A10, I4–I5.
- **Trang Danh sách Workspace:**
  - thẻ Workspace có nhãn vai trò · công nghệ;
  - menu Mở / Cài đặt / Xóa;
  - trạng thái rỗng, đang tải, lỗi kết nối Engine.
- **Wizard Import 3 bước:**
  - bước 2 nhập đường dẫn hoặc duyệt thư mục (A6), rồi nhận diện (A10). Thông báo theo 4 trường hợp; có nút "Dùng <gốc>"; gợi ý PHP chỉ báo;
  - bước 3 là bảng xác nhận, sau đó tạo (A2). Trùng tên thì báo ở bước 1.
- **Trang Cài đặt › Nguồn mã:**
  - sửa tên/mô tả Workspace (A4);
  - thêm thư mục (A7, có gợi ý gốc khi gặp lỗi 422);
  - sửa tên và thư mục loại trừ (A8), gỡ (A9);
  - vùng nguy hiểm: xóa Workspace (A5).
- **Test:**
  - `detection-message.spec.ts`: 4 trường hợp và gợi ý PHP;
  - `workspace.api.spec.ts`: đường dẫn/tham số, lỗi 409, mất kết nối;
  - `app.spec.ts`: khung, chọn Workspace, mở wizard.

## 4. Kết quả kiểm thử

| Lệnh | Kết quả |
| :--- | :--- |
| `mvnw -f engine/pom.xml clean verify` | ✅ 57 test: ir 4, core 18, adapter-spring 8, app 18 unit/ArchUnit + 9 IT. ArchUnit không nới luật |
| `npm --prefix analyzer-node run test` / `lint` | ✅ 8 test / sạch |
| `npm --prefix web run test` / `lint` / `build` | ✅ 9 test / sạch / build được |
| `node scripts/check-forbidden-terms.mjs --all` | ✅ sạch (348 file) |

**Kiểm chứng trên giao diện với dự án demo** (Chrome headless điều khiển tự động; Engine dev + `ng serve`): 4 ca của roadmap đều đạt.

| # | Ca | Kết quả |
| :--- | :--- | :--- |
| 1 | Duyệt tới thư mục chứa cả BE + FE | Tự tách 2 nguồn: Backend · Spring Boot 3.3.x và Frontend · Angular 21.x. Báo có thư mục mã PHP cũ (khoảng 2,6 nghìn file trong giới hạn duyệt) |
| 2 | Chọn thư mục con bên trong BE | Gợi ý "Dùng <gốc BE>" |
| 3 | Tạo Workspace trùng tên | Báo lỗi ở bước 1 |
| 4 | Xóa Workspace | Biến khỏi danh sách; thư mục demo không bị đụng (Engine chỉ đọc qua `SourceReader`) |

- Đã kiểm thêm: sửa thư mục loại trừ, thư mục không liên quan → `NOT_FOUND`, trình duyệt thư mục.
- Thời gian nhận diện:
  - thư mục dự án demo: khoảng 1–2 giây;
  - cả ổ D: khoảng 6,5 giây.

## 5. Việc còn dở / để giai đoạn sau

- **Thẻ Workspace luôn hiện "Chưa quét":** trạng thái lần quét gần nhất sẽ thêm vào `WorkspaceResponse` ở Giai đoạn 2.
- **Xóa Workspace chưa xóa cache dữ kiện trên ổ đĩa:** cache này chưa có, sẽ làm ở Giai đoạn 2.
- **Chưa có UI bộ lọc nhiễu:** API I4–I5 đã có, UI làm ở Task 5.2. Các tab Cài đặt khác làm ở giai đoạn tương ứng.
- **Chưa chạy `spotless:apply`:** theo quy ước, không định dạng lại toàn bộ engine. File mới viết theo style hiện có.

## 6. Vấn đề gặp phải

- **Heredoc dài trong Bash lỗi** khi nội dung có backtick. Đã chuyển sang ghi file trực tiếp.
- **Cổng 5432 bị PostgreSQL cài sẵn chiếm.** Không đụng vào service đó; chuyển sang dùng `DB_PORT`.
- **Mã PHP của demo nằm sâu hơn 4 tầng**, và file `.tpl` nhiều gấp vài lần `.php`. Đã tách độ sâu riêng cho phần gợi ý (8 tầng) và gom đuôi file theo ngôn ngữ.
- **Gợi ý nhầm** một thư mục preset có đuôi `.tpl` của phần mềm khác trên ổ D. Đã sửa bằng cách bắt buộc có đủ file `.php`.

---

## 7. Chỉnh sửa sau review của người dùng (09/10/2026)

### 7.1. BE — trình bày code chuyên nghiệp hơn

- **Tách DTO thành `dto/request/` và `dto/response/`.**
  - Tên package viết thường theo quy ước Java và `CLAUDE.md`, giống cách dự án demo đặt.
  - Mỗi DTO là một file riêng: mỗi trường một dòng, có Javadoc.
  - Không còn record lồng trong DTO khác (trước đây có `DetectionResponse.Root`, `NoiseFiltersRequest.Item`).
  - Response không trả thẳng entity hay kiểu của lõi; luôn qua `*Response.from(...)`.
- **Validate đầy đủ, thông điệp tiếng Việt:**
  - độ dài tên, mô tả, đường dẫn;
  - số phần tử của danh sách;
  - đường dẫn phải tương đối, không có `..`, không có ổ đĩa;
  - glob loại trừ cũng phải tương đối;
  - tham số `path` của A6 cũng được validate.
  - Các mẫu regex dùng chung nằm ở `common/web/ValidationPatterns`.
- **Tách DTO theo thao tác:** `SourceRequest` thành `CreateSourceRequest` và `UpdateSourceRequest`; `DetectionRequest` đổi tên thành `DetectFolderRequest`; thêm `DetectionStatus`, `DetectedRootResponse`, `LegacyHintResponse`, `DetectedStackResponse`, `NoiseFilterResponse`, `NoiseFilterItemRequest`.
- **Vỏ bọc `common/web/ApiResponse<T>`** `{status, code, message, data, errors}` cho mọi response, cả thành công lẫn lỗi.
  - Bỏ dạng lỗi cũ `{error: {...}}` (`ApiError`).
  - API xóa trả `200` với `data = null` kèm `message`, thay cho `204`.
  - Thêm mã lỗi `405 METHOD_NOT_ALLOWED`.
- **Không còn trả "điểm phù hợp" (`score`)** trong API. Thay vào đó là `evidence`, tức dấu hiệu đã dùng để nhận diện. `score` chỉ dùng nội bộ làm ngưỡng.
- **Tài liệu cập nhật:** `CLAUDE.md` §3.1, `API_list.md` (quy ước chung, cột Output của API xóa), `overview.md` §giao tiếp, `project_structure.md` (`common/web`, `workspace/dto`).
- **Test:** `WorkspaceApiIT` thêm ca kiểm tra vỏ bọc và lỗi validate (tên trống, `..` trong đường dẫn, glob tuyệt đối, đường dẫn ổ đĩa).

### 7.2. UI — Import dự án

- **Bước 2:**
  - nút **Thêm** bị khóa khi ô đường dẫn trống (cả phím Enter);
  - bỏ thông báo xanh dài dòng khi nhận diện bình thường, kết quả hiện ngay ở danh sách thẻ;
  - chỉ hiện thông báo khi có điều cần chú ý: phát hiện mã cũ ("Phát hiện mã PHP (có thể là hệ thống cũ) trong <thư mục>."), không tìm thấy dự án, thư mục nằm trong dự án (kèm nút "Dùng <gốc>"), hoặc thư mục đã được thêm.
- **Bước 3:** bỏ cột "Độ tin cậy".
  - Trả lời câu hỏi review: con số đó là **điểm cố định** do từng bộ nhận diện gán (0,95 khi có đủ dấu hiệu; 0,9 qua module con; 0,6 khi chỉ có `angular.json`), chỉ dùng làm ngưỡng nội bộ, không có ý nghĩa thống kê.
  - Thay bằng cột **"Dấu hiệu nhận diện"**, vd `pom.xml có spring-boot 3.3.3`, `angular.json + @angular/core ^21.2.0`.

### 7.3. Quy trình từ Giai đoạn 2

- Mỗi giai đoạn làm **BE trước**, bàn giao và chờ người dùng duyệt, rồi mới làm **FE**.
- Đã ghi vào quy ước chung của `roadmap.md`.

### 7.4. Kết quả kiểm thử sau chỉnh sửa

| Lệnh | Kết quả |
| :--- | :--- |
| `mvnw -f engine/pom.xml clean verify` | ✅ 58 test (IT 10) |
| `npm --prefix web run test` / `lint` / `build` | ✅ 10 test / sạch / build được |
| Kiểm chứng giao diện tự động trên dự án demo | ✅ 15/15 mục (thêm: nút Thêm bị khóa khi trống, thông báo mã cũ ngắn, cột Dấu hiệu nhận diện) |
