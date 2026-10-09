# CLAUDE.md

> Cẩm nang Onboarding & quy định hành vi cho **Claude Code Agent** khi làm việc local trên dự án này.
> Agent **phải đọc và tuân thủ tuyệt đối** file này trước khi sinh, sửa hoặc xóa bất kỳ dòng code nào.

---

## 1. Nguồn ngữ cảnh gốc (Source of Truth) & Dự án

### 1.1. Thư mục Ngữ cảnh Hệ thống (`.context/`)

Mọi phạm vi, chức năng, API, thiết kế CSDL và cấu trúc mã nguồn được đặc tả trong `.context/`. Agent **bắt buộc đọc và đối chiếu** các file liên quan trước khi triển khai:

- **Tổng quan dự án:** `.context/overview.md` (bài toán, phạm vi, yêu cầu phi chức năng, kiến trúc §2.1, mô hình IR §2.2, kịch bản hoạt động §2.4, **chính sách dùng AI §2.7**).
- **Bảng chức năng:** `.context/feature_list.md` (72 chức năng, 9 nhóm, ưu tiên MVP / Nên có / Mở rộng, quy tắc xử lý từng nhóm).
- **Đặc tả API:** `.context/API_list.md` (9 nhóm A–I, ứng với 9 nhóm chức năng).
- **Thiết kế CSDL:** `.context/database.md` (ý nghĩa từng bảng, từng cột) và `docs/diagrams/database.dbml` (kiểu, khóa, ràng buộc của 12 bảng).
- **Cấu trúc mã nguồn:** `.context/project_structure.md` (engine Maven multi-module, sidecar analyzer-node, web, contracts).
- **Hợp đồng dữ liệu Engine ↔ sidecar:** `contracts/` (JSON Schema + `sidecar-protocol.md`).
- **Lộ trình:** `.context/roadmap.md`. **Nhật ký thực hiện:** `docs/progress/` (mỗi phase một file).

Nếu tài liệu mâu thuẫn nhau, hoặc yêu cầu của người dùng mâu thuẫn với tài liệu: **dừng lại và hỏi**, không tự chọn.

### 1.2. Tổng quan Dự án

| Hạng mục | Giá trị |
| --- | --- |
| **Đề tài** | CtV Flow Tracer — AI Code-to-Visual Flow Tracer (VDT 2026 — Giai đoạn 2) |
| **Vai trò** | Công cụ web chạy cục bộ: phân tích **tĩnh** mã nguồn dự án, dựng cây API Backend + cây thành phần Frontend, vẽ **sequence diagram End-to-End**, diễn giải bằng lời kèm dữ liệu mẫu |
| **Kiến trúc** | **Monolith** Spring Boot, **package-by-feature** (`Controller → Service → Repository`); lõi phân tích là **module Maven Java thuần**; **sidecar Node.js** (CLI) phân tích Angular |
| **Stack Engine** | Java 21 · Spring Boot 4.x · Maven multi-module · JavaParser + JavaSymbolSolver · Spring Data JPA + JdbcClient (batch) · Flyway · PostgreSQL 16 · Spring AI · springdoc-openapi · JUnit 5 + AssertJ + Testcontainers + ArchUnit |
| **Stack sidecar** | Node.js 22 · TypeScript · TypeScript Compiler API · `@angular/compiler` · Vitest |
| **Stack Web** | Angular 21 (standalone, signals, OnPush) · Angular Material/CDK · kiểu API sinh từ OpenAPI |
| **AI** | Gemini (mặc định) / Claude / OpenAI-compatible / none — qua Spring AI, sau interface `LlmProvider` |
| **Triển khai** | Docker Compose (`flowtracer-app` = JRE 21 + Node 22, và `postgres`), chỉ lắng nghe `127.0.0.1:8600`; mã nguồn cần phân tích gắn vào ở chế độ **read-only** |
| **Đối tượng phân tích (MVP)** | Backend Java 17+ / Spring Boot 3.x · Frontend Angular 15+ (đích chính Angular 21, Native Federation) |
| **Người dùng** | **Tool cá nhân**, một người, không đăng nhập, không phân quyền |

**Phạm vi BỊ CẤM (tuyệt đối không tự thêm):**
- Chế độ **nhiều người dùng / team server**, đăng nhập, phân quyền, SaaS.
- **AI vẽ diagram hoặc tạo node/edge.** Diagram chỉ sinh từ phân tích tĩnh.
- **Chạy, build, cài dependency** của dự án được phân tích; **kết nối CSDL** hoặc đọc dữ liệu thật của dự án đó.
- Chức năng "làm giàu dữ liệu mẫu bằng AI" riêng. Dữ liệu mẫu do AI sinh **một lần** rồi lưu cache (mục 3.7).
- DDD, CQRS, Ports & Adapters cho mọi package, hay interface cho mọi Service. Kiến trúc đã chốt là monolith đơn giản (mục 3.2).
- Tính năng ghi *Mở rộng* trong `feature_list.md` khi phase hiện tại **chưa** tới, trừ khi người dùng yêu cầu rõ.
- Bất kỳ tính năng nào không có trong tài liệu đã chốt.

---

## 2. Lệnh điều hành dự án (Build / Test / Lint / Migration)

Agent gọi đúng các lệnh sau. **Không tự bịa lệnh.**
> Repo chưa có code. Các lệnh dưới đây là **hợp đồng**, phải được tạo đúng khi làm phase khởi tạo trong roadmap. Không tự đặt tên khác.
> Trên Windows dùng `mvnw.cmd` thay cho `./mvnw`.

### 2.1. Engine (Java — thư mục `engine/`)

```bash
./mvnw -f engine/pom.xml clean verify                    # Build + toàn bộ test (unit, ArchUnit, IT với Testcontainers)
./mvnw -f engine/pom.xml test                            # Chỉ unit test
./mvnw -f engine/pom.xml -pl flowtracer-adapter-spring test   # Test riêng một module
./mvnw -f engine/pom.xml -pl flowtracer-app spring-boot:run -Dspring-boot.run.profiles=dev   # Chạy Engine khi dev (cổng 8600)
./mvnw -f engine/pom.xml spotless:apply                  # Định dạng code Java
./mvnw -f engine/pom.xml -Pgolden-update test            # Sinh lại golden snapshot — CHỈ khi thay đổi IR có chủ đích
```

### 2.2. Sidecar (`analyzer-node/`) & Web (`web/`)

```bash
npm --prefix analyzer-node ci
npm --prefix analyzer-node run gen:contracts             # Sinh kiểu TypeScript từ contracts/*.schema.json
npm --prefix analyzer-node run build                     # Biên dịch → analyzer-node/dist/cli.js
npm --prefix analyzer-node run test                      # Vitest (unit + contract + golden)
npm --prefix analyzer-node run lint

npm --prefix web ci
npm --prefix web run gen:api                             # Sinh kiểu TypeScript từ http://localhost:8600/v3/api-docs
npm --prefix web start                                   # ng serve (proxy /api → Engine)
npm --prefix web run build
npm --prefix web run test
npm --prefix web run lint
```

### 2.3. CSDL & migration (Flyway)

> Migration nằm ở `engine/flowtracer-app/src/main/resources/db/migration/`, đặt tên `V<số>__<mo_ta>.sql`. Flyway tự chạy khi Engine khởi động.

```bash
docker compose -f docker/docker-compose.yml up -d postgres   # Chạy riêng PostgreSQL khi dev
./mvnw -f engine/pom.xml -pl flowtracer-app flyway:info      # Xem trạng thái migration
```

> ⚠️ Thay đổi cấu trúc bảng thì **bắt buộc** thêm file migration mới **và** cập nhật `.context/database.md` + `docs/diagrams/database.dbml`. **Không bao giờ** sửa migration đã chạy. `spring.jpa.hibernate.ddl-auto` luôn là `validate`.

### 2.4. Docker, đo lường & kiểm tra

```bash
docker compose -f docker/docker-compose.yml up -d --build    # Chạy toàn bộ tool
docker compose -f docker/docker-compose.yml down
node scripts/benchmark.mjs                                   # Đo NFR-01 → NFR-04
node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <đường-dẫn-cục-bộ>   # Chỉ chạy cục bộ; chỉ in số liệu tổng hợp
node scripts/check-forbidden-terms.mjs                       # Chặn từ khóa nội bộ trước khi commit
```

---

## 3. Quy ước lập trình NGHIÊM NGẶT (Codestyle & Architecture)

### 3.1. Đặt tên

- **Java:** class `PascalCase`, method/biến `camelCase`, hằng số `UPPER_SNAKE_CASE`, package chữ thường dưới gốc `com.ctv.flowtracer`.
- **TypeScript/Angular:** file `kebab-case`, class `PascalCase`.
- **Hậu tố bắt buộc — nói rõ vai trò:**

| Hậu tố | Vị trí | Vai trò |
| --- | --- | --- |
| `*Controller` | flowtracer-app · `<chức năng>/controller/` | REST / SSE endpoint, mỏng |
| `*Service` | flowtracer-app · `<chức năng>/service/` | Logic nghiệp vụ; service công khai cho package khác |
| `*Repository` | flowtracer-app · `<chức năng>/repository/` | Spring Data JPA, hoặc JdbcClient cho bảng IR |
| `*Request` / `*Response` | flowtracer-app · `dto/` | DTO vào/ra (Java `record` + Bean Validation) |
| `entity/*` | flowtracer-app · `<chức năng>/entity/` | JPA `@Entity` và enum đi kèm (tên class không có hậu tố) |
| `*Rule` / `*Rules` | flowtracer-app, core | Quy tắc thuần, unit test được (vd `AiAdviceRule`, `NestedSourceRules`) |
| `*Step` | flowtracer-app · `scan/pipeline` | Một giai đoạn của pipeline quét |
| `*Extractor` / `*Resolver` / `*Builder` | adapter-spring, core, analyzer-node | Pha 1 / pha 2 / dựng kết quả của lõi phân tích |
| `*Test` / `*IT` | `src/test` | Unit test / test tích hợp (Testcontainers) |

### 3.2. Ranh giới kiến trúc (BẮT BUỘC)

**a) Giữa các module Maven:**
- Hướng phụ thuộc: `flowtracer-adapter-spring → flowtracer-ir`, `flowtracer-core → flowtracer-ir`, `flowtracer-app → cả ba`.
- `flowtracer-ir`, `flowtracer-core`, `flowtracer-adapter-spring` là **Java thuần**: **CẤM** Spring, JPA/JDBC, HTTP client, SDK AI, đọc file trực tiếp (nội dung file được truyền vào qua `SourceFile`).
- `flowtracer-core` **CẤM** phụ thuộc `flowtracer-adapter-*`. Đăng ký adapter chỉ ở `scan/adapter/AdapterRegistry.java`.
- Logic riêng của một công nghệ chỉ nằm trong adapter tương ứng (`flowtracer-adapter-spring`, `analyzer-node/src/adapters/*`).

**b) Trong `flowtracer-app` — monolith, package-by-feature:**
- Mỗi package chức năng (`workspace`, `scan`, `graph`, `linking`, `tracing`, `explanation`, `ai`, `feature`, `comparison`) có bố cục chuẩn: `controller/` · `service/` · `repository/` · `entity/` · `dto/` (+ `pipeline/`, `adapter/`, `provider/`, `model/` khi đặc thù). Class mới phải đặt đúng thư mục con; **không** để file lẻ ở gốc package chức năng.
- Phụ thuộc trong một chức năng: `controller → service → repository → entity`. **Controller mỏng:** chỉ nhận/validate request và gọi Service, **không** gọi thẳng Repository. **Repository:** chỉ truy cập dữ liệu, không gọi Service/Controller. **Entity** không phụ thuộc Service/Controller/DTO. Logic nằm ở Service.
- **Giữa các package:** chỉ gọi **service công khai** (Phụ lục B của `project_structure.md`). **CẤM** dùng Repository hay Entity của package khác.
- **Chỉ 3 interface** được phép tồn tại để thay thế: `SourceReader`, `LlmProvider`, `StackAdapter`. Không tạo interface cho Service chỉ có một hiện thực.
- Các luật trên được kiểm tra bằng `ArchitectureTest` (ArchUnit). **Không tắt hay nới luật ArchUnit** để test chạy qua.

**c) Engine ↔ sidecar:**
- Chỉ giao tiếp qua CLI theo `contracts/sidecar-protocol.md`: request JSON qua stdin, `IrFragment` qua stdout, tiến độ qua stderr.
- Engine **luôn** kiểm tra kết quả sidecar với `contracts/ir-fragment.schema.json` trước khi dùng.
- Đổi schema trong `contracts/` thì phải sửa cả record Java (`flowtracer-ir`) và chạy `gen:contracts` cho sidecar trong **cùng** thay đổi.

### 3.3. Tất định & IR

- **Cùng mã nguồn và cấu hình ⇒ cùng IR ⇒ cùng diagram.** Trong lõi phân tích (Java và sidecar) **CẤM**: `Random`/`Math.random()` không seed, thời gian hiện tại, duyệt `HashMap`/`HashSet`/danh sách file không sắp xếp ổn định khi sinh kết quả.
- Mọi node/edge phải có vị trí nguồn (file + dòng), hoặc `reason` giải thích vì sao không có.
- **Node key ổn định** (`NodeKeys.java` và bản tương ứng trong sidecar). Đổi quy tắc sinh key là **breaking change**; chỉ làm khi người dùng đồng ý.
- Dữ liệu người dùng và cache AI trỏ tới node **bằng `key`**, không bằng `ir_node.id`, và không đặt khóa ngoại tới `ir_node`.
- Thay đổi kiểu IR thì phải cập nhật `contracts/`, `flowtracer-ir`, §2.2 của `overview.md` và golden test trong **cùng** thay đổi.

### 3.4. An toàn mã nguồn & dữ liệu

- **Chỉ đọc** mã nguồn của dự án được phân tích.
  - Trong Engine: chỉ qua `SourceReader`. Mọi đường dẫn tương đối `SOURCE_ROOT`; chặn path traversal và symlink trỏ ra ngoài.
  - Sidecar: chỉ đọc đúng các file Engine liệt kê trong request.
- **Không** build, chạy, cài dependency hay thực thi file nào của dự án được phân tích. Engine chỉ chạy tiến trình con là sidecar của chính tool.
- **Không** kết nối CSDL của dự án được phân tích. Dữ liệu mẫu **chỉ** sinh từ cấu trúc DTO.
- Được gửi **mã nguồn** tới AI bên ngoài, nhưng **bắt buộc che secret** trước khi gửi (`SecretRedactor`): giá trị của khóa chứa `password`, `secret`, `token`, `key`, `credential`; chuỗi kết nối CSDL; khối private key. **Không gửi** file `.env`, keystore, file dữ liệu.
- API key **chỉ** nằm trong biến môi trường: không lưu CSDL, không trả về API, không gửi xuống trình duyệt, không ghi log. `llm_call_log` **không lưu nội dung prompt** (chỉ lưu hash).
- `ApplicationConfigReader` chỉ đọc đúng các khóa cần thiết (context-path, Jackson naming), không giữ giá trị bí mật.

### 3.5. Vệ sinh repo (BẢO MẬT — không được vi phạm)

- **TUYỆT ĐỐI KHÔNG** commit, sao chép hay trích dẫn vào repo:
  - mã nguồn, tên class/biến/endpoint, URL nội bộ hay tài liệu của **dự án demo công ty**;
  - tài liệu nội bộ Viettel (`docs/AI_Viettel/` đã gitignore).
- Repo có remote GitHub công khai. Mọi ví dụ trong code, test và tài liệu dùng **miền nghiệp vụ giả định** (ví dụ "Danh mục", "Shop").
- Fixture trong `fixtures/` phải **tự viết 100%**. Được mô phỏng *đặc điểm kỹ thuật* của dự án demo (Phụ lục B của overview), không được sao chép code.
- Đọc mã nguồn dự án demo trên máy để kiểm tra độ tương thích thì được, nhưng mọi kết quả ghi vào repo chỉ là **số liệu tổng hợp** hoặc mô tả đã ẩn định danh.

### 3.6. Quy tắc sử dụng AI

- Chỉ gọi AI qua `AiGateway` (package `ai`). **CẤM** package khác dùng Spring AI hay gọi provider trực tiếp. ArchUnit kiểm tra điều này.
- Chỉ dùng AI cho các mục đích trong enum `AiPurpose`: `PURPOSE_TEXT`, `SCENARIO_POLISH`, `MOCK_DATA`, `EDGE_RESOLUTION` (MVP); `FEATURE_SUMMARY`, `LEGACY_MATCH` (mở rộng). Thêm mục đích mới phải được người dùng đồng ý và cập nhật §2.7 của overview trước.
- Prompt nằm trong `resources/prompts/<purpose>.v<n>.st` kèm schema đầu ra. Đổi nội dung prompt thì **tăng version** (version nằm trong khóa cache).
- Đầu ra AI **luôn được kiểm tra** trước khi dùng. Không đạt thì dùng kết quả tất định.
- **Mọi tính năng phải chạy được khi `flowtracer.ai.provider=none`.** Test tích hợp chạy với AI tắt.

### 3.7. Ràng buộc nghiệp vụ đặc biệt (CỐT LÕI — không được vi phạm)

- **AI không bao giờ đổi liên kết:** "Nhờ AI phân tích" chỉ trả lời giải thích cho từng ứng viên. Ứng viên AI đề xuất thêm chỉ được hiện khi là node **có thật** trong IR và **hợp lệ về kiểu** (`AiAdviceRule`). AI **không bao giờ** tạo node/edge hay đổi `confidence`; chỉ người dùng chốt tay mới đổi diagram.
- **Liên kết chốt tay luôn thắng:** `PIN` thay đích, `REJECT` bỏ cạnh. Áp dụng trước khi dò vết và giữ qua mọi lần quét lại.
- **Mức tin cậy:** `CERTAIN` chỉ khi tham chiếu rõ ràng và duy nhất. So khớp có ký tự đại diện hoặc phải áp luật heuristic → `INFERRED`. Không có ứng viên hoặc nhiều ứng viên ngang điểm → `UNRESOLVED`, lưu `candidates`, chưa kết luận. Mức khớp ứng viên hiển thị Cao / Trung bình / Thấp, không hiển thị %.
- **Mục đích (tab Diễn giải):** cache → AI viết 80–100 từ (kèm câu ràng buộc gọn, mỗi câu phải trỏ về một ràng buộc thô có thật) → AI lỗi/tắt thì `@Operation` → Javadoc → tài liệu đặc tả → câu khung. Thành phần chứa chỉ có tên, loại, mô tả ngắn.
- **Diagram:** một mức hiển thị. Cột theo §2.2.4 của overview (Frontend gộp 1 cột; mỗi Controller/Service/Repository 1 cột; Database 1 cột). Nhãn mũi tên ngắn, kỹ thuật, tất định (tên API, tên hàm, thao tác dữ liệu).
- **Dữ liệu mẫu:**
  - AI sinh **một lần** từ JSON Schema của DTO (không có dữ liệu thật).
  - Kết quả phải **khớp schema** rồi mới lưu cache; lần sau dùng lại cho tới khi DTO đổi.
  - AI tắt hoặc lỗi thì dùng `FallbackMockGenerator`.
- **Kịch bản:**
  - Gồm "Các actor tham gia" và "Bước n: …"; khung câu sinh tất định từ trace, bước n khớp đúng mũi tên số n.
  - AI viết lời kể phải **giữ nguyên số bước, thứ tự và thành phần** (`ScenarioPolishRule`). Lệch thì dùng khung.
  - Lời văn ngắn gọn, dễ hiểu; không có công tắc "chi tiết kỹ thuật" (chi tiết nằm ở nhãn diagram và popup Xem code).
- **Quét:**
  - Mỗi Workspace chỉ có **một** lần quét chạy tại một thời điểm.
  - File lỗi không làm dừng lần quét (→ `PARTIAL`).
  - Hủy hoặc thất bại thì **giữ nguyên IR** của lần quét thành công trước đó (ghi IR trong một transaction).
- **Xóa Workspace:** chặn khi đang quét; xóa dữ liệu trong CSDL và cache dữ kiện, **không bao giờ đụng tới mã nguồn**.

### 3.8. Ngôn ngữ, commit & tài liệu

- **Giao diện, nội dung diễn giải, tài liệu:** tiếng Việt có dấu. **Tên trong code, commit message:** tiếng Anh, theo Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Không tự `git commit` / `git push`** khi người dùng chưa yêu cầu.
- Thay đổi phạm vi, API, CSDL hay cấu trúc thì cập nhật file tương ứng trong `.context/` **trong cùng thay đổi**.
- **Nhật ký tiến độ:**
  - Khi người dùng yêu cầu làm một giai đoạn của `roadmap.md`, xong thì **tạo một file mới** `docs/progress/<ddmm>_gd<N>_<chu-de>.md` (ví dụ `1210_gd1_workspace_import.md`): việc đã làm theo từng task, kết quả kiểm thử, việc còn dở, vấn đề gặp phải. Một giai đoạn một file; không dồn nhiều giai đoạn vào một file.
  - Việc ngoài giai đoạn (chỉnh giao diện, sửa tài liệu…): ghi vào file theo ngày `docs/progress/<ddmm>_<chu-de>.md`, mỗi ngày một file.

---

## 4. Bản đồ thành phần hệ thống (Maps)

### 4.1. Các khối & module

| Khối / Module | Trách nhiệm |
| --- | --- |
| `contracts/` | JSON Schema dùng chung Engine ↔ sidecar (IrFragment, request, tiến độ) |
| `engine/flowtracer-ir` | Kiểu IR (record), SPI `StackAdapter` (detect / extract / resolve), quy tắc node key |
| `engine/flowtracer-core` | Lọc file, nhận diện, **Linker**, **Tracer**, diagram (view model, PlantUML, SVG), khung kịch bản, đầu vào/đầu ra/ràng buộc, JSON Schema, kiểm tra & sinh dữ liệu mẫu dự phòng |
| `engine/flowtracer-adapter-spring` | Phân tích Java/Spring Boot (JavaParser + SymbolSolver) |
| `engine/flowtracer-app` | Ứng dụng Spring Boot: REST/SSE, CSDL, pipeline quét, AI |
| `analyzer-node` | Sidecar CLI: adapter Angular (MVP), PHP-legacy (mở rộng) |
| `web` | Giao diện Angular 3 cột |

### 4.2. Package chức năng trong `flowtracer-app`

| Package | Trách nhiệm | Bảng sở hữu | Nhóm chức năng · API |
| --- | --- | --- | --- |
| `source` | `SourceReader` — điểm duy nhất đọc mã nguồn người dùng | — | (nền tảng) |
| `workspace` | Workspace, Source (lồng nhau), nhận diện công nghệ, duyệt thư mục, xuất/nhập, bộ lọc nhiễu | `workspace`, `source` | 1 · A, I4–I5 |
| `scan` | Pipeline DISCOVER → DIFF → EXTRACT → RESOLVE → PERSIST → LINK → FINALIZE; AdapterRegistry; gọi sidecar; cache dữ kiện; SSE tiến độ | `scan_run`, `source_file` | 2 · B |
| `graph` | **Chủ sở hữu IR** (JdbcClient batch), cây BE/FE, tìm kiếm, chi tiết node, đoạn code, đối chiếu OpenAPI | `ir_node`, `ir_edge` | 3 · C |
| `linking` | Nối FE ↔ BE (và BE ↔ BE, hệ thống ngoài), liên kết chốt tay, luật liên kết, AI phân tích liên kết, xác minh runtime (mở rộng) | `link_override` | 5 · E |
| `tracing` | Dò vết (không lưu CSDL, cache theo `trace_id`), xuất diagram, flowchart (mở rộng) | — | 4 · D |
| `explanation` | Tab Diễn giải, dữ liệu mẫu, tab Kịch bản, sinh lại nội dung AI | — | 6 · F |
| `ai` | **Cổng AI duy nhất**: bật/tắt mục đích → cache → hạn mức → che secret → provider → kiểm tra → lưu & log | `ai_artifact`, `llm_call_log`, `app_setting` | 9 · I1–I3, I6 |
| `feature` *(mở rộng)* | Nhóm chức năng | `feature`, `feature_member` | 7 · G |
| `comparison` *(mở rộng)* | So sánh & đối chiếu cũ – mới | — | 8 · H |

Health (API I7) dùng Spring Boot Actuator, phục vụ tại `/api/health`.

### 4.3. Ba interface thay thế được

| Interface | Hiện thực | Chọn bằng |
| --- | --- | --- |
| `SourceReader` | `LocalSourceReader` | cố định |
| `LlmProvider` | `SpringAiLlmProvider` (Gemini mặc định / Anthropic / OpenAI-compatible), `NoopLlmProvider` | `flowtracer.ai.provider` |
| `StackAdapter` | `SpringStackAdapter` (JavaParser), `SidecarStackAdapter("angular")`, `SidecarStackAdapter("php-legacy")` *(mở rộng)* | `AdapterRegistry` + nhận diện công nghệ |

---

## 5. Checklist trước khi Agent kết thúc một tác vụ

- [ ] Đã đọc tài liệu `.context/` liên quan; tính năng thuộc phase hiện tại và không nằm trong phạm vi bị cấm (mục 1.2).
- [ ] Đặt tên đúng quy ước (mục 3.1).
- [ ] Không vi phạm ranh giới: lõi Java thuần; Controller mỏng; không dùng Repository/Entity của package khác; không thêm interface thừa; `ArchitectureTest` chạy qua (mục 3.2).
- [ ] Lõi phân tích tất định; node/edge có vị trí nguồn hoặc `reason`; không đổi quy tắc node key (mục 3.3).
- [ ] Chỉ đọc mã nguồn qua `SourceReader` (sidecar: chỉ file được cấp); có che secret trước khi gọi AI; không lộ API key (mục 3.4).
- [ ] Không có code, tên định danh hay URL của dự án công ty trong thay đổi; đã chạy `check-forbidden-terms` (mục 3.5).
- [ ] AI chỉ qua `AiGateway`, đúng mục đích, có kiểm tra đầu ra; tính năng chạy được khi `provider=none` (mục 3.6).
- [ ] Tuân thủ ràng buộc cốt lõi: AI chỉ chọn trong ứng viên, liên kết chốt tay thắng, dữ liệu mẫu sinh một lần, kịch bản giữ nguyên số bước (mục 3.7).
- [ ] Đổi cấu trúc bảng → đã thêm migration Flyway mới và cập nhật `database.md` + `database.dbml` (mục 2.3).
- [ ] Đổi IR/contracts → đã cập nhật `contracts/`, `flowtracer-ir`, sidecar (`gen:contracts`), §2.2 overview và golden test (mục 3.2c, 3.3).
- [ ] Đã chạy build + test liên quan: `./mvnw … verify` (Engine), `npm --prefix analyzer-node run test` (sidecar), `npm --prefix web run test` (Web).
- [ ] Đã cập nhật `.context/` (nếu đổi phạm vi/API/CSDL/cấu trúc) và nhật ký `docs/progress/` của phase hiện tại (mục 3.8).
