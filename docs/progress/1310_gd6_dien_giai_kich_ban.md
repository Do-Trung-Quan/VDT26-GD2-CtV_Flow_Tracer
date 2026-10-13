# Giai đoạn 6 — Diễn giải & Kịch bản, chưa dùng AI (BE + FE)

> Ngày 13/10. Phạm vi lần này: **Task 6.1** (nội dung tất định cho tab Diễn giải và tab Kịch bản; API C4, C5, C6, F1–F3). Phần FE (Task 6.2: cột phải, popup Xem code, Cài đặt › Đối chiếu OpenAPI) làm sau khi người dùng duyệt phần BE — xem mục 5.

---

## 0. Việc kế hoạch làm trước khi vào GĐ6

- **Đối chiếu hệ thống cũ tạm hoãn** (deadline gấp): Giai đoạn 9 (phân tích mã PHP) và Giai đoạn 10 (so sánh cũ – mới, Nhóm 8 / API H) đánh dấu ⏸ trong `roadmap.md`, `feature_list.md`, `API_list.md`, `overview.md`, `project_structure.md`, `CLAUDE.md` (mục phạm vi bị cấm). Nội dung giữ nguyên để làm tiếp nếu còn thời gian; Giai đoạn 11 không còn "so sánh cũ – mới" trong kết quả mong đợi. Phần đã có từ Giai đoạn 1 (nhận diện chỉ **báo** có mã PHP) giữ nguyên.
- **Đọc phần Gemini chỉnh ở GĐ5** (nhật ký `1310_gd5…` mục 7): khử trùng lặp sự kiện (`wiredTargets` trong sidecar, `seenTargets` trong `TraceRun`) và làm phẳng khối `try`. Code hợp lý, nhất quán với thiết kế; **bổ sung**: ghi hai quy tắc vào `overview.md` (§2.2.2 TRIGGERS, §2.2.4) và thêm 2 test bảo vệ trong `EndToEndTracerTest` (trước đó chưa có test riêng).
- **Vệ sinh repo:** mục 7 của nhật ký GĐ5 có tên lớp / màn hình / id Workspace của dự án demo (vi phạm CLAUDE.md §3.5) → đã ẩn danh. **Lưu ý quan trọng:** bản cũ nằm trong commit `5bd78ff` và commit này **đã có trên `origin/main`** (GitHub công khai). Sửa file chỉ ẩn danh từ commit tiếp theo; nội dung cũ vẫn còn trong lịch sử. Cần người dùng quyết định cách xử lý (xem báo cáo).

---

## 1. Task 6.1 — nội dung tất định

### 1.1. Adapter Spring: dựng DTO, thông báo lỗi, quyền

| Việc | Cách làm |
| :--- | :--- |
| Pha 1 | `TypeFact` thêm `typeParameters`, `enumConstants`; `annotationValues` đọc thêm số (`@Size(max = 50)`, `@Min(1)`, `@Column(length = 50)`); `DtoFieldExtractor` ghi trường của lớp và thành phần của `record` (bỏ `static`, `@JsonIgnore`) kèm `@JsonProperty`, ràng buộc Bean Validation / `@Column`, `@Schema(description)` hoặc Javadoc. `JavaFileExtractor.VERSION` = 6 (quét lại sẽ phân tích lại toàn bộ) |
| `DtoSchemaBuilder` | Bắt đầu từ `requestType` / `responseType` / tham số của endpoint → "biểu thức kiểu" (overview §2.2.1) và node `DATA_TYPE` cho mọi kiểu dự án dùng (kể cả lồng nhau, lớp cha — tham số kiểu của lớp cha thay bằng đối số `extends Base<Foo>` —, `record`, generic `ApiResponse<T>`, enum thành `enum` với các giá trị). Tên JSON theo `@JsonProperty`, không có thì theo `spring.jackson.property-naming-strategy` của module chứa kiểu. Ghi `requestTypeExpr`, `responseTypeExpr`, `params[].typeExpr` lên endpoint và cạnh `USES_TYPE` endpoint → kiểu. Tối đa 600 kiểu mỗi Source |
| `MessageResolver` | Đọc `messages_vi.properties` rồi `messages.properties` (file ngôn ngữ khác bị loại; khóa chứa `password/secret/token/credential` bị bỏ) → ghi `throws[].message`. `messages*.properties` được tính là file cấu hình ảnh hưởng phân tích (đổi file thì quét lại) |
| Quyền, cột unique | `BE_METHOD.preAuthorize` (hàm, hoặc lớp); `DB_TABLE.uniqueColumns` từ `@Column(unique = true)` |

### 1.2. Lõi (`flowtracer-core`, `flowtracer-ir`)

| Lớp | Việc |
| :--- | :--- |
| `JsonSchemaBuilder` | Biểu thức kiểu → JSON Schema draft 2020-12 (ràng buộc → `minLength`, `maxLength`, `minimum`, `pattern`, `format`…; mở sâu tối đa 5 cấp, gặp lại chính kiểu đang mở thì dừng) |
| `IoSchemaBuilder` | Khối Đầu vào (`path` / `query` / `body`, mỗi trường một câu "kiểu — bắt buộc, tối đa 50 ký tự. Mô tả") và Đầu ra (giữ lớp bọc `ApiResponse<T>`, mở `T`), lỗi có thể gặp |
| `ConstraintsExtractor` | Ràng buộc thô, mỗi ràng buộc một câu: quyền, tham số bắt buộc, từng trường của thân yêu cầu, từng `throw` kèm điều kiện và thông báo, cột unique của bảng bị ghi |
| `PurposeRule` | Mục đích dự phòng `@Operation` → Javadoc → câu khung; mô tả ngắn của thành phần chứa |
| `BackendFlow`, `UiFlowResolver` | Các hàm / bảng / hệ thống một API đi qua; một thao tác giao diện gọi API nào, mở hộp thoại / màn hình nào, và lời gọi nào **chưa xác định được API đích** |
| `FallbackMockGenerator` | Dữ liệu mẫu tất định theo schema + tên trường; tự kiểm tra bằng `SchemaValidator`, chỗ lệch báo ở `problems`. Không dùng Datafaker (tự viết, cùng seed → cùng dữ liệu) |
| `ScenarioBuilder`, `StepTemplatesVi`, `ir/scenario/Scenario` | Actor + bước khớp 1–1 mũi tên + dòng điều kiện "Nếu …:" / "Ngược lại:" (độ lồng theo khối). Thay `ScenarioStep` (có công tắc "chi tiết kỹ thuật" đã bị bỏ theo CLAUDE.md §3.7); xóa `DisplayNames` (tên actor lấy thẳng nhãn cột) |

### 1.3. API (`flowtracer-app`)

| API | Nội dung |
| :--- | :--- |
| **C4** `GET /nodes/{nodeKey}` | `NodeDetailService`: chuỗi thành phần chứa, "Được gọi từ" (đi ngược `HTTP_REQUEST ← SENDS ← CALLS ← TRIGGERS` bằng CTE, tối đa 30 + tổng), "Gọi tới" |
| **C5** `GET /nodes/{nodeKey}/source` | `CodeSnippetService` (đặt ở package `explanation` để dùng cùng liên kết chốt tay với diagram): đi theo đúng đồ thị của dò vết, một tab cho mỗi (vai trò, file): Template / Component / Kết nối API / Controller / Service / Repository…; đọc qua `SourceReader` |
| **C6** `POST /openapi-check` | `OpenApiCheckService`: tìm `openapi.*` / `swagger.*` trong Source Backend hoặc tải URL (http/https, 10 giây, ≤ 10 MB); đọc JSON hoặc YAML (SnakeYAML an toàn); so theo phương thức + đường dẫn (thử cả khi có / không có `servers[0].url`, `basePath`, context-path) |
| **F1** `GET /nodes/{nodeKey}/explanation` | `ExplanationService`: API, phần tử giao diện (mỗi API nó gọi một mục), thành phần chứa; áp liên kết chốt tay như dò vết. Phần tử có lời gọi chưa xác định đích → `unresolvedCalls` và mục đích nói rõ "gọi máy chủ nhưng …" (không bao giờ ghi "không gọi máy chủ") |
| **F2** `GET /nodes/{nodeKey}/mock-data` | Dữ liệu mẫu theo cấu trúc DTO; `apiKey` chọn API khi phần tử gọi nhiều API |
| **F3** `GET /traces/{traceId}/scenario` | Kịch bản từ luồng đã dò (đọc từ bộ nhớ đệm D1; hết hạn → `404 TRACE_NOT_FOUND`) |
| F4 | Thuộc GĐ7 (cần AI) |

`nodeKey` trên đường dẫn: mã hóa URL **hai lần** (Tomcat không nhận `%2F`); Web dùng `encodeURIComponent(encodeURIComponent(key))`, Engine dùng `NodeKeyCodec.decode`. Mã lỗi mới: `EXPLANATION_UNSUPPORTED` (422), `OPENAPI_NOT_FOUND` (404), `OPENAPI_INVALID` (422).

Tài liệu đã cập nhật: `overview.md` (§2.2.1 `DATA_TYPE` và biểu thức kiểu, thuộc tính mới; §2.2.6 **Tab Diễn giải & Kịch bản tất định** mới), `API_list.md` (C4–C6, F1–F3, quy ước mã hóa `nodeKey`), `feature_list.md`, `project_structure.md`, `CLAUDE.md` (thêm `MessageResolver` vào danh sách nơi đọc file của dự án phân tích; C5 chuyển sang `explanation`), `roadmap.md`.

## 2. Kiểm thử

| Mức | Kết quả |
| :--- | :--- |
| `flowtracer-core` | 72 test (thêm 2 test `try` làm phẳng / khử trùng lặp sự kiện từ GĐ5) |
| `flowtracer-adapter-spring` | 60 test: thêm `DtoSchemaBuilderTest` (5): tên JSON theo chiến lược Jackson, `@JsonProperty`, `@JsonIgnore`, lớp cha + tham số kiểu, enum, record, generic envelope, ràng buộc, `USES_TYPE`, thông báo tiếng Việt thắng tiếng mặc định; golden `spring-flow` / `spring-mini` sinh lại có chủ đích |
| `flowtracer-app` unit | 43 test; `ExplanationGoldenTest` (9) + golden `spring-flow.explain.*` (6 API) và `spring-flow.scenario.category-save.json`: đầu vào / đầu ra / ràng buộc / dữ liệu mẫu mọi API; **số bước kịch bản = số mũi tên và tên actor = nhãn cột với mọi golden trace + luồng nút Lưu**; dữ liệu mẫu qua `SchemaValidator` với mọi API; chuỗi dự phòng mục đích; phần tử chưa xác định đích không bị ghi "không gọi máy chủ". ArchUnit 9 luật giữ nguyên |
| IT (Testcontainers) | 28 IT; mới `ExplanationApiIT` (5): F1 cho API / nút Lưu / nút chỉ mở hộp thoại / nút chưa xác định đích / màn hình; F2 ổn định giữa các lần gọi; F3 bước n = mũi tên n (qua D1); C4, C5 (đọc mã thật, tab Service có `existsByCode`), C6 (file `openapi.yaml` của fixture: khớp 3, thừa 1, thiếu 3; URL chính Engine; 422 / 400) |
| `check-forbidden-terms --all` | Sạch |

Fixture `spring-flow` (tự viết, miền Shop) thêm: Bean Validation + enum cho `CategoryRequest`, `@PreAuthorize` trên `create`, `@Column(unique = true)`, `messages_vi.properties`, `openapi.yaml` mẫu.

**Đo trên dự án demo** (Workspace tạm BE + FE, đã xóa sau khi đo; chỉ số liệu tổng hợp):

| Chỉ số | Giá trị |
| :--- | :--- |
| Diễn giải 244 API | 244/244 thành công, 0 lỗi; thời gian trung vị 39 ms, p95 88 ms |
| Nguồn "Mục đích" | `@Operation` 105 (43%), Javadoc 1, câu khung 138 (57%) — phần câu khung là chỗ AI (GĐ7) có giá trị nhất |
| Đầu ra hiển thị | 95% API; ràng buộc thô 89% API; "lỗi có thể gặp" 89% API; 75 API có thân yêu cầu (đều dựng được) |
| Dữ liệu mẫu | 244/244 khớp cấu trúc DTO (0 `problems`); trung vị 32 ms |
| Phần tử giao diện (mẫu 40) | 100% có diễn giải (trung vị 32 ms); 10 phần tử gọi ≥ 2 API (tạo xong tải lại danh sách); **8/40 có lời gọi chưa xác định API đích** (được báo đúng, xem 3) |
| Kịch bản (mẫu 40) | **40/40 số bước = số mũi tên**; 7 ms |
| Xem code (C5) | trung vị 10 tab / phần tử (4–22); 42 ms |
| Thành phần chứa | 40/40; trung vị 90 ms |
| "Được gọi từ" của API (mẫu 25) | 19/25 có ≥ 1 phần tử gọi tới (6 API không phần tử giao diện nào gọi — hợp lý với API dùng cho nơi khác) |
| Đối chiếu OpenAPI (C6) | Demo không có file đặc tả trong repo → `404 OPENAPI_NOT_FOUND` đúng như thiết kế (kiểm tra khớp / lệch dùng fixture) |

## 3. Việc còn dở / điểm cần biết

- **FE (Task 6.2)** đã làm — xem mục 5.
- **Chưa làm** (chủ động): bước "tài liệu đặc tả" trong chuỗi dự phòng của Mục đích (Workspace chưa có nơi khai báo thư mục tài liệu; đoán đoạn mô tả trong tài liệu tự do dễ hiển thị sai nên không làm khi chưa có quy ước); các mục AI (F4, mục đích 80–100 từ, câu ràng buộc gọn, lời kịch bản) thuộc GĐ7.
- **Câu khung chiếm 57% mục đích trên demo**: đọc được nhưng khô ("API GET … thuộc nhóm “…”: nhận …, trả về …"); không bịa thêm ý — đúng nguyên tắc tất định.
- **8/40 phần tử "gọi API" có lời gọi chưa xác định đích** (cùng nguyên nhân với GĐ5: không có API khớp hoặc 2 API ngang điểm) — diễn giải nói rõ, người dùng chốt tay ở popover trên diagram.
- Bộ lọc nhiễu, tên actor: kịch bản dùng đúng nhãn cột của diagram (đã áp bộ lọc nhiễu), nên tên hàm trong câu khớp nhãn mũi tên.
- C5 "Xem code" cho API chưa xác định đích không có tab Backend (nhất quán với diagram); sau khi chốt tay tab hiện ra.

## 4. Vấn đề gặp phải

- Node `DATA_TYPE` ban đầu chưa tồn tại (IR chưa có kiểu dữ liệu nào) nên Task 6.1 gồm cả việc dựng DTO ở adapter, không chỉ ở lõi.
- `@Validated` trên controller làm lỗi tham số (`context_lines=99`) thành `500` thay vì `400` (do `ConstraintViolationException` không được xử lý) → bỏ `@Validated`, dùng kiểm tra tham số có sẵn của Spring như `SearchController`.
- Phần tử "gọi API" có liên kết chưa xác định ban đầu bị diễn giải là "chỉ thay đổi giao diện, không gọi máy chủ" — phát hiện nhờ đo trên demo (8/40), đã sửa (`UiFlowResolver.unresolved`, `unresolvedCalls`) và thêm test.

---

## 5. Task 6.2 — Cột phải (FE)

> Làm sau khi phần BE được duyệt, bám `docs/UI/UI_offline.html` (hàm `renderRight`, `explainHtml`, `scriptHtml`, `viewCode`, tab `openapi`) và đã đối chiếu từng màn bằng ảnh chụp bản vẽ so với bản cài đặt (Chrome headless).

### 5.1. Việc đã làm

| Việc | Nơi làm |
| :--- | :--- |
| Kiểu API sinh lại từ OpenAPI của Engine; `explanation.api.ts` (F1, F2, F3, C4, C5, C6), hàm mã hóa khóa node hai lần | `core/api/` |
| Cột phải: tên + nhãn loại + nút **Xem code**, hai tab **Diễn giải** / **Kịch bản**, thành phần chứa (không tab) | `explorer/panel/node-panel.ts` |
| Khối JSON nền tối, tô màu cú pháp (khóa / chuỗi / số / true-false-null), nút **Sao chép** | `panel/json-block.ts`, `explanation-model.ts` |
| Tab Diễn giải đủ 7 mục theo thứ tự bản vẽ: Mục đích · Đầu vào · Đầu ra · Ràng buộc · Dữ liệu mẫu · Được gọi từ · Gọi tới (chip bấm để chuyển sang thành phần đó) | `node-panel.ts` |
| Tab Kịch bản: "Các actor tham gia", "Scenario:", dòng điều kiện in nghiêng, bước con thụt vào, bước chưa xác định chữ đỏ | `node-panel.ts` |
| **Tô sáng đồng bộ hai chiều**: rê chuột bước → mũi tên sáng tạm; bấm bước → giữ sáng và cuộn diagram tới mũi tên; bấm mũi tên → bước sáng và cuộn danh sách; bấm lại để tắt | `sequence-canvas.ts` (`highlight` thành biến hai chiều, thêm `preview`, `revealStep`), `explorer.page.ts` |
| Popup **Xem code** (C5): tab theo lớp, đường dẫn + nhãn "Chỉ đọc", đánh số dòng, tô sáng dòng liên quan | `panel/code-dialog.ts` |
| Cài đặt › **Đối chiếu OpenAPI** (C6): báo tìm thấy file đặc tả trong repo, ô nhập URL, 3 thẻ số liệu, bảng "Chỉ trong mã / Chỉ trong Swagger" kèm gợi ý | `workspaces/openapi-check-tab.ts`, `openapi-check-model.ts` |
| Cột phải rộng 390px như bản vẽ (330px khi màn hình < 1100px); icon `refresh` | `explorer.page.ts`, `icon.ts` |

Điều chỉnh nhỏ ở BE để khớp bản vẽ: mô tả actor viết hoa chữ đầu ("Người dùng thao tác trên giao diện."); nhãn nguồn mục đích "Swagger @Operation"; `NodeRelationResponse` thêm `elementType`, `fallbackName`, `routePath` để chip "Nút Lưu", "Màn hình Danh mục" đặt nhãn đúng như cây Frontend; `context` của "Được gọi từ" kèm loại ("Hộp thoại Thêm danh mục") và bỏ qua form.

### 5.2. Kiểm thử

- `npm --prefix web run test`: **86 test** qua (thêm: tô màu JSON kể cả chuỗi chứa dấu nháy / ký tự HTML, đầu vào / đầu ra, dòng code, dòng kịch bản, `ExplanationApi`, bảng đối chiếu OpenAPI). `lint`, `build` sạch.
- Chrome headless trên fixture (`angular-mini` + `spring-flow`): **47/47** kiểm tra qua — đủ 7 mục đúng thứ tự, mục đích có nhãn nguồn, khối JSON tô màu có Sao chép, ràng buộc, dữ liệu mẫu có nhãn "Bộ sinh dự phòng", chuỗi "Được gọi từ", chip chuyển thành phần, kịch bản = số mũi tên, tô sáng hai chiều (rê / bấm / bấm lại), popup Xem code (tab, dòng tô sáng, Esc), thành phần chứa, phần tử chỉ giao diện, phần tử có lời gọi chưa xác định, API, giữ tab khi đổi thành phần, tab Đối chiếu OpenAPI (tìm thấy file, URL không hợp lệ → báo lỗi).
- Engine: `verify` toàn bộ (28 IT) và các test lõi qua sau khi đổi nhãn / chữ hoa.

### 5.3. Đối chiếu bản vẽ — chỗ khác (đã chủ động)

| # | Bản vẽ | Bản cài đặt | Lý do |
| :--- | :--- | :--- | :--- |
| 1 | Nguồn mục đích phần tử giao diện: "Chú thích TSDoc" | "Câu khung" | Bộ phân tích Angular chưa đọc TSDoc; đoạn mô tả không bịa |
| 2 | Có nút "So sánh cũ – mới" | Không có | Mở rộng "Đối chiếu hệ thống cũ" đã tạm hoãn |
| 3 | Mục đích 80–100 từ do AI, chip "Sinh lại" ở Mục đích / Dữ liệu mẫu | Chưa có AI: hiện câu khung + dòng "AI chưa được bật…"; nút "Sinh lại" ở tab Kịch bản hiện mờ | AI làm ở Giai đoạn 7 |
| 4 | Ràng buộc khi AI tắt là chú thích thô viết bằng mã (`@NotBlank @Size(max=50) code`) | Câu tiếng Việt tất định rút từ code ("Trường code: bắt buộc, tối đa 50 ký tự") | Đọc được hơn, vẫn trỏ về ràng buộc có thật |
| 5 | Thành phần gọi 1 API | Gọi nhiều API (vd nút Lưu sau khi tạo còn tải lại danh sách): thêm hàng chip "API được gọi (n)" để chọn API xem Đầu vào / Đầu ra / Dữ liệu mẫu | Bản vẽ chỉ có ví dụ 1 API |
| 6 | Đầu vào chỉ có `body` | Giữ đúng như bản vẽ (`{"body": {…}}`); API có query hiện `{"query": {…}}` | — |
| 7 | Dữ liệu mẫu: một khối `{request, response}` | Giống bản vẽ | — |
| 8 | Chọn module trên cây Backend hiện mô tả "Module" ở cột phải | Hàng module chỉ mở / thu gọn (hành vi từ GĐ2), cột phải chỉ có API / phần tử / thành phần chứa phía Frontend | Chưa có API diễn giải cho module |
| 9 | Dòng tô sáng trong Xem code: 1–2 dòng | Cả thân hàm liên quan (tối đa 30 dòng) | Dòng quan trọng không xác định tất định được |
| 10 | Cột "Database" chung trong danh sách actor | Mỗi bảng một actor "Bảng <tên>" | Đã chốt từ GĐ3 |
| 11 | Tab Đối chiếu OpenAPI: banner tìm thấy file, chỉ chạy khi bấm "Đối chiếu" | Chạy đối chiếu với file trong repo ngay khi mở tab (để biết có file hay không) và hiện kết quả luôn | Không có API chỉ dò file; việc đọc nhẹ, chỉ đọc |
| 12 | Cột phải nêu "Luồng xử lý n bước · m cột", "Thuộc về", "Vị trí trong mã nguồn" (thêm ở GĐ3–4) | Đã bỏ — bản vẽ không có; chuỗi thành phần chứa nằm ở "Được gọi từ", đường dẫn file ở Xem code | Bám bản vẽ |
| 13 | Tab đang xem được giữ khi chọn thành phần khác | Giống bản vẽ | — |

Cảnh báo của API / luồng (ngừng dùng, đường dẫn chưa giải được, luồng chưa dò được) vẫn hiện thành khung vàng ở đầu cột phải vì là thông tin thật mà bản vẽ chưa có chỗ.

---

## 6. Sửa sau khi chạy thử trên dự án demo (13/10)

Người dùng chạy giao diện trên dự án demo và nêu 4 vấn đề. Nguyên nhân và cách sửa (đã đo lại trên demo bằng Workspace tạm, đã xóa; chỉ ghi số liệu tổng hợp):

| # | Hiện tượng | Nguyên nhân | Cách sửa |
| :--- | :--- | :--- | :--- |
| 1 | Đầu ra chỉ có `"data": "object"`; dữ liệu mẫu `response: {}` | Lớp bọc kết quả `ApiResponse<T>` không có mã nguồn: nằm trong jar thư viện lõi của dự án, bộ phân tích chỉ đọc `.java` nên không biết các trường của lớp bọc | **Đọc khai báo trong jar** (người dùng đồng ý): `ClassFileReader` đọc file `.class` (trường, kiểu generic, annotation JSON / kiểm tra dữ liệu), `LibraryStubBuilder` dựng mã rút gọn cho lớp mà mã dự án import (và lớp chúng tham chiếu, tối đa 300), rồi đi qua bộ phân tích như mã thường. Thêm hook `StackAdapter.isLibraryArchive / libraryFiles`; `DiscoverStep` đọc jar, `ExtractStep` trích dữ kiện lớp rút gọn (không vào danh sách file, không tính vào số liệu quét). Đổi jar thì Quét lại phân tích lại. Thêm khai báo sẵn `Page<T>` của Spring Data |
| 2 | Dữ liệu mẫu "Điện thoại", "DT001" | Bộ sinh dự phòng đặt cứng giá trị theo miền "Shop" | Giá trị trung tính: tên → "Tên mẫu", mã → "MA001", mô tả → "Mô tả mẫu"…; tên trường `*_time` / `*_at` → ngày giờ ISO; `errorCode` → 0; `page` → 1; `page_size` → 20 |
| 3 | Ràng buộc và "Lỗi có thể gặp" in nguyên `AppError.X.exception`, hằng số, biểu thức code | Lỗi dựng từ hằng enum mã lỗi nên không có khóa thông báo; tool chỉ tìm `messages_vi.properties` (dự án dùng `messages-app_vi.properties`) | Nhận `Enum.CONST.exception()` và tra khóa thông báo khai báo trong enum → câu tiếng Việt; chấp nhận tên file `messages-*_vi`; câu ràng buộc chỉ nêu thông báo (bỏ mã trong ngoặc); `ConditionTextRule` diễn đạt lại điều kiện đơn giản ("status để trống hoặc (status khác 0 và status khác 1)") |
| 4 | Form Lưu của một màn hình chỉ vẽ PUT, thiếu POST | Luồng PUT ở Backend kết thúc bằng `throw` không điều kiện → bộ dò vết đặt cờ dừng cho **cả** luồng nên nhánh `else` (POST) không được vẽ | Cờ dừng chỉ áp dụng trong nhánh chứa lệnh `throw`; nhánh else và phần còn lại vẫn được vẽ. Thêm test `TracerTest` |

Kết quả đo trên demo: API Tác giả (danh sách / tạo / sửa) có đầu ra đúng lớp bọc `errorCode` / `message` / `data` và đủ trường của `data` (kể cả tên JSON `page_size`, `sort_order`, `created_time`); nút Lưu của hai form (Tác giả, Chức vụ) cùng vẽ PUT và POST trong khung `alt … Ngược lại`; số API có đầu ra rỗng / "object" giảm 18 → 12, 12 API còn lại trả file hoặc không có JSON. Engine: 218 test qua (gồm `LibraryStubBuilderTest`, `ConditionTextRuleTest`, test enum mã lỗi).

**Quyết định:** "Lỗi có thể gặp" khó dịch tay hết, nhất là lỗi phát sinh về sau → ở Giai đoạn 7 để AI (cùng mục đích `PURPOSE_TEXT`) diễn đạt lại; câu tất định ở trên là phương án dự phòng khi AI tắt / lỗi. Đã ghi vào roadmap Task 7.2 và overview §2.7.

**Còn lại:** khi có AI, kiểm tra lại các câu ràng buộc cho tự nhiên; thông báo ném bằng chuỗi tự do hoặc khóa nằm ngoài file thông báo vẫn hiện "lỗi <mã>".
