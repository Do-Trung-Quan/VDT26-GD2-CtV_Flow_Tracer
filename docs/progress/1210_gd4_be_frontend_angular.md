# Giai đoạn 4 — Frontend Angular & tìm kiếm nhanh

> Ngày làm: 12/10/2026. Phạm vi: **Task 4.1** (sidecar Angular + `SidecarStackAdapter`) và **phần BE của Task 4.2** (API C2 cây Frontend, C3 tìm kiếm, D1 luồng chỉ giao diện). Phần giao diện (**FE của Task 4.2**: tab Frontend, Ctrl+K, canvas 2 cột) làm sau khi người dùng duyệt BE — xem mục 4.
> Trạng thái: ✅ BE + FE xong, chờ người dùng review.

---

## 1. Việc đã làm

### 1.1. Task 4.1 — Sidecar Angular (`analyzer-node`)

| Thành phần | Làm gì |
| :--- | :--- |
| `extract/` (pha 1, từng file, cache theo mã băm) | `.ts`: class (decorator, selector, `templateUrl` / template inline, field, `inject()` / `@Inject`, hàm), lời gọi hàm theo **thứ tự thực thi** kèm điều kiện bao quanh, lời gọi `HttpClient` (phương thức, URL dạng biểu thức, kiểu body), mở hộp thoại (`dialog.open(Class)`), chuyển trang (`router.navigate`), route (`component`, `loadComponent`, `loadChildren`, `children`, `loadRemoteModule`), provider (`useExisting` / `useClass` / `useFactory`), `InjectionToken`, hằng số chuỗi và object (`environment.apiBase`, enum), từ điển `*.vi.ts`. Template (`parseTemplate` của `@angular/compiler`): phần tử có sự kiện, form, tab, ô nhập, `routerLink`, nhãn (chữ tĩnh, `t('khóa')`, `'khóa' \| translate`, `<label>` bao hoặc đứng ngay trước, `[placeholder]="t(..) \|\| '..'"`), tiêu đề. JSON: `angular.json`, tsconfig, proxy, từ điển `vi.json`. `federation.config.js` chỉ đọc văn bản, **không chạy** |
| `workspace/` | `angular.json` → ứng dụng / thư viện; `compilerOptions.paths` (kể cả `extends`); khai báo `exposes` của microfrontend |
| `resolve/` (pha 2, cả Source) | `SymbolTable` (import tương đối, alias, file barrel), `RouteTreeBuilder` (màn hình + đường dẫn đầy đủ qua `loadChildren` / `children` / NgModule `forChild` / microfrontend), `DiResolver` (token + provider → `CERTAIN`; interface chỉ có 1 lớp hiện thực → `INFERRED`; nhiều đích → `UNRESOLVED` kèm ứng viên), `CallResolver`, `UrlEvaluator` (hằng số ở file khác, field của class, `environment.*`, tham số → `{id}`), `LabelResolver` (từ điển gần file nhất), `GraphBuilder` (cây giao diện + FE_METHOD + HTTP_CALL), luật `REWRITE` từ `pathRewrite` của proxy |
| Hợp đồng | Không đổi schema. Thêm hàm sinh key `ngApp`, `ngScreen`, `ngDialog`, `ngForm`, `ngTab`, `ngMethod` ở cả `NodeKeys.java` và `node-keys.ts` (key mới, không đổi key cũ). Cache sidecar có phiên bản (`{ v, facts }`) và gán lại đường dẫn khi hai file trùng nội dung |

**IR mới của Frontend:** `UI_APP`, `UI_SCREEN`, `UI_TAB`, `UI_DIALOG`, `UI_FORM`, `UI_ELEMENT`, `FE_METHOD`, `HTTP_CALL`; cạnh `CONTAINS` (có thứ tự trong nút cha), `TRIGGERS`, `CALLS`, `OPENS`, `NAVIGATES`, `SENDS` (chi tiết: `overview.md` §2.2.1–2.2.2).

**Quyết định cần báo:**

- **Không dùng TypeScript type checker** (roadmap có ghi): kết quả phân tích không được phụ thuộc `node_modules`: thư mục này có thể vắng (nguồn mới clone, gắn read-only vào Docker) hoặc khác phiên bản giữa các máy, làm cùng mã nguồn ra IR khác (vi phạm tính tất định); nó cũng bị loại khi quét và rất lớn. Dự án demo có `node_modules` nhưng bộ phân tích không đọc. Type checker cũng chỉ cho kiểu (`.d.ts`) của thư viện, không cho thân hàm nên luồng đi vào thư viện vẫn đứt; phần mã của chính dự án đã có `SymbolTable`. Backend cũng vậy với `ClassIndex` thay `SymbolSolver`. Đã cập nhật `CLAUDE.md` và `roadmap.md`.
- **Lời gọi trong callback đóng hộp thoại** (`ref.afterClosed().subscribe(() => this.load())`) mang `guard.branch = afterClosed` và **không tính** vào luồng của lần bấm: nếu không, nút "Thêm mới" (chỉ mở hộp thoại) bị coi là gọi API chỉ vì hộp thoại tải lại danh sách khi đóng.
- Thành phần con (thẻ trùng `selector` của một component trong dự án) được **gộp phẳng** vào màn hình chứa nó; phần tử của component dùng chung hiện dưới từng màn hình dùng nó (cùng một node, nhiều nút cha).
- `UI_APP` không có `port` / `isShell` như bảng cũ trong `overview.md`: mã nguồn không có căn cứ chắc chắn nên không ghi (đã sửa bảng).

### 1.2. Task 4.1 — Engine

| Thành phần | Làm gì |
| :--- | :--- |
| `SidecarStackAdapter` | Hoàn thiện: `analyzes` (mã TypeScript, template, file cấu hình, từ điển `vi.json`; bỏ `.spec.ts`, `.d.ts`), `extract` chỉ ghi nhận file (`SidecarFileFacts`), `resolve` gọi `analyze` **một lần cho cả Source**, kiểm tra kết quả bằng `contracts/ir-fragment.schema.json` rồi mới đổi thành IR |
| `SidecarRunner` | Thêm thư mục Source tuyệt đối (chặn thoát khỏi `SOURCE_ROOT`), `cacheDir` nằm trong cây cache của Workspace (`<data-dir>/facts/<workspaceId>/sidecar-angular-v<ver>/<sourceId>`, xóa cùng Workspace), nạp schema từ classpath |
| `AdapterRegistry` | Khai báo file được phân tích và `ANGULAR_ANALYSIS_VERSION` (tăng khi đổi cách trích / luật ghép → "Quét lại" phân tích lại thay vì giữ IR cũ) |
| `ResolveStep` | Truyền `workspaceId` và `rootPath` của Source cho adapter chạy trong sidecar |
| `SchemaValidator` (core) | Hiện thực thật bằng `json-schema-validator` (draft 2020-12), có test; dùng lại cho dữ liệu mẫu ở GĐ6 |
| `flowtracer-app/pom.xml`, `docker/Dockerfile` | Đóng gói `contracts/*.schema.json` vào classpath từ **chính thư mục `contracts/`** (không sao chép tay); Dockerfile copy `contracts/` vào giai đoạn build Engine |

### 1.3. BE của Task 4.2

| API | Làm gì |
| :--- | :--- |
| **C2** `GET /api/workspaces/:id/trees/frontend` | Cây `Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử` từ cạnh `CONTAINS`; mỗi phần tử có `elementType`, nhãn tiếng Việt (nếu mã nguồn có), `callsApi` (suy ra bằng truy vấn đệ quy: sự kiện → hàm xử lý → … → lời gọi HTTP, không tính mở hộp thoại và `afterClosed`); vòng chứa nhau bị cắt |
| **C3** `GET /api/workspaces/:id/search?q&kinds&limit` | Nhóm kết quả: API Backend, Màn hình, Phần tử giao diện, Bảng dữ liệu, Hàm / lớp; mỗi từ khóa khớp tên / nhãn / key (chuỗi con) hoặc tiền tố trong `search_text` (tsvector); xếp khớp đúng → bắt đầu bằng → chứa; giới hạn mỗi nhóm bằng `ROW_NUMBER` kèm tổng số khớp của nhóm |
| **D1** (mở rộng) | `UiOnlyTracer`: xuất phát từ `UI_ELEMENT` cho 2 cột User ↔ Frontend, 2 bước (bấm → hộp thoại / màn hình / "giao diện đã cập nhật"), `uiOnly = true`; phần tử có gọi API trả `422 TRACE_START_UNSUPPORTED` (chờ GĐ5). `Trace` thêm `uiOnly` (không xuất hiện trong JSON khi `false` nên golden BE giữ nguyên) |

Thay đổi liên quan: `IrEdgeRepository.reachableNodeIds` không đi theo `CONTAINS`; `IrNodeRepository` thêm `findByKinds`, `search`; `TraceResponse` thêm `uiOnly`.

### 1.4. Tài liệu đã cập nhật

`.context/overview.md` (§2.2.1 thuộc tính node FE, §2.2.2 quy ước cạnh FE và `afterClosed`, §2.2.4 luồng chỉ giao diện + `uiOnly`), `API_list.md` (C2, C3, D1), `project_structure.md`, `roadmap.md`, `CLAUDE.md` (§1.2, §3.7), `contracts/sidecar-protocol.md` (cache, file cấu hình), `fixtures/README.md`, `fixtures/angular-mini/README.md`.

### 1.5. Bổ sung sau phản hồi (cùng ngày)

| Việc | Làm gì |
| :--- | :--- |
| **Cạnh vòng đời** | `UI_SCREEN` / `UI_DIALOG` / `UI_APP` → `constructor` (gồm khởi tạo field `x = this.svc.load()`), `ngOnInit`, `ngAfterViewInit` bằng `TRIGGERS`, chỉ khi hàm có tác dụng. Đóng khoảng trống "lời gọi HTTP chạy khi mở màn hình" và là điều kiện của "Được gọi từ: Màn hình …" ở GĐ5 |
| **Suy kiểu biến cục bộ và nối chuỗi** | `CallFact` thêm `rootType` và `via`; `CallResolver.resultOf` lấy kiểu trả về đã khai báo (`Promise<T>` + `await` → `T`); không có kiểu khai báo thì bỏ qua. Cạnh `INFERRED` kèm lý do |
| **Tên dự phòng cho phần tử không có nhãn** | Node thêm `control`; C2 thêm `fallbackName` (tên control → tên hàm xử lý → khóa dịch → thẻ); luồng chỉ giao diện gọi là "Bấm nút onSave". Không dùng AI |
| Phiên bản | `FACTS_VERSION` = 2, `ANGULAR_ANALYSIS_VERSION` = 2 (Quét lại tự phân tích lại) |

Đo lại trên demo sau bổ sung: xem mục 2.

---

## 2. Kiểm thử

| Lệnh | Kết quả |
| :--- | :--- |
| `npm --prefix analyzer-node run test` | ✅ **41 test** (trích dữ kiện 16 · ghép IR trên fixture 15 · golden 2 · detect / proxy / hợp đồng 8); IR của fixture hợp lệ với `ir-fragment.schema.json`; chạy 2 lần cho cùng kết quả |
| `npm --prefix analyzer-node run lint` · `check-forbidden-terms --all` | ✅ sạch (587 file) |
| `engine/mvnw clean verify` | ✅ BUILD SUCCESS: **138 test đơn vị** (ir 4 · core 49 · adapter-spring 55 · app 30, gồm `ArchitectureTest` 9 luật giữ nguyên) và **22 test tích hợp** (`AngularScanIT` 1 · `ScanPipelineIT` 8 · `SchemaMappingIT` 1 · `WorkspaceApiIT` 10 · `TracingApiIT` 2) |

`AngularScanIT` quét `fixtures/angular-mini` qua **sidecar thật + PostgreSQL thật** rồi kiểm tra C2 (cây, thứ tự, `callsApi`), C3 (nhóm, lọc `kinds`, lỗi 400 / 404) và D1 (luồng chỉ giao diện, nút gọi API bị từ chối `422`).

### Đo trên dự án Frontend demo (cục bộ, chỉ số liệu tổng hợp)

| Chỉ tiêu | Kết quả |
| :--- | :--- |
| Quét lần đầu | 679 file Angular trong ~10–25 giây, 0 file lỗi; 3.134 node, 6.902 cạnh (trước bổ sung: 2.792 / 5.895) |
| Cây thành phần | 5 ứng dụng · 60 màn hình · 37 hộp thoại · 31 form · **1.034 phần tử** (3.001 lần xuất hiện vì component dùng chung hiện ở nhiều màn hình); dựng cây ~0,2 giây |
| Lời gọi HTTP | **202 / 236** vị trí gọi `HttpClient` trong mã (86%; trước bổ sung 164, 69%); 100% số đã dựng tính được URL (hằng số ở thư viện khác, `environment`, `this.field`). Phần tăng đến từ cạnh vòng đời (tải dữ liệu khi mở màn hình). Suy kiểu biến cục bộ / nối chuỗi không làm tăng số phần tử gọi API trên demo vì mã demo hầu hết gọi qua `this.x` |
| Phần tử gọi API / chỉ giao diện | 298 / 736 phần tử |
| Dò vết luồng chỉ giao diện | 736/736 thành công, 298/298 phần tử gọi API bị từ chối `422`; nhất quán 100% với cờ `callsApi`; trung vị 12 ms, p95 18 ms, tối đa 39 ms (NFR-04 ≤ 2 giây ✅) |
| Tìm kiếm | 19–30 ms mỗi lần |
| Nhãn tiếng Việt | 654/1.034 phần tử (63%) có nhãn đọc được; 380 phần tử còn lại (icon, nội dung động…) đều có `fallbackName` (tên control / hàm xử lý / khóa dịch / thẻ) nên **không phần tử nào trống tên** |

---

## 3. Việc còn dở / giới hạn đã biết

- **Phần tử gọi API chưa dò được** (298 phần tử trên demo): cần Linker nối `HTTP_CALL` ↔ endpoint (GĐ5).
- **Chưa có nhãn tiếng Việt** ở 37% phần tử demo: chỉ có tên kỹ thuật (`fallbackName`). Nhãn từ `{{ biến }}` hoặc `@Input` ngoài `label` / `placeholder` / `title` / `header` / `text` không đọc được. Muốn nhãn tiếng Việt cho phần này cần AI (mục đích mới, chưa được duyệt) hoặc để người dùng tự đặt.
- **34/236 vị trí gọi HTTP** vẫn chưa với tới: mã không dùng, hoặc đi qua cách gọi chưa hỗ trợ (kiểu suy ra không có khai báo, callback RxJS, hàm bọc).
- **Chưa có chế độ dự phòng dùng TypeScript type checker** cho dự án có `node_modules` (đã bàn: chỉ làm nếu độ phủ còn thiếu rõ).
- **Hàm bọc `HttpClient` dùng chung** (`api.get(path)` với `path` là tham số): URL ghi `{path}` và `urlResolved = false` — chưa lần theo đối số của nơi gọi.
- **Bỏ dấu khi tìm kiếm** chưa có ("them" không khớp "thêm") — theo quyết định của người dùng, chưa làm.
- **Tiến độ sidecar (stderr)** chỉ ghi log khi lỗi; thanh tiến độ quét chưa chia nhỏ theo file khi phân tích Angular.
- **Bản Engine đang chạy ở cổng 8600 là bản cũ**: cần khởi động lại, rồi bấm "Quét lại" ở Workspace có Frontend (đổi `analysisVersion` sẽ tự phân tích lại).

---

## 4. Task 4.2 — Giao diện (`web/`)

| Thành phần | Làm gì |
| :--- | :--- |
| Tab **Frontend** (cột trái) | `FrontendTree` — cây C2: icon theo loại (ứng dụng = lớp, màn hình, hộp thoại = cửa sổ, form = tệp, nút = con trỏ, ô nhập, bảng), nhãn theo bản vẽ ("Màn hình Danh mục", "Nút Thêm mới", "Ô Mã"), thẻ **API** (thao tác dẫn tới lời gọi HTTP) / **UI** (chỉ giao diện), lọc nhanh có tô sáng và đếm khớp. Phần tử không có nhãn trong mã nguồn hiện **tên kỹ thuật in nghiêng** ("Nút *onRefresh*", tooltip giải thích). Cùng một thành phần ở nhiều màn hình (component dùng chung, hộp thoại) hiện dưới từng màn hình, trạng thái mở theo từng dòng; chọn một thành phần chứa thì mở luôn |
| Cột phải | Loại + thẻ "Gọi máy chủ" / "Chỉ giao diện", tiêu đề theo nhãn, "Thuộc về" (đường từ ứng dụng), vị trí file:dòng, với thành phần chứa: số phần tử bên trong; với phần tử: tóm tắt luồng (số bước, cột) |
| Canvas | Phần tử không gọi máy chủ → diagram 2 cột User ↔ Frontend (hai bước, như bản vẽ) kèm thẻ **"Không gọi máy chủ"**. Phần tử gọi API → **thông báo** (không phải lỗi đỏ) "Phần tử này gọi API Backend… có từ Giai đoạn 5". Thành phần chứa → thông báo "chọn một nút hoặc ô nhập bên trong" |
| **Ctrl+K** (`SearchPalette`) | Ô tìm kiếm ở giữa header thành nút mở bảng lệnh (Ctrl/Cmd+K ở mọi nơi, kể cả khi đang gõ; chưa chọn Workspace thì nhắc). Gõ → C3 (có độ trễ 160 ms, bỏ kết quả cũ khi gõ tiếp), nhóm API / Màn hình / Phần tử / Bảng dữ liệu, ↑ ↓ (đi vòng) Enter Esc, hover chọn. API hiện "METHOD đường dẫn", mô tả và module ở dòng phụ. Chọn kết quả → `ExplorerNavigation` → màn hình chính đổi tab, mở các tổ tiên trên cây, chọn và cuộn tới, rồi dò vết luôn. Bảng dữ liệu chỉ báo thông tin (chưa có chỗ mở riêng) |
| Khác | Sinh lại `schema.d.ts` từ Engine (C2, C3, `uiOnly`); thêm 8 icon (lớp, cửa sổ, tệp, con trỏ, ô nhập, bảng, tia chớp, CSDL); `BackendTree` nhận yêu cầu "mở tới" để Ctrl+K mở được API |

### Kiểm thử giao diện

| Lệnh | Kết quả |
| :--- | :--- |
| `npm --prefix web run test` | ✅ **60 test** (thêm: nhãn / lọc / trải phẳng / mở tới / rút gọn theo API của cây Frontend 17, bảng lệnh 5) |
| `npm --prefix web run lint` · `npm --prefix web run build` | ✅ sạch · ✅ build được |
| Chạy thật trên Chrome headless (Workspace `angular-mini` + `spring-flow`, Engine thật + PostgreSQL thật) | ✅ **26 kiểm tra**: đếm cây, nhãn, mở thành phần chứa, luồng 2 cột đúng nhãn và thẻ "Không gọi máy chủ", nút gọi API ra thông báo không lỗi, tên kỹ thuật in nghiêng, lọc, Ctrl+K (mở / focus / nhóm / mũi tên / Enter chọn đúng nút / chọn API sang tab Backend và vẽ diagram / Esc / chưa chọn Workspace) |

### Chỗ khác bản vẽ `docs/UI/UI_offline.html` (cần người dùng biết)

1. **37% phần tử demo không có nhãn tiếng Việt** (mock luôn có): hiện tên kỹ thuật in nghiêng. Muốn nhãn tiếng Việt cần AI hoặc người dùng tự đặt.
2. **Một thành phần xuất hiện ở nhiều màn hình** (component dùng chung, hộp thoại mở từ nhiều nơi): mock mỗi thành phần một chỗ. Trên demo 1.034 phần tử khác nhau hiện thành 3.001 dòng.
3. **Nút gọi API chưa có luồng End-to-End** (mock có đủ): hiện thông báo chờ Giai đoạn 5; chưa có thẻ cảnh báo "bước chưa xác định" trên cây vì chưa có liên kết FE–BE.
4. **Thẻ "Không gọi máy chủ" trên canvas** là bổ sung theo roadmap/feature_list; mock không vẽ thẻ này cho luồng 2 cột.
5. **Bảng lệnh**: bỏ nhóm "Hàm / lớp"; chọn bảng dữ liệu chỉ hiện toast (mock còn chọn API đầu tiên của module). Tìm chưa bỏ dấu.
6. **Cột phải**: chưa có tab Diễn giải / Kịch bản, nút Xem code, mô tả thành phần chứa (GĐ6); thành phần chứa hiện số phần tử thay cho đoạn mô tả.

---

## 5. Rút gọn cây Frontend: mặc định chỉ phần tử gọi API

Phản hồi sau khi xem giao diện: cây ngập phần tử chỉ thao tác giao diện (nút đổi tab, ô nhập, liên kết…) — ít thông tin hơn phần tử gọi API. Đã duyệt phương án: **ẩn ở tầng hiển thị, không xóa khỏi IR** (luồng chỉ giao diện D1, luồng End-to-End GĐ5, "Được gọi từ", diễn giải GĐ6, nhóm chức năng GĐ7 vẫn dùng được).

| Việc | Làm gì |
| :--- | :--- |
| BE (C2) | `callsApi` mở rộng cho **màn hình / hộp thoại / ứng dụng**: true khi chính nó gọi HTTP lúc mở (cạnh vòng đời → … → `SENDS`; không tính phần tử bên trong, không tính `afterClosed`). Cùng truy vấn đệ quy với phần tử (`findUiKeysCallingApi`); tab / form vẫn null |
| FE | `pruneToApi` (hàm thuần): luôn giữ ứng dụng và **mọi màn hình** (đơn vị nghiệp vụ, tránh tưởng bộ phân tích sót màn hình); giữ phần tử gọi API; giữ hộp thoại / form / tab nếu tự gọi API khi mở hoặc còn thành phần giữ lại bên trong. Công tắc "Hiện cả phần tử chỉ thao tác giao diện (N)" ở đầu cây, mặc định **tắt**, nhớ theo trình duyệt. Gõ lọc → tìm trong **mọi** phần tử; Ctrl+K tới phần tử đang ẩn → tự bật công tắc. Thành phần chứa (ứng dụng, màn hình, hộp thoại, form, tab) **không có thẻ và không có số đếm**, đúng bản vẽ (chỉ nút / ô nhập / bảng có thẻ API / UI); cờ `callsApi` của màn hình / hộp thoại chỉ dùng để quyết định giữ hay ẩn |

Số liệu trên demo: **3.134 → 491 dòng (giảm 84%)** — phần tử 3.001 → 407 dòng (298 phần tử gọi API khác nhau; 736 chỉ giao diện bị ẩn), hộp thoại 37 → 31, form 31 → 7, ứng dụng/màn hình giữ nguyên 5/60 (nếu ẩn cả màn hình không có phần tử gọi API sẽ mất 18/60 màn hình nên không làm).

Kiểm thử: `ng test` 60 test (thêm 5 cho `pruneToApi` / `countByApi`); Engine `TreeBuilderTest` + `AngularScanIT` (cờ của màn hình: Danh mục true vì `ngOnInit`, Đơn hàng true vì khởi tạo field, Sản phẩm false); Chrome headless 36 kiểm tra (mặc định tắt công tắc, ẩn phần tử UI, màn hình giữ đủ, bật công tắc và lưu cờ, gõ lọc tìm cả phần tử ẩn, Ctrl+K tới phần tử ẩn tự bật công tắc).

Lệch so với mock: mock hiện cả nút UI trong cây; nay ẩn mặc định.

---

## 6. Sửa sau khi xem cây thật: cờ API sai, nút mở hộp thoại, hộp thoại không hiện

Người dùng chụp cây màn hình "Tác giả" của demo: "Thêm mới" / "Chỉnh sửa" bị gắn **API** dù chỉ mở form, và hộp thoại không hiện. Đọc mã demo (chỉ đọc) tìm ra **hai lỗi của bộ phân tích**, và theo yêu cầu thêm quy tắc giữ nút dẫn tới API:

| Việc | Nguyên nhân / cách làm |
| :--- | :--- |
| **Cờ API sai ở nút mở hộp thoại** | Mã demo viết `ref.afterClosed$.subscribe((ok) => { if (ok) this.facade.loadList(); })`: (1) tên sự kiện có hậu tố `$` (`afterClosed$`) không được nhận ra; (2) callback có `if` lồng bên trong làm mất điều kiện `afterClosed` (điều kiện trong cùng ghi đè điều kiện ngoài). Nay nhận `afterClosed`, `afterClosed$`, `closed`, `onClose`… và giữ `branch = afterClosed` qua mọi `if` lồng (điều kiện bên trong nối vào `text`) |
| **Hộp thoại biến mất** | Nút Lưu của hộp thoại là `<shared-button type="submit">` (component bọc), bộ phân tích chỉ nhận `<button>` làm nút gửi form nên hộp thoại không có phần tử gọi API và bị ẩn. Nay nhận mọi component bọc nút có `type="submit"` |
| **Hộp thoại đặt thẳng trong template** (`<paper-borrow-ticket-dialog>`, `<sw-dialog>…</sw-dialog>`: 4 chỗ trên demo) | Trước: gộp phẳng phần tử vào màn hình. Nay: MỘT `UI_DIALOG` (`hosted`) dưới nút chứa, nội dung chiếu vào và template của chính nó nằm bên trong; thẻ chủ không thành phần tử. Chưa nối nút bật hộp thoại bằng biến trạng thái (`show = true`) với hộp thoại đó nên nút này vẫn bị coi "chỉ giao diện" |
| **Giữ nút dẫn tới API** | C2 thêm `leadsToApi` cho phần tử (truy vấn đệ quy `findUiLinks`: phần tử → hàm xử lý → `OPENS` hộp thoại / `NAVIGATES` màn hình, đích có phần tử hoặc tự gọi API). Cây mặc định giữ phần tử gọi API **và** nút này (thẻ UI, tooltip "mở hộp thoại hoặc chuyển màn hình có gọi API"); dòng đếm ghi riêng số nút này; Ctrl+K / lọc vẫn tìm cả phần tử ẩn |
| Phiên bản | `FACTS_VERSION` = 3, `ANGULAR_ANALYSIS_VERSION` = 3 |
| Nhãn form | Form không có nhãn hiện "Form onSubmit" thay vì tên nội bộ "form.ngSubmit.onSubmit" |

Kết quả màn hình Tác giả trên demo sau sửa: `Thêm mới [→API]`, `Xóa [API]`, ô lọc trạng thái, ô Họ và tên, bảng (phân trang gọi API), `Chỉnh sửa [→API]`, `Xóa [API]`, rồi **Hộp thoại ▸ Form ▸ Lưu [API]**. Toàn demo: số phần tử gọi API khác nhau 298 → 267 (31 nút mở hộp thoại hết bị gắn nhầm), 58 nút mở hộp thoại / chuyển trang có API được giữ; cây mặc định 3.127 → 589 dòng (−81%).

Kiểm thử: sidecar 45 test (fixture thêm nút Sửa dùng `afterClosed$` + `if`, nút Lưu là `<app-button type="submit">`, hộp thoại đặt trong template); `TreeBuilderTest` + `AngularScanIT` (thêm kiểm tra `leadsToApi`, Sửa không gọi API); web 62 test; Chrome headless 37 kiểm tra (nút Thêm mới / Sửa hiện thẻ UI, hộp thoại ▸ form ▸ Lưu, nút chỉ đổi giao diện ẩn và hiện lại khi bật công tắc, Ctrl+K tới nút ẩn tự bật công tắc).
