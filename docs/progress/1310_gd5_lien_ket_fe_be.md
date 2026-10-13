# Giai đoạn 5 — Liên kết Frontend ↔ Backend & luồng End-to-End

> Ngày 13/10. **Task 5.1** (Linker, luật liên kết, BE → BE, bước LINK, Tracer FE → BE), **Task 5.2** phần BE (E1, E3–E5, E6–E7, bộ lọc nhiễu trong Tracer) rồi phần FE sau khi người dùng duyệt BE (mục 6).

---

## 1. Task 5.1 — Linker và luật liên kết

| Việc | Cách làm |
| :--- | :--- |
| `UrlPattern` (core) | Chuẩn hóa đường dẫn: bỏ host / query / fragment, gộp `/`; đoạn literal, biến (`{id}`, `{id:\d+}`, `:id`, `*`, `{?}`), đoạn trộn (`item-{id}` → so bằng regex) |
| `LinkRuleEngine` (core) | Sinh **phương án** đường dẫn thay vì áp nối tiếp mọi luật: gốc; sau một luật (`PREFIX` thay tiền tố trọn đoạn — dùng cả để thay phần không tính được như `{apiBase}`; `REWRITE` = regex như `pathRewrite` của proxy); luật thủ công rồi luật tự phát hiện. Phía API: thêm phương án `CONTEXT_PATH` thủ công cho API chưa có context-path. Kiểm tra cú pháp regex của luật |
| `EndpointMatcher` + `CandidateScorer` (core) | Phương thức phải trùng (`ANY` trừ 4 điểm); điểm từng cặp đoạn: literal = literal 10, biến ↔ biến 8, trộn 7, literal ↔ biến 6, biến ↔ literal 3; −1 mỗi luật. URL chưa tính hết mà bắt đầu bằng biến được so phần cuối (−6). `/items/export-excel` thắng `/items/{id}` (20 so với 16). Mức khớp Cao ≥ 0,85 / Trung bình ≥ 0,6 / Thấp — không trả con số |
| `Linker` (core) | `CERTAIN` chỉ khi URL là chuỗi hằng, không cần luật, khớp từng đoạn literal; ứng viên đầu cách ứng viên thứ hai < 2 điểm → `UNRESOLVED` + tối đa 5 ứng viên; không khớp API nào → URL tuyệt đối thì `EXTERNAL` theo host, ngược lại `UNRESOLVED`; Workspace không có Backend → `EXTERNAL` "API bên ngoài". Lý do ghi rõ biểu thức URL, luật đã áp (kèm nơi phát hiện), khớp biến / phần cuối |
| BE → BE (adapter Spring) | Pha 1 ghi thêm đối số URL / `HttpMethod` của lời gọi (`CallSiteFact.http`); `MappingExtractor` đọc cả interface `@FeignClient`. Pha 2 (`FlowResolver`) dựng `HTTP_CALL` (`origin = BE`, key `java:http:<FQN>#<hàm>(<kiểu>):<n>`) + cạnh `SENDS` cho `RestTemplate`/`RestOperations`, `WebClient`/`RestClient` (`.get().uri(…)`, `.method(HttpMethod.X).uri(…)`), Feign (`path` + mapping của interface + mapping của hàm; host = `url` hoặc `name`). Hằng số trong URL được giải bằng `ConstantTable`, không giải được thì giữ `{tên}` và ghi `urlIssue`. `SpringLinkRules` ghi luật `CONTEXT_PATH` tự phát hiện (chỉ để xem). `JavaFileExtractor.VERSION` = 5 → quét lại sẽ phân tích lại toàn bộ |
| Bước LINK (`scan`) | `ScanStage.LINK` "Nối Frontend – Backend" giữa PERSIST và FINALIZE. `LinkingService.linkAfterScan`: ghi luật tự phát hiện của mọi Source vào `workspace.settings.detectedLinkRules` rồi nối lại; `GraphWriteService.replaceLinks` xóa mọi `HTTP_REQUEST` + `EXTERNAL` cũ, ghi mới trong một transaction. LINK chạy **cả khi quét lại không có thay đổi** (Workspace quét từ GĐ4 có liên kết ngay khi quét lại). `ResolveStep` truyền luật thủ công (vd `QUERY_SINK`) vào bộ phân tích |
| Tracer FE → BE (core) | `TraceRun` nhận điểm xuất phát `UI_ELEMENT` gọi API: User → Frontend (mọi hàm FE gộp cột Frontend, kể cả lớp FE tên `…Service`), mỗi `SENDS` theo `HTTP_REQUEST` → bước `METHOD path` sang Controller rồi dò tiếp như luồng từ API, chiều trả về `200 <kiểu>`; tới `EXTERNAL` thì cột riêng (vai trò `EXTERNAL`); chưa nối / đã loại bỏ → cột "API chưa xác định", không chọn hộ; bỏ lời gọi `afterClosed`; bước cuối là phản hồi giao diện. `UiOnlyTracer.callsServer` quyết định luồng chỉ giao diện hay End-to-End. Không còn `422` cho phần tử gọi API |
| Đồ thị con dò vết | Không đi vào bên trong màn hình / hộp thoại / ứng dụng được mở (chỉ là hiệu ứng); thêm 12 cạnh độ sâu khi xuất phát từ phần tử; nạp thêm phần đi tới được từ đích đã ghim |

## 2. Task 5.2 (BE) — Chốt tay, luật, bộ lọc nhiễu

| API | Nội dung |
| :--- | :--- |
| **E1** `GET /edges/{edgeId}` | `EdgeController` → `LinkingService.edgeDetail`: loại, mức tin cậy tự động, lý do, `from` / `to`, ứng viên kèm `level` + `levelLabel` (Cao / Trung bình / Thấp), vị trí điểm gọi, quyết định chốt tay đang áp, `overridable`. `404 EDGE_NOT_FOUND` |
| **E3–E5** `/link-overrides` | `LinkOverrideService`: liệt kê (kèm tên hai đầu, cờ `stale` khi thành phần không còn trong kết quả quét), tạo (`PIN` thay mọi quyết định cũ cùng thành phần gọi + loại; `REJECT` thay quyết định cũ cùng đích; đích phải có thật và đúng loại: `HTTP_REQUEST` → API / hệ thống ngoài, `CALLS` → hàm), xóa (`404 LINK_OVERRIDE_NOT_FOUND`) |
| Áp chốt tay | `TracingService` nạp quyết định → `TraceRequest.overrides` (đã nằm trong `traceId`); `OverrideApplier` áp trước khi dò; ghim lời gọi HTTP chọn được API bất kỳ (lời gọi hàm chỉ trong ứng viên). Quyết định lưu theo node key nên giữ qua mọi lần quét |
| **E6–E7** `/link-rules` | `LinkRuleService`: luật tự phát hiện (chỉ xem) + luật thủ công; lưu xong nối lại ngay, message "Đã nối lại N liên kết" (N = số lời gọi đổi đích / mức tin cậy). Regex sai → `400`. Đang quét → chỉ lưu, bước LINK của lần quét đó dùng luật mới. Khóa cache dò vết có thêm luật hiệu lực nên diagram đổi ngay |
| Bộ lọc nhiễu (I4–I5) | Đã có từ GĐ3 và đã áp trong Tracer (`TraceRequest.noiseFilters`, nằm trong `traceId`); GĐ5 không đổi gì |

Tài liệu đã cập nhật: `overview.md` (§2.2.1 `HTTP_CALL` phía BE, `EXTERNAL`; §2.2.4 luồng End-to-End; **§2.2.5 Thuật toán Linker** mới), `API_list.md` (D1, E1, E3–E7), `feature_list.md` (luật liên kết, gọi giữa các Backend), `project_structure.md` (LINK, `CandidateScorer` nằm ở `core/linker`, DTO của `linking`), `database.md` (`settings.detectedLinkRules`), `roadmap.md`.

## 3. Kiểm thử

| Mức | Kết quả |
| :--- | :--- |
| `flowtracer-core` | 70 test (thêm `LinkerTest` 16: CERTAIN chỉ với chuỗi hằng; luật proxy + hằng số → INFERRED kèm lý do; phương thức phải trùng; đoạn cố định thắng đoạn biến; ứng viên ngang điểm → UNRESOLVED; so phần cuối khi thiếu base URL; luật PREFIX thủ công thay `{apiBase}`; CONTEXT_PATH thủ công; URL tuyệt đối → EXTERNAL; chỉ có FE → "API bên ngoài"; ANY; tất định. `EndToEndTracerTest` 5: User → … → Bảng, không đoán khi chưa xác định, ghim / loại bỏ, cột hệ thống ngoài) |
| `flowtracer-adapter-spring` | 55 test; golden `spring-flow` / `spring-mini` sinh lại có chủ đích (lời gọi HTTP phía BE, luật CONTEXT_PATH) |
| `flowtracer-app` unit | 34 test (ArchUnit 9 luật giữ nguyên). `TracerGoldenTest` thêm: golden **E2E** nút Lưu của hộp thoại (`fixtures/golden/e2e.trace.category-save.*`, ghép IR angular-mini + spring-flow + luật proxy), lời gọi FE không có API khớp, BE → BE và 2 hệ thống ngoài trong luồng xác nhận đơn hàng |
| IT (Testcontainers) | 23 IT. Mới `LinkingApiIT` (Workspace 2 nguồn, sidecar thật): nút Lưu → User → Frontend → Controller → Service → Repository → Bảng; E1 lý do "CATEGORY_BASE" + "^/gateway"; E6 luật proxy tự phát hiện; nút Xuất Excel chưa xác định → E4 ghim → diagram đổi ngay → **quét lại vẫn giữ** → E5 bỏ → về chưa xác định; E7 regex sai 400, luật hợp lệ "Đã nối lại 1 liên kết". `ScanPipelineIT` có thêm giai đoạn LINK; `AngularScanIT`: nút Xóa ở Workspace chỉ có FE → cột "API bên ngoài" |
| `check-forbidden-terms --all` | Sạch |

Fixture `spring-flow` (tự viết, miền Shop) thêm `CatalogGateway` (WebClient → API cùng Workspace), `InventoryClient` (`@FeignClient`, service ngoài), `PaymentGateway` (RestTemplate, URL tuyệt đối từ hằng số).

**Đo trên dự án demo** (Workspace tạm BE + FE, đã xóa sau khi đo; chỉ số liệu tổng hợp):

| Chỉ số | Giá trị |
| :--- | :--- |
| Lời gọi HTTP | 213: 200 suy luận (94%), 13 chưa xác định, 0 chắc chắn (demo ghép URL từ hằng số nên không có lời gọi nào là chuỗi hằng thuần) |
| Lý do chưa xác định (9 cạnh gặp khi dò) | 7 không có API nào trong mã Backend khớp; 2 có hai API khớp ngang nhau (chờ chốt tay) |
| Luật tự phát hiện | 1 luật `REWRITE` từ proxy |
| Dò vết mọi phần tử gọi API | 267 / 267 dò được; 244 tới Controller, 237 tới một bảng dữ liệu; trung vị 38 bước, dài nhất 163 (có cảnh báo rút gọn); trung bình 47 ms / lần |
| Bước LINK | 3,8 giây trong lần quét 31 giây |

## 4. Việc còn dở

- ~~FE của Task 5.2~~ — đã làm, xem mục 6.
- Trọng số `CandidateScorer` mới đặt theo quy tắc của Nhóm 5; hiệu chỉnh bằng `evaluate-accuracy.mjs` ở giai đoạn đo lường.

## 5. Vấn đề gặp phải

- Key `HTTP_CALL` phía BE ban đầu lặp tiền tố (`java:http:java:method:…`) → sửa `NodeKeys.javaHttpCall` bỏ tiền tố `java:method:`.
- Sau mỗi lần quét / nối lại, edge `HTTP_REQUEST` được ghi lại nên `edgeRef` đổi: giao diện phải lấy `edgeRef` từ kết quả dò vết mới nhất (đã ghi trong API_list E1).
- Thứ tự các luật proxy: áp nối tiếp `^/gateway` rồi `^/api` sẽ cắt mất đường dẫn thật → chuyển sang thử từng phương án.

---

## 6. Task 5.2 (FE) — popover liên kết, Cài đặt

Bám bản vẽ `docs/UI/UI_offline.html` (popover `openEdgePop`, Cài đặt `SET_TABS` / `SET_HELP`).

| Việc | Cách làm |
| :--- | :--- |
| Kiểu API | Sinh lại `generated/schema.d.ts` từ Engine mới; `LinkingApi` (E1, E3–E5, E6–E7; E7 trả kèm thông điệp "Đã nối lại N liên kết"). BE: ẩn getter kiểm tra `isTargetPresentForPin` của `CreateLinkOverrideRequest` khỏi OpenAPI (`@JsonIgnore` + `@Schema(hidden)`) |
| Popover liên kết (`edge-popover.ts`) | Mở khi bấm mũi tên đi chưa chắc chắn hoặc đã chốt tay có `edgeRef`; tooltip thêm dòng "Bấm để xem ứng viên"; nút "Xem" của thẻ "n bước chưa xác định" cuộn tới bước rồi mở popover. Nội dung như bản vẽ: nhãn mức tin cậy (Suy luận / Chưa xác định / Chốt tay), "Bước n: A → B", lý do (hoặc "Bạn đã chốt tay … Ghi chú: …"), vị trí điểm gọi, "ỨNG VIÊN · MỨC KHỚP DO PHÂN TÍCH TĨNH TÍNH" với mức Cao / Trung bình / Thấp, ô ghi chú, nút Bỏ chốt tay / Nhờ AI phân tích (mờ) / Chốt tay. Chốt / bỏ chốt → toast rồi dò vết lại. Popover luôn nằm trọn trong canvas (đẩy vào trong, giới hạn chiều cao); không bị tooltip che |
| Cài đặt | 5 tab: Nguồn mã · **Luật liên kết** · **Bộ lọc nhiễu** · **Liên kết chốt tay** · Lịch sử quét, mỗi tab một khối hướng dẫn (câu chữ của bản vẽ). Luật liên kết: bảng Nguồn (Tự phát hiện / Thủ công) · Loại · Mẫu · Thay bằng; luật tự phát hiện "chỉ xem"; hộp thoại thêm / sửa "Lưu & nối lại", regex sai báo ngay ở trường Mẫu. Bộ lọc nhiễu: chip mặc định (khóa) và chip do bạn thêm (thêm / bỏ; chặn trùng với mặc định). Liên kết chốt tay: bảng Từ · Tới · Loại · Ghi chú · Thời điểm · Bỏ chốt, trạng thái rỗng như bản vẽ, ghi rõ khi thành phần không còn trong kết quả quét |
| Toast tiến độ quét | Thêm giai đoạn "Nối Frontend – Backend" (7 giai đoạn) |
| Explorer | Bỏ thông báo cũ "cần nối Frontend – Backend (Giai đoạn 5)"; sau khi chốt / bỏ chốt thì dò vết lại |

**Khác bản vẽ (cần người dùng biết):**

- Thêm ô **"Chọn API khác"** trong popover (tìm API qua C3) cho lời gọi HTTP: bản vẽ chỉ chọn trong ứng viên, nhưng trên demo 7/9 lời gọi chưa xác định **không có ứng viên nào** nên nếu không có ô này thì không chốt tay được. Lời gọi hàm vẫn chỉ chọn trong ứng viên.
- Popover không có nút "Loại bỏ" (bản vẽ cũng không có); API E4 vẫn nhận `REJECT`.
- Bảng luật: cột "Mẫu" (bản vẽ ghi "Mẫu (trong mã Frontend)") vì có cả luật context-path của Backend; loại luật theo đúng 4 loại của Engine: "Tiền tố URL / thay biến", "Proxy (biểu thức chính quy)", "Context-path Backend", "Điểm thực thi truy vấn" (bản vẽ: Tiền tố URL / Proxy / Thay biến); có thêm trường Mô tả.
- Cột "Từ" của tab Liên kết chốt tay là lời gọi HTTP (vd `GET /gateway/api/v1/categories/export`), không phải đường dẫn "Màn hình › Nút" như dữ liệu mẫu của bản vẽ.
- Chưa có tab "Đối chiếu OpenAPI" (Nên có — C6, chưa làm).

**Kiểm thử FE:** web 74 test (thêm `edge-popover-model.spec` 5, `link-settings-model.spec` 4, `linking.api.spec` 3; cập nhật `scan-toast.spec` cho 7 giai đoạn), lint sạch, build qua. Trình duyệt thật (Chrome headless, Workspace tạm angular-mini + spring-flow, đã xóa sau khi chạy): **27/27 kiểm tra** — nút Lưu ra luồng User → … → Bảng category; di chuột / bấm mũi tên Suy luận → popover có lý do hằng số + luật proxy, nút "Nhờ AI phân tích" mờ, popover nằm trọn trong canvas; Xuất Excel chưa xác định → "Xem" mở popover → tìm và chọn API khác → Chốt tay → diagram dò lại tới Controller; quét lại vẫn giữ chốt tay; Cài đặt: 5 tab, luật proxy tự phát hiện, regex sai báo lỗi, lưu luật "Đã nối lại N liên kết", thêm / bỏ bộ lọc nhiễu, bỏ chốt về trạng thái rỗng. Engine: `ArchitectureTest` + unit app 34 test, `LinkingApiIT` qua sau khi đổi annotation.

---

## 7. Điều chỉnh và tối ưu hóa sau phản hồi thực tế (13/10)

Sau khi kiểm thử trực tiếp trên dự án demo, người dùng phát hiện hai điểm cần tinh chỉnh để diagram rõ nghĩa và sát nghiệp vụ hơn:

### 7.1. Khử trùng lặp bước khi quét thành phần danh sách & bảng (Deduplication)

- **Hiện tượng:**
  - Thành phần bảng (`Bảng onSelectionChange`) và ô tìm kiếm (`Ô Tên loại sách` trong màn hình Loại sách) bị hiển thị lặp đúp các bước thực thi giống hệt nhau (22 bước thay vì 12 bước, 2 khối lặp giống nhau).
- **Nguyên nhân:**
  - Phía sidecar Angular (`screen-tree.builder.ts`):
    1. Một phần tử giao diện liên kết nhiều sự kiện cùng trỏ tới 1 hàm xử lý (ví dụ: `shared-input` có cả `(enter)` và `(suffixClick)` cùng gọi `onSearch()`).
    2. Thành phần bảng/phân trang vừa phát sinh sự kiện `pageChange`, vừa phát sinh `pageSizeChange`, đều kích hoạt nạp lại dữ liệu danh sách.
  - Phía Engine (`TraceRun.java`): Hàm `runFromElement()` duyệt mọi trigger của phần tử mà chưa lọc trùng đích gọi (`target`). Ngoài ra, nếu có hai lời gọi HTTP liên tiếp tới cùng một endpoint, luồng bị phân giải lặp lại.
- **Cách xử lý:**
  - `analyzer-node/src/adapters/angular/resolve/screen-tree.builder.ts`:
    + Thêm tập `wiredTargets = new Set<string>()` trong hàm `wire()`: nếu một phần tử có nhiều event cùng trỏ tới cùng một handler method thì chỉ giữ lại 1 trigger duy nhất.
    + Lọc bỏ `pageSizeChange` nếu phần tử đã có sự kiện `pageChange`.
  - `engine/flowtracer-core/src/main/java/com/ctv/flowtracer/core/tracer/TraceRun.java`:
    + Thêm `seenTargets = new HashSet<String>()` trong `runFromElement()` để ngăn chặn việc dò lại phương thức xử lý đã được kích hoạt.
    + Trong `handleHttp()`, gom và gộp các lời gọi lặp tới cùng endpoint thay vì mở rộng lặp lại.
- **Kết quả:**
  - Diagram của `shared-input.enter.onSearch` và `shared-table.selectionChange.onSelectionChange` giảm từ 22 bước $\rightarrow$ **12 bước chuẩn xác**, không còn bước lặp.

### 7.2. Lược bỏ khung bao `opt [try]` trên Sequence Diagram

- **Hiện tượng:**
  - Các bước gọi hàm/service thường bị bọc trong khung `opt [try]`, khiến diagram bị lồng nhiều lớp hộp không cần thiết.
- **Nguyên nhân:**
  - Bộ phân tích cú pháp AST nhận diện khối `try...catch` và bọc trong block `OPT "try"`. Tuy nhiên, `try...catch` chủ yếu là cơ chế phòng vệ lỗi runtime trong code (defensive exception handling), không đại diện cho rẽ nhánh logic nghiệp vụ (business branching như `if...else`).
- **Cách xử lý:**
  - Sửa `TraceRun.java`: Đổi `case TRY -> emitSimple(..., TraceBlock.Type.OPT, "try")` thành `case TRY -> emitPlain(events, from)`. Luồng bên trong khối `try` được làm phẳng và thực thi tuần tự tự nhiên mà không sinh khung `opt [try]`.
- **Kết quả:**
  - Diagram thoáng đãng, dễ đọc; chỉ giữ lại các khung logic nghiệp vụ thật sự (`alt`, `opt` có điều kiện if, `loop`).

### 7.3. Kết quả kiểm thử lại toàn diện

- **Unit/Golden tests:**
  - `analyzer-node`: 6 test suites, 45 tests passed.
  - `flowtracer-core`: 70 tests passed.
  - `flowtracer-app`: 34 unit tests + 23 ITs passed (tổng cộng 159 tests trong engine).
- **Kiểm chứng trực quan (Live Browser & UI):**
  - Đã quét lại workspace demo `46bca025-ba49-4ad9-9baa-c8ae829819bd`.
  - Kiểm tra các màn hình (Loại sách, Tác giả...): sequence diagram hiển thị chuẩn 12 bước trên 6 lifelines (`User` $\rightarrow$ `Frontend` $\rightarrow$ `BookTypeController` $\rightarrow$ `BookTypeServiceImpl` $\rightarrow$ `BookTypeRepository` $\rightarrow$ `Database`), không còn khối `try`, không còn lặp bước.

