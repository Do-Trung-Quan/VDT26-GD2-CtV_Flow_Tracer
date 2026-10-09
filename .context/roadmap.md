# Roadmap phát triển — CtV Flow Tracer

> Lộ trình từ **bộ khung hiện tại** (Giai đoạn 0) tới **sản phẩm hoàn chỉnh, đóng gói bằng Docker Compose**, gồm **11 giai đoạn**.
> - **Mỗi giai đoạn là một "lát cắt dọc"**: làm cả Backend (Engine/sidecar) lẫn Frontend (web) của cùng một nhóm chức năng, để kiểm chứng được ngay trên giao diện.
> - Mỗi giai đoạn vừa đủ cho **một lần làm việc của Claude Code**: làm xong, kiểm thử xong, ghi nhật ký rồi mới sang giai đoạn sau.
> - **Phạm vi:**
>   - MVP + Nên có (theo [feature_list.md](feature_list.md));
>   - phần Mở rộng **chỉ gồm** phân tích mã PHP hệ thống cũ và **so sánh & đối chiếu cũ – mới** (Nhóm 8);
>   - các chức năng Mở rộng khác không nằm trong lộ trình này.
> - Giao diện bám theo bản vẽ [docs/UI/UI_offline.html](../docs/UI/UI_offline.html).

---

## Quy ước chung cho mọi giai đoạn

**Dữ liệu dùng để phát triển và kiểm thử:**

| Loại | Dùng cho | Nằm ở đâu |
| :--- | :--- | :--- |
| **Dự án demo** (code công ty) | Phát triển và kiểm chứng trên giao diện ở **mọi** giai đoạn; đo độ chính xác | Chỉ trên máy, gắn qua `SOURCE_ROOT`. **Không** commit, kể cả kết quả chi tiết |
| **Mini fixture tự viết** | Unit test, golden test, CI trên GitHub | `fixtures/`, viết dần trong từng giai đoạn, mỗi giai đoạn chỉ vài file đúng ca cần test |

> Vì sao vẫn cần mini fixture:
> - CI trên GitHub và các golden test trong repo không đọc được dự án demo, mà repo lại công khai.
> - Test tự động cần dữ liệu cố định, biết trước đáp án.
>
> Mini fixture **không** phải một giai đoạn riêng: chỉ viết đúng ca đang làm (ví dụ 1 controller + 1 service có 2 lớp hiện thực).

**Đầu mỗi giai đoạn**, Claude Code đọc:
- [CLAUDE.md](../CLAUDE.md);
- các mục liên quan trong `.context/`;
- nhật ký giai đoạn trước trong `docs/progress/`.

**Cách chia việc trong một giai đoạn (từ Giai đoạn 2):** làm **phần BE trước** → bàn giao (tóm tắt + cách kiểm thử) → người dùng duyệt → mới làm **phần FE**. Không làm BE và FE trong một lượt.

**Cuối mỗi giai đoạn** phải xong đủ 6 việc:
1. `./mvnw -f engine/pom.xml verify`, `npm --prefix analyzer-node run test`, `npm --prefix web run test` đều xanh. `ArchitectureTest` không bị nới luật.
2. **Kiểm chứng trên giao diện** với dự án demo theo checklist của giai đoạn.
3. `node scripts/check-forbidden-terms.mjs` sạch (có từ Giai đoạn 1).
4. Đổi API / CSDL / IR thì cập nhật `.context/` và `contracts/` trong cùng giai đoạn.
5. **Tạo file nhật ký mới** `docs/progress/<ddmm>_gd<N>_<chu-de>.md` cho giai đoạn này (một giai đoạn một file): việc đã làm theo từng task, kết quả kiểm thử, việc còn dở, vấn đề gặp phải.
6. Người dùng review và tự commit. Claude Code không tự commit.

**Cách chạy khi kiểm chứng trên giao diện:**
```bash
docker compose -f docker/docker-compose.yml up -d postgres
./mvnw -f engine/pom.xml -pl flowtracer-app spring-boot:run -Dspring-boot.run.profiles=dev   # Engine: http://localhost:8600
npm --prefix web start                                                                        # Web:    http://localhost:4200
```

**Ký hiệu:** 🔴 bắt buộc theo thứ tự · 🟠 lõi MVP · 🟢 hoàn thiện / đóng gói · ⚪ mở rộng.

---

## Tiến độ hiện tại

| Giai đoạn | Nội dung | Trạng thái |
| :--- | :--- | :--- |
| 0 | Tài liệu `.context/`, khung mã nguồn (engine 4 module, sidecar, web, contracts), migration V1–V2, bản vẽ giao diện | ✅ Xong |
| 1 | Workspace & Import dự án — nhật ký: `docs/progress/0910_gd1_workspace_import.md` | ✅ Xong, chờ người dùng review |
| 2 → 11 | Xem bên dưới | ⏳ Chưa làm |

---

## Chuẩn bị môi trường (làm 1 lần)

1. **Công cụ:** JDK 21+, Node.js 22, Docker Desktop (bật trước khi chạy test có CSDL), Git.
2. **Cài thư viện:**
   ```bash
   npm --prefix analyzer-node ci
   npx -y npm@11 --prefix web ci       # npm 10.9.2 có lỗi khi cài web
   ```
3. **Tạo `.env`** từ `.env.example`:
   - `SOURCE_ROOT` = thư mục cha chứa dự án demo (gắn chỉ đọc);
   - `LLM_PROVIDER=none` cho tới Giai đoạn 7;
   - máy đã có PostgreSQL chiếm cổng 5432 thì đặt `DB_PORT=5433` và `DB_URL=jdbc:postgresql://localhost:5433/flowtracer`.
   - Profile `dev` của Engine tự đọc `.env` ở gốc repo.
4. **Tạo `.forbidden-terms`** (đã gitignore): liệt kê tên dự án, tên package, domain nội bộ của dự án demo để script chặn rò rỉ.
5. **Công cụ hỗ trợ:** Swagger UI của Engine (`/swagger-ui.html`), DBeaver/TablePlus để xem CSDL, plugin xem PlantUML.

---

## Giai đoạn 1 — Workspace & Import dự án 🔴

> Kết thúc giai đoạn: **import được dự án demo trên giao diện thật**, thấy nguồn mã được tự nhận diện.

### Task 1.1 — Nhận diện thư mục và API Workspace (BE)

**Mục tiêu:** Engine tạo, sửa, xóa Workspace / nguồn mã, duyệt thư mục an toàn, nhận diện thư mục theo 4 luật ở Nhóm 1.

Tôi sẽ làm:
- `flowtracer-core/detection/StackDetector`:
  - trả `VALID` (kèm các gốc dự án), `INSIDE_PROJECT` (kèm gốc gợi ý) hoặc `NOT_FOUND`;
  - xử lý Maven nhiều module;
  - phát hiện thư mục mã PHP nằm trong `docs/` của BE và chỉ **báo** là có; Source `LEGACY` để tới Giai đoạn 9.
- Package `workspace`:
  - `WorkspaceService` (`409` khi trùng tên / đang quét), `SourceService` + `NestedSourceRules`;
  - API A1–A10 (`POST /api/fs/detect`), I4–I5;
  - chặn path traversal và symlink ra ngoài `SOURCE_ROOT`.
- `scripts/check-forbidden-terms.mjs` (đọc `.forbidden-terms`, quét file sắp commit; `--all` quét cả repo).
- Mini fixture: vài cây thư mục giả cho unit test nhận diện.

**Cách kiểm thử:**
```bash
./mvnw -f engine/pom.xml verify
node scripts/check-forbidden-terms.mjs --all
```

| Ca (unit test) | Kết quả mong đợi |
| :--- | :--- |
| Thư mục không liên quan | `NOT_FOUND` |
| Thư mục con trong dự án BE | `INSIDE_PROJECT`, gợi ý gốc |
| Thư mục chứa cả BE và FE (kể cả chính repo này) | `VALID`, đúng số gốc |
| `GET /api/fs/browse?path=../..` | `400` |

---

### Task 1.2 — Khung web và trang Workspace (FE)

**Mục tiêu:** Có giao diện thật cho danh sách Workspace và luồng Import.

Tôi sẽ làm:
- Khung Angular:
  - header: chọn Workspace, ô tìm kiếm ở giữa (chưa hoạt động), đèn AI, nút Import dự án;
  - thanh icon, theme màu, toast, modal, trạng thái rỗng / đang tải dùng chung.
- Sinh kiểu API: `npm --prefix web run gen:api`.
- Trang Danh sách Workspace (A1, A5).
- Wizard Import 3 bước: thông tin → chọn thư mục qua trình duyệt thư mục (A6) + nhận diện (A10) với thông báo theo 4 trường hợp → xác nhận và tạo (A2).
- Trang Cài đặt › tab Nguồn mã: thêm / sửa tên và thư mục loại trừ / gỡ, vùng nguy hiểm xóa Workspace.

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Import → duyệt tới thư mục chứa cả BE và FE của demo | Tự tách 2 nguồn đúng vai trò |
| 2 | Chọn một thư mục con bên trong BE | Gợi ý "Dùng <gốc BE>" |
| 3 | Tạo Workspace trùng tên | Báo lỗi ở bước 1 |
| 4 | Xóa Workspace | Biến khỏi danh sách; thư mục demo không bị đụng |

---

## Giai đoạn 2 — Quét & cây API Backend 🔴

> Kết thúc giai đoạn: **bấm Quét trên giao diện, thấy tiến độ, rồi thấy cây API của dự án demo** khớp Swagger.

### Task 2.1 — Pipeline quét và lưu IR (BE)

**Mục tiêu:** Một lần quét chạy nền, có tiến độ, chịu được file lỗi, không làm hỏng dữ liệu cũ khi thất bại.

Tôi sẽ làm:
- `scan/pipeline`: DISCOVER → DIFF → EXTRACT → RESOLVE → PERSIST → LINK → FINALIZE.
  - Mã băm file và so với `source_file` để ra file mới / thay đổi / bị xóa.
  - Lô xử lý trên virtual threads; cache dữ kiện theo mã băm.
- `GraphWriteService`: ghi `ir_node` / `ir_edge` bằng JdbcClient theo lô, **trong một transaction**.
- Quy tắc của lần quét:
  - mỗi Workspace một lần quét (`409`);
  - file lỗi → `PARTIAL`;
  - hủy hoặc thất bại thì giữ IR cũ.
- API B1–B6, SSE tiến độ (B2).

**Cách kiểm thử:** test tích hợp với adapter giả (quét lần đầu / quét lại 2 file / hủy giữa chừng / quét chồng → `409`).

---

### Task 2.2 — Bộ phân tích Spring: endpoint và module (BE)

**Mục tiêu:** Đọc được toàn bộ endpoint của Backend.

Tôi sẽ làm:
- `TypeDeclarationExtractor`, `MappingExtractor`.
- `EndpointBuilder`: ghép đường dẫn cấp class và cấp method, cộng `context-path` từ `ApplicationConfigReader`.
- Lấy `@Tag`, `@Operation`, Javadoc; module theo `@Tag` → Controller → package.
- `TreeBuilder` + API C1.
- File sai cú pháp → `Diagnostic`, không dừng lần quét.
- Mini fixture `fixtures/spring-mini`: 2 controller có `@Tag` / không `@Tag`, `context-path`, 1 file lỗi cú pháp; golden test.

**Cách kiểm thử:** `./mvnw -f engine/pom.xml -pl flowtracer-adapter-spring test` (golden).

---

### Task 2.3 — Giao diện quét và cột trái Backend (FE)

**Mục tiêu:** Quét và duyệt cây API ngay trên giao diện.

Tôi sẽ làm:
- Toast tiến độ quét qua SSE: 6 giai đoạn, thu nhỏ, hủy (B4), xem file lỗi (B6).
- Màn hình chính: khung 3 cột; cột trái tab Backend (lọc nhanh, kéo đổi độ rộng, thu gọn); cụm nút nổi góc trái trên (trạng thái quét, Quét lại).
- Cài đặt › tab Lịch sử quét (cột File mới / thay đổi / bị xóa / lỗi).

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Bấm Quét | Thanh tiến độ chạy qua 6 giai đoạn |
| 2 | Mở tab Backend | Số endpoint khớp `/v3/api-docs` của demo (đếm bằng tay hoặc script nhỏ) |
| 3 | Sửa 1 file Java của demo trong bản sao rồi Quét lại | Lịch sử ghi "1 file thay đổi" |
| 4 | Hủy giữa chừng | Cây cũ vẫn còn nguyên |

---

## Giai đoạn 3 — Luồng xử lý Backend & sequence diagram 🟠

> Kết thúc giai đoạn: **bấm một API trong cây → thấy diagram Controller → Service → Repository → Database** của dự án demo.

### Task 3.1 — Phân giải Spring: tiêm phụ thuộc, lớp hiện thực, truy vấn, bảng (BE)

**Mục tiêu:** Nối lời gọi tới đúng method đích, kèm mức tin cậy; biết method nào đọc / ghi bảng nào.

Tôi sẽ làm:
- Trích xuất:
  - `MemberExtractor` (Lombok `@RequiredArgsConstructor`, `@Qualifier`, `@Primary`);
  - `CallSiteExtractor` (thứ tự lời gọi, điều kiện bao quanh);
  - `ThrowExtractor`, `StringFragmentExtractor`.
- Phân giải:
  - `ClassIndex`, `JavaResolver` (SymbolSolver), `InjectionResolver`, `ImplementationResolver`;
  - mức tin cậy: `CERTAIN` / `INFERRED` / `UNRESOLVED` + ứng viên;
  - `DataAccessResolver` + `QueryTextAnalyzer` (Spring Data, `@Query`, câu ghép động, `QuerySinkRules`), `EntityTableResolver`.
- Mini fixture: interface có 2 lớp hiện thực (`@Primary`), repository có `@Query` và câu ghép bằng `StringBuilder`.

**Cách kiểm thử:** golden IR + test tất định (chạy 2 lần cho cùng kết quả).

---

### Task 3.2 — Tracer, cột theo vai trò, xuất diagram (BE)

**Mục tiêu:** Sinh `Trace` đúng mô hình §2.2.4 của overview cho luồng bắt đầu từ API.

Tôi sẽ làm:
- `Tracer`: `Subgraph`, `CycleGuard` (độ sâu 12), bộ lọc nhiễu mặc định.
- Dựng cột và bước:
  - `ParticipantBuilder`: Client · Controller X · Service X (nhiều cột nếu gọi nhiều service) · Repository X · Database;
  - `SelfStepBuilder`;
  - chiều trả về;
  - `BlockBuilder` (`alt` / `opt` từ `if … throw`).
- Nhãn mũi tên ngắn và tất định.
- `TracingService` + `TraceCache` + D1.
- `PlantUmlExporter`, `SvgExporter` + D2.

**Cách kiểm thử:** golden trace cho mini fixture; dò vết ≤ 2 giây (NFR-04).

---

### Task 3.3 — Canvas sequence diagram (FE)

**Mục tiêu:** Vẽ diagram tương tác giống bản vẽ.

Tôi sẽ làm:
- Renderer SVG kiểu PlantUML:
  - User / Client hình người, Database hình trụ, tiêu đề cột lặp ở đáy;
  - thanh kích hoạt, số bước, nhãn chiều trả về "trả về …", khối `alt` / `else`;
  - nét theo mức tin cậy.
- Zoom, kéo, vừa khung (tối thiểu 85%).
- Cụm nút góc phải dưới: zoom, Xuất PlantUML / SVG / PNG.
- Chú giải thu gọn được; tooltip khi di chuột.
- Thẻ cảnh báo "n bước chưa xác định" ở góc phải trên.

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Bấm một API thêm mới của demo | Diagram đủ các cột tới Database; nhánh lỗi thành khối `alt` |
| 2 | Xuất PlantUML, mở bằng trình xem PlantUML | Vẽ được, đúng thứ tự bước |
| 3 | Bấm một API có interface nhiều lớp hiện thực | Có mũi tên "Chưa xác định" và thẻ cảnh báo |

---

## Giai đoạn 4 — Frontend Angular & tìm kiếm nhanh 🟠

> Kết thúc giai đoạn: **thấy cây thành phần Frontend của demo** và luồng "chỉ trên giao diện" của các nút không gọi máy chủ.

### Task 4.1 — Sidecar Angular (BE)

**Mục tiêu:** Dựng cây Ứng dụng → Màn hình → Hộp thoại/Form → Phần tử, và tìm được lời gọi HTTP của từng nút.

Tôi sẽ làm (`analyzer-node/src/adapters/angular/`):
- Đọc `angular.json`, cấu hình federation, route.
- `parseTemplate`: lấy sự kiện `(click)`, `(ngSubmit)`, `(change)` và output của component con.
- Nhãn từ i18n `vi`; hộp thoại mở bằng `dialog.open(...)`.
- TypeScript type checker lần handler → facade → repository (`InjectionToken`) → `HttpClient`.
- `urlExpr` / `urlPattern` ghép từ hằng số và `environment*.ts`; luật proxy tự phát hiện.
- Chỉ đọc file Engine liệt kê. Cache dữ kiện.
- Engine: `SidecarStackAdapter` (stdin / stdout / stderr, mã thoát 2/3/4, timeout), **kiểm tra đầu ra bằng JSON Schema**.
- Mini fixture `fixtures/angular-mini` + golden.

**Cách kiểm thử:**
```bash
npm --prefix analyzer-node run test
./mvnw -f engine/pom.xml verify      # IT: Engine quét mini fixture qua sidecar
```

---

### Task 4.2 — Tab Frontend, luồng chỉ giao diện, Ctrl+K (BE + FE)

**Mục tiêu:** Duyệt và tìm mọi thành phần trên giao diện.

Tôi sẽ làm:
- BE:
  - C2 (cây FE);
  - C3 (tìm kiếm toàn văn bằng tsvector);
  - Tracer cho luồng chỉ trên giao diện (User ↔ Frontend).
- FE:
  - tab Frontend (icon theo loại, nhãn "API" / "UI");
  - ô tìm kiếm giữa header + bảng lệnh Ctrl+K (phím mũi tên, Enter, nhóm kết quả theo loại).

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Mở tab Frontend | Đủ các ứng dụng microfrontend và màn hình; nhãn nút tiếng Việt |
| 2 | Bấm nút "Thêm mới" của một màn hình | Diagram 2 cột User ↔ Frontend, ghi "Không gọi máy chủ" |
| 3 | Ctrl+K gõ tên một màn hình | Enter mở đúng thành phần, cây tự mở tới đó |

---

## Giai đoạn 5 — Liên kết Frontend ↔ Backend & luồng End-to-End 🟠

> Kết thúc giai đoạn: **bấm một nút có gọi API → thấy luồng từ User tới Database**, chốt tay được liên kết chưa chắc chắn.

### Task 5.1 — Linker và luật liên kết (BE)

**Mục tiêu:** Nối mọi `HTTP_CALL` với endpoint đích, đúng tiêu chí mức tin cậy ở Nhóm 5.

Tôi sẽ làm:
- `UrlPattern`, `LinkRuleEngine` (tiền tố, proxy, thay biến, context-path), `EndpointMatcher`.
- `CandidateScorer`: tính điểm rồi quy ra **Cao / Trung bình / Thấp**.
- Đích ngoài Workspace → `EXTERNAL`. Lời gọi `@FeignClient` / `RestTemplate` / `WebClient` → nối BE → BE.
- `LinkingService` ở bước LINK.
- Tracer nối luồng FE → BE.

**Cách kiểm thử:** unit test trên mini fixture FE + BE có URL ghép từ biến và luật proxy.

---

### Task 5.2 — Chốt tay, luật, bộ lọc nhiễu (BE + FE)

**Mục tiêu:** Người dùng sửa được liên kết sai và giữ qua mọi lần quét.

Tôi sẽ làm:
- BE:
  - E1;
  - `LinkOverrideService` (PIN / REJECT theo node key) — E3–E5;
  - `LinkRuleService` (nối lại ngay sau khi sửa luật) — E6–E7;
  - bộ lọc nhiễu I4–I5 áp vào Tracer.
- FE:
  - popover liên kết: lý do, ứng viên + mức khớp, nút Chốt tay; nút "Nhờ AI phân tích" để sẵn nhưng mờ cho tới Giai đoạn 7;
  - popover luôn nằm trọn trong màn hình;
  - Cài đặt › tab Luật liên kết, Bộ lọc nhiễu, Liên kết chốt tay.

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Bấm nút Lưu ở một hộp thoại | Luồng User → Frontend → Controller → Service → Repository → Database |
| 2 | Bấm mũi tên "Suy luận" FE → BE | Popover hiện lý do (luật proxy / hằng số) |
| 3 | Chốt tay một liên kết chưa xác định, rồi Quét lại | Chốt tay vẫn áp dụng; xuất hiện trong tab Liên kết chốt tay |
| 4 | Thêm luật liên kết thủ công | Báo "Đã nối lại N liên kết", cảnh báo giảm |

---

## Giai đoạn 6 — Diễn giải & Kịch bản (chưa dùng AI) 🟠

> Kết thúc giai đoạn: **cột phải đầy đủ khi `LLM_PROVIDER=none`**.

### Task 6.1 — Nội dung tất định (BE)

**Mục tiêu:** Có đủ dữ liệu cho tab Diễn giải và tab Kịch bản mà không cần AI.

Tôi sẽ làm:
- `IoSchemaBuilder` / `JsonSchemaBuilder` (bóc `ApiResponse<T>`). Thành phần UI lấy I/O theo API mà nó gọi; luồng chỉ giao diện thì ghi "Không có".
- `ConstraintsExtractor` (ràng buộc thô): Bean Validation, `@PreAuthorize`, `if … throw` + thông báo i18n (`MessageResolver`), unique / khóa ngoại, kiểm tra "đang được tham chiếu".
- `FallbackMockGenerator` + `SchemaValidator`.
- Mục đích dự phòng: `@Operation` → Javadoc → tài liệu → câu khung. Thành phần chứa: tên, loại, mô tả ngắn.
- `ScenarioBuilder`: "Các actor tham gia" + "Bước n" khớp 1–1 mũi tên.
- API C4 ("Được gọi từ / Gọi tới"), C5 (đoạn code theo từng lớp), F1–F3.
- C6 đối chiếu OpenAPI *(Nên có)*.

**Cách kiểm thử:** unit test: số bước kịch bản = số mũi tên với mọi golden trace; dữ liệu mẫu qua được `SchemaValidator`.

---

### Task 6.2 — Cột phải (FE)

**Mục tiêu:** Đọc hiểu một luồng ngay trên giao diện.

Tôi sẽ làm:
- Tab Diễn giải: mục đích, đầu vào / đầu ra (khối JSON, nút sao chép), ràng buộc, dữ liệu mẫu, Được gọi từ / Gọi tới (bấm để chuyển).
- Tab Kịch bản: "Các actor tham gia", "Scenario:", "Bước n" thẳng hàng; tô sáng đồng bộ hai chiều với diagram.
- Popup Xem code chia tab theo lớp.
- Cài đặt › tab Đối chiếu OpenAPI.

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Bấm một API có `@Operation` | Mục đích lấy từ Swagger; ràng buộc thô có các nhánh `throw` |
| 2 | Bấm một nút gọi API | Đầu vào / đầu ra theo API đó; "Được gọi từ" là chuỗi màn hình → hộp thoại → nút |
| 3 | Di chuột qua "Bước 5" | Mũi tên số 5 sáng lên |
| 4 | Đối chiếu OpenAPI với `/v3/api-docs` của demo đang chạy (nếu có) | Báo cáo khớp / lệch; chỉ tiêu NFR-07 |

---

## Giai đoạn 7 — Cổng AI & nội dung AI 🟠

> Kết thúc giai đoạn: **mục đích 80–100 từ, lời kịch bản tự nhiên, dữ liệu mẫu do AI sinh, nút "Nhờ AI phân tích" hoạt động**; tắt AI vẫn đủ chức năng.

### Task 7.1 — `AiGateway` và nhà cung cấp (BE)

**Mục tiêu:** Một cửa duy nhất gọi AI, an toàn và tiết kiệm.

Tôi sẽ làm:
- `AiGateway` 7 bước:
  1. mục đích có bật không;
  2. cache theo `input_hash` (tăng `hit_count` khi trúng);
  3. hạn mức token ngày;
  4. `SecretRedactor` che secret;
  5. gọi provider;
  6. kiểm tra đầu ra theo schema;
  7. lưu `ai_artifact` và ghi `llm_call_log` (chỉ hash, không lưu prompt).
- `SpringAiLlmProvider` (Gemini / Anthropic / OpenAI-compatible) chọn theo `LLM_PROVIDER`.
- API I1–I3, I6.

**Cách kiểm thử:** unit test với provider giả:

| Ca | Kết quả mong đợi |
| :--- | :--- |
| Gọi lần 2 cùng đầu vào | Trúng cache, không gọi provider |
| Code có `password=…` | Bị che trước khi gửi |
| Vượt hạn mức | Dừng gọi, trả kết quả tất định |

---

### Task 7.2 — Các mục đích AI và giao diện AI (BE + FE)

**Mục tiêu:** 4 mục đích MVP hoạt động trên giao diện, đầu ra luôn được kiểm tra.

Tôi sẽ làm:
- BE — prompt `*.v1.st` + schema đầu ra:
  - `purpose-text`: 80–100 từ + câu ràng buộc trỏ về ràng buộc thô;
  - `scenario-polish`: `ScenarioPolishRule` giữ nguyên số bước, thứ tự, thành phần;
  - `mock-data`: khớp schema DTO, sinh một lần;
  - `edge-resolution`: giải thích ứng viên; ứng viên đề xuất thêm phải qua `AiAdviceRule`.
- BE: SSE mục đích (F1 `stream=true`), F4 sinh lại, E2 `/advice`.
- FE:
  - chữ hiện dần, nhãn "✦ AI", nút Sinh lại, nhãn "Bộ sinh dự phòng" khi AI tắt;
  - "Nhờ AI phân tích" trong popover;
  - đèn AI ở header;
  - trang Cấu hình AI (bật / tắt mục đích, kiểm tra kết nối, hạn mức hôm nay, thống kê).

**Cách kiểm chứng trên giao diện (khóa Gemini thật + dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Mở một nút lần đầu | Chữ mục đích đầu tiên ≤ 5 giây (NFR-05); mở lại ≤ 1 giây |
| 2 | "Nhờ AI phân tích" ở mũi tên chưa xác định | Có lời giải thích từng ứng viên; diagram **không** tự đổi |
| 3 | Trang Cấu hình AI | Lượt gọi, token, tỷ lệ trúng cache khớp dữ liệu `llm_call_log` / `ai_artifact` |
| 4 | Đặt `LLM_PROVIDER=none` rồi khởi động lại | Mọi tab vẫn đủ nội dung tất định (NFR-18) |

---

## Giai đoạn 8 — Hoàn thiện & đo lường MVP 🟢

> Kết thúc giai đoạn: **MVP đạt chỉ tiêu NFR-01 → NFR-09 trên dự án demo**, có số liệu.

### Task 8.1 — Script đo và ground truth

**Mục tiêu:** Đo được hiệu năng và độ chính xác bằng số liệu thật.

Tôi sẽ làm:
- `scripts/benchmark.mjs`: thời gian quét lần đầu / quét lại, tải cây, dò vết, RAM.
- `scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <file>`: endpoint so OpenAPI, precision / recall liên kết FE → BE, tỉ lệ trace đúng tới bảng. **Chỉ in số liệu tổng hợp.**
- Ground truth của demo (30 luồng) lập từ tài liệu "map giao diện ↔ API" sẵn có của demo. **File để ngoài repo.**
- **Kiểm tra không bị gắn cứng vào demo:** chạy thêm trên 1–2 dự án mã nguồn mở công khai dùng Spring Boot + Angular (không phải demo). Ghi lại số liệu tổng hợp và những quy ước mà tool chưa hiểu, để sửa luật hoặc ghi vào phần "giới hạn" của báo cáo.

**Cách kiểm thử:**
```bash
node scripts/benchmark.mjs
node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <đường-dẫn-cục-bộ>
```
> ✅ Kết quả mong đợi: in bảng số liệu; ghi vào `docs/evaluation.md` (chỉ con số).

---

### Task 8.2 — Tối ưu và rà soát giao diện

**Mục tiêu:** Đạt chỉ tiêu và mượt khi dùng thật.

Tôi sẽ làm:
- Tối ưu: kích thước lô, số luồng, chỉ mục CSDL, cache dữ kiện, bộ nhớ.
- Sửa các ca sai mà script độ chính xác chỉ ra.
- Rà trạng thái rỗng / đang tải / lỗi, công cụ "Chú thích sự kiện" (nếu giữ).

**Cách kiểm chứng:**
> ✅ Kết quả mong đợi:
> - Quét lần đầu ≤ 3 phút; quét lại ≤ 15 giây; RAM ≤ 2 GB.
> - Endpoint ≥ 98%; liên kết P ≥ 95%, R ≥ 85%; trace đúng ≥ 90%.
> - Chỉ tiêu chưa đạt phải ghi nguyên nhân và hướng xử lý.

---

## Giai đoạn 9 — Mở rộng 1: Phân tích mã PHP hệ thống cũ ⚪

> Bối cảnh (theo Phụ lục B của overview):
> - Mã PHP cũ nằm **trong thư mục tài liệu của repo BE**; framework PHP tự viết; dữ liệu trên MongoDB.
> - Mỗi lớp nghiệp vụ **kế thừa lớp CRUD dùng chung**, khai báo **quyền theo từng action trong một mảng**, đặt **tên collection theo quy ước "Module.Entity"**.
> - Giao diện là file template `.tpl`; định tuyến theo quy ước tên lớp / action.
> - Tài liệu module của BE ghi rõ "file PHP nguồn" của từng API.
>
> Kết thúc giai đoạn: **có tab "Hệ thống cũ" trên giao diện và xem được luồng của một action PHP**.

### Task 9.1 — Nhận diện và Source `LEGACY` (BE + FE)

**Mục tiêu:** Khai báo mã PHP trong `docs/` của BE thành nguồn "Hệ thống cũ" mà không phân tích trùng.

Tôi sẽ làm:
- `StackDetector` chỉ **gợi ý**: thư mục có mật độ `.php` / `.tpl` cao + lớp kế thừa lớp CRUD dùng chung → "có thể là mã hệ thống cũ". Việc xác nhận là của người dùng; tool **không tự quyết định** đâu là "cũ" vì đó là khái niệm nghiệp vụ, không suy ra được từ code.
- Import và Cài đặt › Nguồn mã: thông báo "Phát hiện mã PHP, có thể là hệ thống cũ" + nút **"Thêm làm nguồn Hệ thống cũ"**; người dùng bấm thì Source cha (BE) mới tự loại trừ thư mục này. Mã cũ nằm trộn trong cùng codebase mới (dùng chung, kế thừa) không tách được theo thư mục; khi đó nó được phân tích như một phần của nguồn đang chứa nó.
- API A11 (gợi ý tách mã cũ).

**Cách kiểm chứng trên giao diện:** import lại demo → thấy **đề xuất** nguồn `LEGACY` đúng thư mục, chưa tự thêm; bấm thêm rồi quét → số file Java không đổi (không phân tích trùng).

---

### Task 9.2 — Bộ phân tích `php-legacy` và luồng cũ (BE + FE)

**Mục tiêu:** Dựng cây và luồng của hệ thống cũ theo đúng quy ước framework.

Tôi sẽ làm:
- `analyzer-node/src/adapters/php-legacy` (tree-sitter-php):
  - lớp và lớp cha;
  - mảng quyền theo action;
  - tên collection;
  - các method override CRUD;
  - lời gọi giữa lớp;
  - truy vấn MongoDB;
  - ghi nhật ký thao tác.
- Đọc `.tpl` lấy nút và action gọi tới. Định tuyến theo quy ước → node `HTTP_CALL` / `API_ENDPOINT` phía cũ.
- Hành vi kế thừa từ lớp CRUD (select / edit / delete) được dựng thành bước ngay cả khi lớp con không viết lại.
- Mini fixture `fixtures/php-mini` tự viết (3 lớp, 1 lớp CRUD giả, 2 template) + golden.
- FE: tab "Hệ thống cũ" ở cột trái (Module → Lớp xử lý → Action / Template); diagram và hai tab cột phải dùng chung renderer.

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Mở tab Hệ thống cũ | Thấy các module PHP và lớp xử lý |
| 2 | Bấm action "delete" của một lớp | Luồng Giao diện cũ → Định tuyến → Lớp xử lý → Lớp CRUD → MongoDB, có bước kiểm tra quyền |
| 3 | Lớp con không viết lại `delete` | Vẫn có bước kế thừa từ lớp CRUD, ghi rõ "kế thừa" |

---

## Giai đoạn 10 — Mở rộng 2: So sánh & đối chiếu hệ thống cũ – mới ⚪

> Kết thúc giai đoạn: **mở một luồng mới, bấm "So sánh với hệ thống cũ", thấy hai diagram cạnh nhau và danh sách khác biệt (có vị trí code hai phía)**.

### Task 10.1 — Ghép luồng cũ – mới và tìm khác biệt (BE)

**Mục tiêu:** Tìm đúng luồng tương ứng và chỉ ra chỗ lệch một cách tất định.

Tôi sẽ làm:
- H3 — tìm luồng tương ứng, theo thứ tự bằng chứng:
  1. tài liệu đặc tả ghi rõ file PHP nguồn (mức Cao);
  2. độ giống tên, trường dữ liệu, collection ↔ bảng (Trung bình / Thấp);
  3. AI `LEGACY_MATCH` chỉ xếp hạng khi nhiều ứng viên ngang nhau.
- H1 — gợi ý thành phần để so sánh.
- H2 — so sánh hai trace:
  - ghép bước theo vai trò và hành động;
  - phân loại **giống / chỉ có ở cũ / chỉ có ở mới / khác cách làm**;
  - so ràng buộc (quyền, kiểm tra trước khi xóa, ghi nhật ký) và dữ liệu (collection ↔ bảng, trường).
- Bật 2 mục đích mở rộng trong `AiPurpose` (đã có sẵn trong enum).

**Cách kiểm thử:** golden diff giữa `php-mini` và `spring-mini` (ít nhất 1 ca cho mỗi loại khác biệt).

---

### Task 10.2 — Màn hình so sánh (FE)

**Mục tiêu:** Đối soát trực quan trên giao diện.

Tôi sẽ làm:
- FE:
  - nút "So sánh cũ – mới" ở cột phải;
  - hộp chọn luồng tương ứng (ứng viên + bằng chứng);
  - **chia đôi canvas** (mới | cũ), mỗi khung có tiêu đề, đổi chỗ, đóng; khung đang chọn quyết định nội dung tab Diễn giải / Kịch bản;
  - bước khác biệt tô màu trên cả hai diagram;
  - tab **Đối chiếu** ở cột phải: tổng hợp số khác biệt, danh sách khác biệt kèm vị trí code hai phía (bấm để nhảy tới bước tương ứng trên diagram).

**Cách kiểm chứng trên giao diện (dự án demo):**

| # | Thao tác | Kết quả mong đợi |
| :--- | :--- | :--- |
| 1 | Mở API xóa của một module đã chuyển đổi → "So sánh với hệ thống cũ" | Ứng viên mức Cao kèm bằng chứng "tài liệu ghi file PHP nguồn" |
| 2 | Chọn ứng viên | Hai diagram cạnh nhau; các bước khác biệt được tô màu |
| 3 | Tab Đối chiếu | Có khác biệt kiểu "chỉ có ở cũ" (vd ghi nhật ký) nếu hệ thống mới thiếu |

---

## Giai đoạn 11 — Đóng gói & phát hành 🟢

> Kết thúc giai đoạn: **một lệnh `docker compose up` trên máy sạch là dùng được**, đạt Definition of Done (§1.4 của overview).

### Task 11.1 — Image Docker và Compose

**Mục tiêu:** Chạy toàn bộ tool bằng một lệnh (NFR-23).

Tôi sẽ làm:
- Hoàn thiện `docker/Dockerfile` nhiều stage: web build vào `static/` của Engine; sidecar `dist` (Angular + php-legacy); JRE 21 + Node 22.
- `docker-compose.yml`:
  - `postgres` có volume;
  - healthcheck `/api/health`;
  - chỉ mở `127.0.0.1:8600`;
  - `SOURCE_ROOT` gắn **chỉ đọc**;
  - `.env.example` đầy đủ.
- Flyway tự chạy khi khởi động.

**Cách kiểm thử:**
```bash
docker compose -f docker/docker-compose.yml up -d --build
docker compose -f docker/docker-compose.yml ps     # flowtracer-app: healthy
```
> ✅ Kết quả mong đợi: mở `http://127.0.0.1:8600`, import demo, quét, xem luồng, so sánh cũ – mới; máy khác trong mạng không truy cập được cổng 8600.

---

### Task 11.2 — CI, tài liệu người dùng, nghiệm thu

**Mục tiêu:** Sẵn sàng bàn giao.

Tôi sẽ làm:
- `.github/workflows/ci.yml`: lint + test Engine / sidecar / web, golden test, `check-forbidden-terms --all`, build image.
- `README.md`: cài đặt, cấu hình AI, hướng dẫn sử dụng; ảnh chụp lấy từ mini fixture, không lấy từ demo.
- Chạy checklist Definition of Done trên máy sạch (Windows Docker Desktop + Linux).
- Người dùng gắn tag `v1.0.0` (Claude Code không tự commit hay gắn tag).

**Cách kiểm thử:**

| Tiêu chí DoD | Cách xác nhận |
| :--- | :--- |
| 1. `docker compose up` trên máy sạch, import và quét được | Làm lại Task 11.1 trên máy / VM khác |
| 2. Cây BE / FE đầy đủ, đạt NFR-07 | Số liệu Giai đoạn 8 |
| 3. Mọi nút có gọi API ra diagram + 2 tab đầy đủ | Kiểm tra ngẫu nhiên 10 nút của demo |
| 4. NFR-01 → NFR-09 có số liệu | `docs/evaluation.md` |
| 5. AI tắt vẫn chạy | `LLM_PROVIDER=none`, lặp lại mục 3 |
| 6. CI xanh | Trang Actions của repo |

---

## Tổng quan lộ trình

```
GĐ 0 (xong)
  → GĐ 1  Workspace & Import            [BE + FE]
  → GĐ 2  Quét & cây API Backend         [BE + FE]
  → GĐ 3  Luồng Backend & diagram        [BE + FE]
  → GĐ 4  Frontend Angular & Ctrl+K      [BE + FE]
  → GĐ 5  Liên kết FE ↔ BE, End-to-End   [BE + FE]
  → GĐ 6  Diễn giải & Kịch bản (không AI) [BE + FE]
  → GĐ 7  Cổng AI & nội dung AI          [BE + FE]
  → GĐ 8  Hoàn thiện & đo lường MVP
  → GĐ 9  Mở rộng: phân tích PHP cũ      [BE + FE]
  → GĐ 10 Mở rộng: so sánh cũ – mới      [BE + FE]
  → GĐ 11 Đóng gói & phát hành
```

| Giai đoạn | Task | Ưu tiên | Kiểm chứng trên giao diện |
| :--- | :--- | :--- | :--- |
| 1 — Workspace & Import | 1.1, 1.2 | 🔴 | Import dự án demo |
| 2 — Quét & cây Backend | 2.1, 2.2, 2.3 | 🔴 | Quét, cây API khớp Swagger |
| 3 — Luồng Backend & diagram | 3.1, 3.2, 3.3 | 🟠 | Diagram của một API |
| 4 — Frontend & Ctrl+K | 4.1, 4.2 | 🟠 | Cây FE, luồng chỉ giao diện |
| 5 — Liên kết & End-to-End | 5.1, 5.2 | 🟠 | Luồng từ nút tới bảng, chốt tay |
| 6 — Diễn giải & Kịch bản | 6.1, 6.2 | 🟠 | Cột phải khi AI tắt |
| 7 — Cổng AI | 7.1, 7.2 | 🟠 | Nội dung AI, "Nhờ AI phân tích" |
| 8 — Hoàn thiện & đo lường | 8.1, 8.2 | 🟢 | Đạt NFR |
| 9 — PHP hệ thống cũ | 9.1, 9.2 | ⚪ | Tab Hệ thống cũ, luồng PHP |
| 10 — So sánh cũ – mới | 10.1, 10.2 | ⚪ | Split view, khác biệt, checklist |
| 11 — Đóng gói & phát hành | 11.1, 11.2 | 🟢 | `docker compose up` trên máy sạch |
