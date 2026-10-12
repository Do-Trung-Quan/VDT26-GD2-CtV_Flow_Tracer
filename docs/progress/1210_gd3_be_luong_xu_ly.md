# Giai đoạn 3 — Luồng xử lý Backend & sequence diagram

> Ngày làm: 12/10/2026. Phạm vi: **Task 3.1** (phân giải Spring), **Task 3.2** (Tracer, cột theo vai trò, xuất diagram) và **Task 3.3** (canvas sequence diagram trên giao diện — làm sau khi người dùng duyệt Backend; xem mục 5).
> Trạng thái: ✅ Xong, chờ người dùng review.

---

## 1. Việc đã làm

### 1.1. Task 3.1 — Phân giải Spring (`flowtracer-adapter-spring`)

| Thành phần | Làm gì |
| :--- | :--- |
| `CallSiteExtractor` (+ `GuardResolver`) | Mỗi hàm / constructor: mọi lời gọi **và** mọi lệnh `throw` theo **thứ tự thực thi** (đối số, receiver chạy trước lời gọi bao ngoài) trong **chung một dãy số**; điều kiện bao quanh gần nhất (then / else / case / catch / thân vòng lặp). Hiểu tham chiếu hàm `this::toResponse` |
| `StringFragmentExtractor` | Mảnh chuỗi trông như JPQL/SQL (literal, text block, nối literal); đánh dấu mảnh nằm trong điều kiện |
| `TypeDeclarationExtractor`, `MemberExtractor` | Thêm giá trị annotation (`@Table(name)`, `@Qualifier`, `@Query(value, nativeQuery)`, `@ConditionalOnProperty`…), tên + `@Qualifier` của tham số, hàm có thân hay không, lớp trừu tượng |
| `ClassIndex` | Tên ngắn → FQN theo import / cùng package / import `*`; tổ tiên, lớp hiện thực, tìm field và hàm theo tên + số đối số (kể cả lớp cha) |
| `InjectionResolver` | Field được tiêm (Lombok `@RequiredArgsConstructor`, `@Autowired` và constructor đều là field thật trong mã) + `@Qualifier` ở field hoặc ở tham số constructor cùng tên |
| `ImplementationResolver` | Chọn lớp hiện thực của interface: 1 bean → `CERTAIN`; `@Qualifier` khớp 1 bean → `CERTAIN`; `@Primary` hoặc tên field trùng tên bean → `INFERRED`; còn lại → `UNRESOLVED` + ứng viên có điểm (**không chọn hộ**) |
| `LayerClassifier`, `BusinessNames` | Xếp tầng Controller / Service / Repository / khác (annotation → kế thừa Spring Data → lớp hiện thực → hậu tố tên) và tên nghiệp vụ để đặt nhãn cột |
| `DataAccessResolver`, `QueryTextAnalyzer`, `EntityTableResolver`, `SpringDataMethods`, `QuerySinkRules` | Truy vấn suy ra từ tên (`findBy…`, `existsBy…`, `countBy…`, `deleteBy…`), `@Query` JPQL/native, hàm kế thừa (`save`, `findById`…), truy vấn ghép bằng `StringBuilder` / text block thực thi qua `EntityManager` / `JdbcTemplate` / luật `QUERY_SINK`; bảng từ `@Table(name)` (kể cả hằng số), nếu không thì suy ra theo quy ước đặt tên (đánh dấu `INFERRED`) |
| `FlowResolver` (thay `CallResolver`) | Từ endpoint: `HANDLED_BY` → `CALLS` → `EXECUTES` → `READS`/`WRITES`; **chỉ dựng phần đi tới được từ endpoint** (kể cả ứng viên của lời gọi chưa xác định, để chốt tay rồi dò tiếp được) |

Đã bỏ `ThrowExtractor` và `CallResolver` (gộp vào `CallSiteExtractor` / `FlowResolver`). `DtoFieldExtractor`, `DtoSchemaBuilder`, `MessageResolver` thuộc Giai đoạn 6 nên để nguyên khung.

**IR mới:** node `BE_METHOD`, `DATA_QUERY`, `DB_TABLE`; cạnh `HANDLED_BY`, `CALLS`, `EXECUTES`, `READS`, `WRITES` (chi tiết thuộc tính: `overview.md` §2.2.1). Không đổi node key, không đổi `contracts/ir-fragment.schema.json` (các loại node/cạnh này đã có sẵn trong schema).
`JavaFileExtractor.VERSION` = `2` → cache dữ kiện cũ tự bị bỏ.

### 1.2. Task 3.2 — Tracer, cột, xuất diagram (`flowtracer-core` + `flowtracer-app`)

| Thành phần | Làm gì |
| :--- | :--- |
| `Tracer` + `TraceRun` | Dò từ endpoint theo `callOrder`: bước đi, chiều trả về (kiểu trả về khai báo), bước tự xử lý, truy vấn → Database, nhánh lỗi, khối `alt`/`opt`/`loop` lồng nhau. Quy tắc đầy đủ: `overview.md` §2.2.4 |
| `ParticipantBuilder` | Cột Client · Controller X · Service X (nhiều cột nếu gọi nhiều service) · Repository X · Database; lớp trùng nhãn thì thêm tên lớp |
| `SelfStepBuilder` | Gom lời gọi hàm private / hàm của entity thành MỘT bước tự xử lý (`checkPermission(), normalize()`), mức tin cậy = mức thấp nhất |
| `BlockBuilder` | Đánh số bước, đổi dấu mở/chuyển nhánh/đóng thành khối (`TraceBlock` nay có `elseBranches`) |
| `NoiseFilterApplier`, `OverrideApplier` | Bỏ thành phần khớp bộ lọc nhiễu cùng phần bên trong; áp chốt tay (`PIN` thay đích + `pinned`, `REJECT` bỏ cạnh / ứng viên) |
| `PlantUmlExporter`, `SequenceViewModelBuilder`, `SvgExporter` | Mã PlantUML (`strictuml`, `autonumber`, `activate`/`deactivate`, `alt/else/end`) và SVG tĩnh |
| `TracingService`, `TraceCache`, `TraceController` | **D1** `POST /api/workspaces/:id/traces`, **D2** `GET /api/workspaces/:id/traces/:traceId/export?format=plantuml|svg`; cache theo `trace_id` (≤ 256 mục, 10 phút) |
| `GraphQueryService.subgraphFrom` + `IrEdgeRepository` | Nạp đồ thị con bằng truy vấn đệ quy (một lượt lấy node đi tới được, một lượt lấy cạnh), kèm `edgeRef` = id cạnh trong CSDL |

Thay đổi liên quan: `TraceStep` thêm cờ `error`; `TraceIds` đưa bộ lọc nhiễu vào khóa (đổi bộ lọc là đổi luồng); `WorkspaceService.traceSettings` (cho `tracing`); mã lỗi `TRACE_NOT_FOUND` (404), `TRACE_START_UNSUPPORTED` (422); bộ lọc nhiễu mặc định thêm `*Response#*`, `*Request#*`, `*Dto#*`, `*DTO#*`.

### 1.3. Tài liệu `.context/` đã cập nhật

`overview.md` (§2.1 cách giải kiểu, §2.2.1 thuộc tính node, §2.2.4 quy tắc dựng bước + ví dụ), `API_list.md` (D1, D2, `trace_id`), `project_structure.md` (adapter-spring, core, tracing, fixtures), `roadmap.md` (bảng tiến độ, Task 3.1), `fixtures/README.md`.

---

## 2. Kiểm thử

| Lệnh | Kết quả |
| :--- | :--- |
| `engine/mvnw clean verify` | ✅ BUILD SUCCESS: **123 test đơn vị** (ir 4 · core 37 · adapter-spring 55 · app 27, gồm `ArchitectureTest` 9 luật giữ nguyên) và **21 test tích hợp** (`ScanPipelineIT` 8 · `SchemaMappingIT` 1 · `WorkspaceApiIT` 10 · `TracingApiIT` 2) |
| `check-forbidden-terms --all` | ✅ sạch (502 file) |

Các bộ test mới:

| Test | Kiểm gì |
| :--- | :--- |
| `SpringFlowGoldenTest` (+ golden `spring-flow.ir.json`) | IR của fixture `spring-flow`: thứ tự lời gọi, `CERTAIN`/`INFERRED`/`UNRESOLVED` + ứng viên, truy vấn suy ra từ tên / `@Query` / ghép động, bảng từ `@Table`, hằng số, quy ước đặt tên; mọi cạnh có vị trí hoặc lý do; **chạy 2 lần cho cùng kết quả** |
| `QueryTextAnalyzerTest`, `QuerySinkRulesTest` | Đọc câu JPQL/SQL (động từ, bảng sau FROM/JOIN/UPDATE/INTO, danh sách bảng, bỏ chú thích / chuỗi / truy vấn con); luật `QUERY_SINK` |
| `TracerTest` (14 ca, `flowtracer-core`) | Quy tắc dựng bước trên đồ thị dựng tay: gom tự xử lý, lọc nhiễu, `alt`/else, `opt`/`loop`, chỉ vẽ nhánh lỗi phụ thuộc lời gọi, chưa xác định không đi sâu, chốt tay / từ chối, đệ quy và độ sâu, đặt tên cột |
| `TracerGoldenTest` (+ golden `spring-flow.trace.*.json/.puml`) | Adapter thật + Tracer thật trên 5 API của fixture: JSON trace và mã PlantUML; hình dạng giống Phụ lục A; tất định; dò vết ≪ 2 giây |
| `TracingApiIT` | Trên PostgreSQL thật: quét fixture rồi D1/D2 qua HTTP — 16 bước, khối `alt`, `edgeRef` là id cạnh thật, cùng `trace_id` khi dò lại, `UNRESOLVED` + cảnh báo, xuất PlantUML/SVG, các mã lỗi 400/404/422 |

### Số đo trên dự án demo (đọc cục bộ, chỉ số tổng hợp)

| Chỉ số | Giá trị |
| :--- | :--- |
| Quét lần đầu 725 file Java | ~10 giây (lần đầu sau khi mở Engine ~18 giây); NFR-01 ≤ 3 phút ✅ |
| IR | 2.499 node, 5.940 cạnh, 244 endpoint; 0 file lỗi, 3 cảnh báo (đều là trùng endpoint đã biết) |
| Dò vết 244 API | 244/244 thành công; trung vị 22 ms, p95 48 ms, tối đa 121 ms (NFR-04 ≤ 2 giây ✅) |
| Số cột | trung vị 6, tối đa 20; 185 API chạm Database, 210 chạm Repository |
| Số bước | trung vị 25, p95 65, tối đa 150; 6 API vượt 70 (xem mục 3.2) |
| Khối `alt` | 61/244 API có ít nhất một |
| Lời gọi chưa xác định | 97 cạnh `UNRESOLVED` (52 không ứng viên + 45 có ứng viên) → 30 API có bước chưa xác định; 68 bước `INFERRED` |
| Lời gọi tới entity / DTO / lớp khác không tìm thấy hàm đích | ~4.270 (đa số getter/setter do Lombok sinh) — bỏ qua, ghi một chẩn đoán INFO tổng hợp |

---

## 3. Điều cần người dùng biết

### 3.1. Khác với bản vẽ giao diện mẫu (`docs/UI/UI_offline.html`) — cần quyết định hoặc chấp nhận

Giai đoạn FE sau này sẽ vẽ theo mẫu; những chỗ **Backend không thể (hoặc chưa thể) cho ra đúng như mẫu**:

1. **Nhãn khối `alt` / `else`**: mẫu ghi lời văn nghiệp vụ ("Mã đã tồn tại" / "Mã chưa tồn tại"). Backend trả **điều kiện kỹ thuật** (`categoryRepository.existsByCode(code)`) và "ngược lại". Lời văn tiếng Việt cần file thông báo i18n + `MessageResolver` (Giai đoạn 6) — hoặc AI ở Giai đoạn 7 — và chỉ làm được khi `throw` mang khóa thông báo.
2. **Nhãn chiều trả về lỗi**: mẫu ghi `400 Bad Request`, `409 Conflict`. Phân tích tĩnh chưa biết mã HTTP của lỗi (do `@ControllerAdvice` / `@ResponseStatus` quyết định). Backend trả khóa thông báo (`CATEGORY_CODE_EXISTS`) rồi tên exception (`BusinessException`).
3. **`INSERT category` / `UPDATE category` của `save()`**: phân tích tĩnh không biết bản ghi mới hay cũ, nên nhãn là `INSERT/UPDATE category`.
4. **Ca "chưa xác định" trong mẫu mâu thuẫn với luật đã chốt**: mẫu cho ứng viên `@Primary` mức "Cao" nhưng vẫn gắn `UNRESOLVED`. Theo luật (Nhóm 5, CLAUDE.md §3.7) có đúng một `@Primary` thì là `INFERRED` (vẫn đi tiếp, có ứng viên để đổi). Fixture của mình tách thành hai ca: 2 lớp **không** `@Primary` → `UNRESOLVED`; 1 lớp `@Primary` → `INFERRED`. Mẫu cần sửa lời giải thích ở ứng viên cho khớp.
5. **Tên cột của service/repository không thuộc cùng module**: mẫu ghi "Service Sản phẩm". Backend chỉ có tên tiếng Việt khi một Controller cùng tên gốc có `@Tag`; còn lại là tên lớp ("Service ProductQuery"). Tên module Frontend / i18n (Giai đoạn 4) có thể bổ sung.
6. **Khối lồng nhau và nhiều khối**: mẫu chỉ vẽ **một** khối `alt`. Luồng thật có `alt` / `opt` / `loop` **lồng nhau**, nhánh `else` kéo tới hết luồng — canvas Task 3.3 phải vẽ được khung lồng, vạch `else`, nhiều khung.
7. **Độ dài luồng**: mẫu ~20 bước. Sau khi rút gọn, trên demo trung vị 25, p95 65, **tối đa 150 bước** (6 API vượt 70 vì giữ đủ nghiệp vụ) và tới 20 cột. Task 3.3 cần cuộn / thu phóng tốt, không thể giả định ≤ 70 bước.
8. **Chiều trả về của hàm `void`** có nhãn `OK`; mẫu không có ví dụ. Chiều trả về của lời gọi chưa xác định là `(chưa xác định)` như mẫu.

### 3.2. Quyết định thiết kế cần người dùng xem

**Cập nhật sau review (cùng ngày) — diagram gọn nhưng không mất nghiệp vụ:**

- **Chỉ vẽ ngoại lệ nghiệp vụ**: `throw` chỉ vẽ khi điều kiện phụ thuộc dữ liệu từ Service / Repository hoặc trạng thái entity (`existsByCode`, `count > 0`, `order.isPending()`) và exception không phải loại kiểm tra dữ liệu (`*Validation*`, `*Invalid*`, `IllegalArgument*`…). Kiểm tra đầu vào không vẽ — là "Ràng buộc" của Giai đoạn 6.
- **Không vẽ bước không thuộc nghiệp vụ**: lớp tiện ích (mapper, parser, converter, util, validator…) và hàm `validate…` không thành bước tự xử lý (vẫn đi sâu để thấy lời gọi sang Service / Repository). Bước tự xử lý chỉ còn hàm cùng lớp và hàm của entity. Thêm bộ lọc nhiễu mặc định `*Parser#*`, `*Converter#*`, `*Assembler#*`, `*Formatter#*`, `*Helper#*`, `*Util#*`, `*Validator#*`.
- **Mục tiêu 70 bước, không đánh đổi nghiệp vụ.** Bản rút gọn đầu tiên (bỏ nhánh lỗi → bỏ bước tự xử lý → **cắt độ sâu**) làm mất nghiệp vụ: đo trên demo, 20 API bị cắt độ sâu mất trung vị 51% (tối đa 99%) số lời gọi sang Service / Repository / Database. Đã bỏ cách cắt độ sâu và bỏ nhánh lỗi. Nay rút gọn theo thứ tự (1) **không vẽ lại phần bên trong của lời gọi lặp lại** (bước có `repeatOf` trỏ về lần gọi đầu) rồi (2) bỏ bước tự xử lý trong cùng một lớp; nếu vẫn dài hơn 70 thì **giữ nguyên và ghi rõ** trong `warnings[0]`.
- **Độ sâu chỉ tính cấp nghiệp vụ** (Controller / Service / Repository gọi nhau): trước đây hàm phụ, hàm tiện ích cũng tốn cấp nên 13 API bị cắt oan ngay cả khi chưa rút gọn; nay không còn cảnh báo cắt độ sâu nào trên demo.
- **Kết quả trên demo (244 API), đối chiếu với luồng đầy đủ:** 212 vừa 70 bước sẵn; 21 chỉ cần gộp lời gọi lặp lại; 11 phải bỏ thêm bước tự xử lý; 6 vẫn dài hơn 70 (74, 80, 96, 104, 118, 150 bước). **Số thao tác nghiệp vụ (từ cột nào sang cột nào, nhãn gì) mất: 0 ở cả 244 API**; nhánh lỗi nghiệp vụ mất: 0. Cái bị bỏ: 640 bước tự xử lý (tên hàm phụ trong cùng một lớp) ở 23 API và phần bên trong lặp lại của lần gọi thứ 2 trở đi. Số bước trung vị 25, p95 65, lớn nhất 150.
- **Cải tiến giải kiểu** (theo chỉ đạo): getter/setter trên entity / DTO bỏ qua; gọi vào Service / Repository không thấy hàm đích → `UNRESOLVED` không ứng viên (demo: 52 cạnh, cộng 45 cạnh có ứng viên); lời gọi nối chuỗi và biến `var` giải bằng kiểu trả về khai báo (`Optional<X>` + `orElseThrow`…); hàm CRUD Spring Data luôn là `EXECUTES`.

**Quyết định trước đó:**

- **Không vẽ kiểm tra đầu vào thuần túy.** Lần chạy đầu trên demo, mọi `if … throw` đều thành khối `alt` + chuỗi trả về lỗi → 31% số bước chỉ là chiều trả về lỗi, tối đa 494 bước.
- **Một `Guard` cho mỗi lời gọi**: chỉ lấy điều kiện gần nhất (IR chỉ có một `guard`), nên `if (a) { if (b) { gọi } }` chỉ ghi `b`.
- Nhãn bước tự xử lý tối đa 4 tên hàm rồi `…`.

### 3.2b. Sửa sau khi người dùng chạy thử trên giao diện (12/10)

- **Bấm API không ra diagram** (`0 bước`, "chưa có hàm xử lý"): workspace đã quét bằng bản cũ có IR thiếu cạnh `HANDLED_BY`, và **"Quét lại" báo "không có thay đổi" rồi giữ IR cũ**. Đã sửa: lần quét thành công ghi phiên bản bộ phân tích vào thống kê; phiên bản đổi (hoặc lần quét cũ chưa ghi) thì RESCAN phân tích lại toàn bộ dù không file nào đổi. Lỗi lệch này không bắt được ở test cũ vì chỉ thử workspace mới; đã thêm test tích hợp cho cả hai trường hợp, và thử trên bản sao dữ liệu của người dùng.
- **API không có bước truy cập bảng dù nghiệp vụ cần DB** — hai nguyên nhân:
  1. Bộ lọc nhiễu mặc định `*#get*()` / `*#set*(*)` bỏ nhầm cả hàm **nghiệp vụ** của Service / Repository tên `getX()` (vd lấy danh sách tùy chọn, hồ sơ cá nhân…). Đã bỏ hai bộ lọc này; getter / setter của entity, DTO được bỏ ở bước dựng IR (không thành cạnh) còn hàm `getX()` của Controller / Service / Repository luôn được giữ.
  2. Repository dựng câu truy vấn ở hàm phụ và thực thi qua lớp truy vấn dùng chung nằm trong jar không có mã nguồn. Nay hàm của kiểu thư viện có tên giống lớp truy cập dữ liệu, được gọi từ lớp Repository, được nhận là điểm thực thi (mức `INFERRED`, nói rõ lý do); bảng suy ra từ mảnh chuỗi trong hàm, trong các hàm cùng lớp mà nó gọi và trong hằng chuỗi của lớp. Người dùng vẫn khai được luật `QUERY_SINK` để chắc chắn hơn (API khai luật làm ở Giai đoạn 5).
  - Trên demo: số API có cột bảng 210 → **221 / 244**; 23 API còn lại không có bước DB là các API tĩnh / cấu hình / mã giả lập hệ thống ngoài / SSO / mẫu nhập — không có truy vấn nào trong mã.
- **Cột Database ghi rõ bảng**: mỗi bảng một cột "Bảng <tên>" (truy vấn đụng nhiều bảng thì mỗi bảng một cặp bước); không xác định được bảng thì dùng cột "Database" chung. Đổi §2.2.4 của overview và CLAUDE.md §3.7 theo yêu cầu. Nhãn mũi tên chỉ còn câu lệnh (`SELECT`, `INSERT/UPDATE`).
- Hệ quả: số bước trên demo tăng vì nay thấy nhiều lời gọi nghiệp vụ hơn; 15 API vượt 70 bước (lớn nhất 158).

### 3.3. Giới hạn đã biết

- Không dùng JavaSymbolSolver (CLAUDE.md §1.2 và §4.1 vẫn ghi): dự án phân tích không có classpath nên chỉ giải được phần mã của chính dự án — mà phần đó đã có `ClassIndex`. **Cần người dùng quyết định** sửa hay giữ dòng đó ở CLAUDE.md.
- Nối chuỗi `a.b().c()` và `var` chỉ giải được khi `b` là hàm của dự án (hoặc `Optional` + `orElseThrow`…) có khai báo kiểu trả về; không ghi lời gọi có receiver là biến lambda không rõ kiểu, tham chiếu qua import tĩnh, lời gọi trong lớp ẩn danh / lớp cục bộ.
- Lớp hiện thực tạo bằng `@Bean` (không có `@Service`/`@Component`) không thấy được → không nằm trong danh sách ứng viên.
- Truy vấn ghép động: mọi mảnh chuỗi của hàm được gán cho từng điểm thực thi trong hàm đó; hằng số SQL khai ở field không được theo dõi; bảng suy ra từ các mảnh → `INFERRED`.
- Bộ nhớ đệm dò vết sống 10 phút: dò trong lúc đang quét lại có thể giữ kết quả theo IR cũ tới hết hạn.
- Chốt tay (`OverrideApplier`) đã làm và có test nhưng **chưa nối vào `TracingService`** (bảng `link_overrides` và API chốt tay thuộc Giai đoạn 5); khi nối cần nạp thêm cây con của ứng viên được chốt.
- Dò vết xuất phát từ phần tử giao diện chưa hỗ trợ (`422 TRACE_START_UNSUPPORTED`) — Giai đoạn 4–5.

### 3.4. Còn treo từ trước

- Khóa node của endpoint trùng giữa các application trong cùng Source (hiện chỉ giữ cái đầu + cảnh báo) — cần quyết định vì đổi khóa là thay đổi phá vỡ.
- Quy tắc ngoại lệ khóa `…token-path` trong `PropertyLookup` — chờ xác nhận.

---

## 4. Cách người dùng tự kiểm tra

1. Chạy Engine: `docker compose -f docker/docker-compose.yml up -d postgres` rồi `engine/mvnw.cmd -f engine/pom.xml -pl flowtracer-app spring-boot:run "-Dspring-boot.run.profiles=dev"`.
2. Tạo Workspace trỏ vào fixture và quét (hoặc dùng giao diện hiện có: Import → thư mục `fixtures/spring-flow`).
3. Lấy `nodeKey` của API: `GET /api/workspaces/{id}/trees/backend`.
4. Dò vết: `POST /api/workspaces/{id}/traces` với `{"startKey":"java:endpoint:POST /api/v1/categories"}` → 16 bước, 5 cột, một khối `alt`.
5. Xuất: `GET /api/workspaces/{id}/traces/{traceId}/export?format=plantuml` → dán vào trình xem PlantUML, so với `fixtures/golden/spring-flow.trace.post-api-v1-categories.puml`.
6. API `DELETE /api/v1/categories/{id}` có bước màu "chưa xác định" kèm cảnh báo; `PUT /api/v1/orders/{id}/confirm` có bước `INFERRED` (`@Primary`).

---

## 5. Task 3.3 — Canvas sequence diagram (Frontend)

### 5.1. Đã làm

| Thành phần (`web/src/app/…`) | Làm gì |
| :--- | :--- |
| `core/api/trace.api.ts` + kiểu sinh từ OpenAPI (`gen:api`) | D1 dò vết, D2 xuất diagram |
| `features/explorer/sequence/sequence-model.ts` | Đổi kiểu OpenAPI (mọi trường tùy chọn) sang kiểu chặt; cột chỉ được đi tới bằng bước chưa xác định → gạch chéo + dấu "?" |
| `sequence-layout.ts` | Dàn cột theo độ rộng chữ; mũi tên; khung `alt`/`opt`/`loop` **lồng nhau**, nhiều vạch `else`, thanh kích hoạt (trả về do lỗi không đóng) |
| `sequence-svg.ts` | Vẽ ra chuỗi SVG thuần theo đúng bản vẽ mẫu: người cho Client, hình trụ cho Database, tiêu đề cột lặp ở đáy, nét theo mức tin cậy, huy hiệu `?` `≈` `✓`, nhãn "trả về …", thoát ký tự trong nhãn |
| `sequence-canvas.ts` | Canvas: skeleton khi đang dò, lỗi, trạng thái rỗng; zoom (nút, Ctrl + cuộn), kéo để cuộn, "Vừa khung" (tự vừa khung khi mở, tối thiểu 85%); chú giải thu gọn được; thẻ vàng "n bước chưa xác định — Xem"; mô tả khi di chuột; bấm mũi tên để tô sáng; **Xuất** ▾: mã PlantUML (D2, có nút sao chép), ảnh SVG, ảnh PNG (chuyển ngay trên trình duyệt) |
| `explorer.page.ts` | Chọn API → dò vết (chống đua khi chọn nhanh); quét xong tự dò lại; cột phải thêm "Luồng xử lý" (số bước, số cột, tên các cột) và các cảnh báo của luồng (kể cả lý do rút gọn) |

### 5.2. Kiểm thử

| Lệnh | Kết quả |
| :--- | :--- |
| `npm --prefix web run test` | ✅ 37 test (thêm 13: dàn cột, khung lồng nhau, thanh kích hoạt, SVG, thoát ký tự, tô sáng, bước chưa xác định, lần gọi lặp lại, `TraceApi`) |
| `npm --prefix web run lint` · `build` | ✅ sạch · build được |
| Chạy giao diện thật (Chrome không đầu) trên fixture `spring-flow` | ✅ 3 luồng: 16 bước đủ khối `alt` + `else`; luồng có cột "Service Audit ?" gạch chéo và thẻ cảnh báo; xuất PlantUML hiện đúng mã; zoom hoạt động |
| Trên dự án demo (chỉ số tổng hợp) | Luồng lớn nhất 150 bước, 20 cột: vẽ xong sau ~160 ms, SVG ~4.900 × 6.200 px ở 85% |

### 5.3. Khác với bản vẽ giao diện mẫu (`UI_offline.html`)

1. **Khối `alt`** vẽ nhiều khối lồng nhau, nhiều nhánh `else`, và cả `opt` / `loop` (mẫu chỉ có một `alt`).
2. **Nhãn khối `alt` / `else` và nhãn chiều trả về lỗi là nhãn kỹ thuật** ("ngược lại", `CATEGORY_CODE_EXISTS`, `BusinessException`) thay vì lời văn "Mã đã tồn tại" / "400 Bad Request" — do Backend (xem 3.1).
3. **Bấm mũi tên chưa xác định / suy luận chưa mở popover ứng viên** (Chốt tay, Nhờ AI chọn — Giai đoạn 5 và 7), và chưa nhảy sang tab Kịch bản (Giai đoạn 6). Hiện bấm chỉ tô sáng mũi tên; mô tả khi di chuột có mức tin cậy và lý do nhưng **chưa có file:dòng** như mẫu vì bước dò vết chưa mang vị trí mã nguồn.
4. **Lần gọi lặp lại** có thêm hậu tố "· như bước n" (mẫu không có vì luồng mẫu ngắn).
5. **SVG / PNG xuất từ chính canvas** (giống mẫu); **PlantUML lấy từ Engine (D2)**; SVG do Engine sinh (D2 `format=svg`) vẫn có nhưng giao diện không dùng vì kém đẹp hơn canvas.
6. Thẻ cảnh báo chỉ đếm bước chưa xác định; các cảnh báo khác (rút gọn, đệ quy) nằm ở cột phải.
7. Cột phải chưa có tab Diễn giải / Kịch bản (Giai đoạn 6) nên chưa có đồng bộ tô sáng diagram ↔ kịch bản.
