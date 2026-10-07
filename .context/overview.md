# CtV Flow Tracer — Tài liệu tổng quan dự án

> **Đề tài:** AI Code-to-Visual Flow Tracer — Công cụ phân tích mã nguồn tĩnh và trực quan hóa luồng xử lý End-to-End, phục vụ onboarding, phát triển và chuyển đổi (migration) phần mềm
> **Chương trình:** Viettel Digital Talent 2026 — Giai đoạn 2
> **Tác giả:** Đỗ Trung Quân
> **Phiên bản tài liệu:** 1.1 — 30/09/2026
> **Trạng thái dự án:** Đã chốt phạm vi và kiến trúc, chưa bắt đầu viết code

---

## Cách đọc tài liệu này

Tài liệu này là **nguồn sự thật (source of truth)** về bài toán, phạm vi và kiến trúc của dự án. Nó được viết để một người mới hoặc một AI agent mới đọc xong là hiểu dự án và sẵn sàng đóng góp. Chi tiết từng phần nằm ở các tài liệu đi kèm:

| Tài liệu                                                      | Nội dung                                                                                                                |
| :--------------------------------------------------------------| :------------------------------------------------------------------------------------------------------------------------|
| [overview.md](overview.md) *(tài liệu này)*                   | Bài toán, phạm vi, yêu cầu phi chức năng, kiến trúc, mô hình dữ liệu trung gian, kịch bản hoạt động, chính sách dùng AI |
| [feature_list.md](feature_list.md)                            | Danh sách **chức năng** chia theo nhóm mục đích, kèm mức ưu tiên và quy tắc xử lý                                       |
| [API_list.md](API_list.md)                                    | Danh sách **API nội bộ** (Engine ↔ Web UI), chia nhóm tương ứng với nhóm chức năng                                      |
| [database.md](database.md)                                    | Thiết kế **cơ sở dữ liệu**: mô tả từng bảng và từng cột                                                                 |
| [docs/diagrams/database.dbml](../docs/diagrams/database.dbml) | **Sơ đồ CSDL** dạng DBML (xem trên dbdiagram.io)                                                                        |
| [project_structure.md](project_structure.md)                  | Cấu trúc thư mục mã nguồn của dự án, ý nghĩa từng folder/file                                                           |
| [roadmap.md](roadmap.md) *(sẽ bổ sung)*                       | Lộ trình theo từng phase                                                                                                |
| [CLAUDE.md](../CLAUDE.md)                                     | Quy tắc bắt buộc khi AI agent (Claude Code) đóng góp vào dự án                                                          |
| `docs/progress/` *(sẽ bổ sung)*                               | Nhật ký công việc đã làm, mỗi phase trong roadmap một file                                                              |

| Bạn là… | Nên đọc theo thứ tự |
| :--- | :--- |
| Người mới muốn hiểu đề tài | §0 → §1.1 → §1.2 → §2.1 → §2.4 → Phụ lục A |
| Dev chuẩn bị viết code | §0 → §2.1 → §2.2 → feature_list.md → API_list.md → database.md |
| AI agent | `CLAUDE.md` → §0 → §2.1 → §2.2 → §2.7 → tài liệu chi tiết của phần đang làm |
| Người review / mentor | §0 → §1.2 → §1.3 → §1.4 → roadmap.md |

---

## 0. Tóm tắt nhanh (TL;DR)

- **Vấn đề:** Muốn hiểu một luồng nghiệp vụ (bấm nút này thì chuyện gì xảy ra, đi qua những lớp nào, ghi vào bảng nào), dev phải lần qua hàng chục file ở hai repo FE và BE. Tài liệu thường thiếu hoặc lỗi thời. Dự án migrate từ hệ thống cũ còn khó hơn vì phải đối chiếu logic cũ và mới.
- **Giải pháp:** Một **Web UI** kết nối vào mã nguồn dự án. Tool **phân tích tĩnh** (đọc code, không chạy code) để dựng **cây API Backend**, **cây thành phần Frontend** và **sequence diagram End-to-End** cho từng endpoint hoặc từng nút bấm. Tool cũng sinh **phần diễn giải dễ hiểu** kèm **dữ liệu mẫu**.
- **Giao diện 3 cột:** trái là cây BE/FE, giữa là canvas diagram, phải là panel 2 tab "Diễn giải" và "Kịch bản".
- **Lõi (MVP):** Java Spring Boot + Angular. **Mở rộng:** PHP (hệ thống cũ) dùng cho split view so sánh cũ và mới, Flowchart, nhóm chức năng, xác minh runtime.
- **Nguyên tắc vàng:** diagram luôn do **phân tích tĩnh sinh ra một cách tất định**. **AI chỉ dùng khi bắt buộc**: diễn giải mục đích, làm mượt câu kịch bản, chọn giữa các ứng viên khi phân tích tĩnh không chắc chắn.
- **Kiến trúc:** `Analysis Engine` (Java 21 + Spring Boot) + `Node Analyzer` (sidecar Node.js để phân tích Angular) + `Web UI` (Angular) + `PostgreSQL`, đóng gói bằng Docker Compose. Đây là **tool cá nhân**: mỗi người tự chạy trên máy của mình.
- **AI:** qua lớp Provider trừu tượng. Mặc định dùng **Gemini**, có thể đổi sang Claude, API tương thích OpenAI, hoặc tắt hẳn AI.

---

# 1. Pha phân tích yêu cầu

## 1.1 Tổng quan hệ thống

### 1.1.1 Bối cảnh và vấn đề

Trong phát triển phần mềm doanh nghiệp, **onboarding** (tiếp nhận dự án mới, tiếp quản module lạ) là giai đoạn tốn thời gian nhất. Hệ thống hiện đại thường tách rời hoàn toàn: Frontend Angular ở một repo, Backend Spring Boot ở repo khác. Để hiểu **một** luồng nghiệp vụ End-to-End, lập trình viên phải:

1. Tìm nút bấm trong HTML template, lần sang hàm xử lý trong component TypeScript.
2. Đi qua facade/service/repository phía FE để tìm lời gọi HTTP và URL thật (thường ghép từ hằng số, cấu hình môi trường, luật proxy).
3. Sang repo BE, tìm Controller khớp URL, rồi lần qua Service, Repository, câu truy vấn, Entity và bảng dữ liệu.
4. Tự ghép tất cả trong đầu hoặc trên giấy.

Việc này tạo **gánh nặng nhận thức lớn**, dễ sót nhánh xử lý, và phải lặp lại với mỗi người mới. Trong các **dự án chuyển đổi** (ví dụ từ hệ thống PHP cũ sang Angular + Spring Boot), còn thêm một câu hỏi khó: *luồng mới đã giữ đủ logic của luồng cũ chưa?*

### 1.1.2 Giải pháp và giá trị mang lại

**CtV Flow Tracer** là một công cụ web chạy cục bộ. Nó đọc mã nguồn dự án và:

- **Dựng bản đồ**: danh sách API theo module (tương tự Swagger) và danh sách thành phần tương tác trên FE theo màn hình, tab, form.
- **Vẽ luồng End-to-End**: người dùng chọn một endpoint hoặc một nút, tool vẽ ngay sequence diagram từ giao diện xuống tới bảng dữ liệu.
- **Giải thích bằng lời**: mục đích, đầu vào, đầu ra, ràng buộc, kịch bản từng bước, và dữ liệu mẫu sinh từ DTO/Entity trong code.
- **Minh bạch độ tin cậy**: mọi mũi tên trên diagram có nhãn cho biết tool *chắc chắn* hay *suy luận*, và mọi node đều trỏ được về `file:dòng` trong code.

Giá trị: rút ngắn thời gian onboarding, giảm sai sót khi sửa code (biết một nút bấm tác động tới dữ liệu nào), hỗ trợ đối soát nghiệp vụ khi migrate, và tạo tài liệu kiến trúc "sống" (luôn khớp với code hiện tại).

### 1.1.3 Phạm vi

| Hạng mục | Trong phạm vi MVP | Mở rộng (làm sau MVP) | Ngoài phạm vi |
| :--- | :--- | :--- | :--- |
| Backend | Java 17+, Spring Boot 3.x (Spring MVC REST, Spring Data JPA, JPQL/SQL viết tay) | PHP (framework tự viết của hệ thống cũ) cho so sánh cũ/mới; ZK; NestJS; Laravel… | Ngôn ngữ không có bộ phân tích |
| Frontend | Angular 15+ (đích chính: Angular 21, standalone components, Native Federation microfrontend) | React, Vue… | — |
| Diagram | Sequence diagram | Flowchart (luồng rẽ nhánh bên trong logic) | — |
| Nguồn code | Thư mục trên máy (khai báo qua Web UI) | Upload `.zip`, Git URL, CLI cho CI | — |
| Người dùng | Một người, trên máy cá nhân (Docker Compose) | — | Nhiều người dùng chung một hệ thống; dịch vụ SaaS |
| Dữ liệu thật | **Không bao giờ** truy cập DB hay dữ liệu thật của dự án được phân tích | — | — |

**Dự án demo:** một dự án nội bộ có thật, gồm BE Spring Boot, FE Angular microfrontend, và mã PHP của hệ thống cũ nằm bên trong repo BE. Mô tả chi tiết (đã ẩn định danh) ở **Phụ lục B**. Mã nguồn dự án demo **không bao giờ được đưa vào repo này**.

### 1.1.4 Thuật ngữ

| Thuật ngữ | Nghĩa trong dự án |
| :--- | :--- |
| **Workspace** | Một "dự án" trong tool: tập hợp nhiều Source được phân tích cùng nhau (ví dụ repo BE, repo FE và phần mã cũ). |
| **Source** | Một thư mục mã nguồn có **vai trò** (`BACKEND`, `FRONTEND`, `LEGACY`), kèm luật include/exclude. Source khai báo theo **thư mục**, không theo repo, và có thể lồng nhau. |
| **Stack detection** | Bước tự nhận diện công nghệ của Source (qua `pom.xml`, `angular.json`, …) để chọn Adapter phù hợp. |
| **Adapter** | Bộ phân tích cho một công nghệ cụ thể (Spring, Angular, PHP-legacy…), dạng plugin. Đầu ra của Adapter là IR. |
| **IR (Intermediate Representation)** | Mô hình dữ liệu trung gian dạng **graph** (Node + Edge) chung cho mọi ngôn ngữ. Đây là "bản đồ" của Workspace. |
| **Node / Edge** | Node là một thành phần (nút bấm, method, endpoint, bảng…). Edge là quan hệ giữa hai node (kích hoạt, gọi, gửi HTTP, đọc/ghi…). |
| **Node key** | Định danh **ổn định** của node qua các lần quét (ví dụ `java:method:com.acme.CategoryService#create(CategoryReq)`). Dữ liệu người dùng luôn tham chiếu bằng key, không bằng id. |
| **Linker** | Bước nối lời gọi HTTP ở FE với endpoint ở BE bằng cách chuẩn hóa và so khớp URL. |
| **Tracer** | Thuật toán đi trên graph từ một node xuất phát để thu được chuỗi bước End-to-End. |
| **Trace** | Kết quả của Tracer: danh sách participant và các bước có thứ tự. Trace là đầu vào để vẽ diagram và sinh kịch bản. |
| **Confidence (mức tin cậy)** | Nhãn trên mỗi Edge: `CERTAIN`, `INFERRED`, `AI_RESOLVED`, `UNRESOLVED` (và `RUNTIME_VERIFIED` ở bản mở rộng). |
| **Link override** | Quyết định chốt tay của người dùng cho một liên kết (ghim hoặc loại bỏ). Được lưu lại và áp dụng cho các lần quét sau. |
| **Feature (Nhóm chức năng)** | *(Mở rộng)* Nhóm các endpoint/thành phần liên quan, được lưu lại để xem diagram và diễn giải ở cấp chức năng. |
| **Scenario (Kịch bản)** | Diễn giải bằng lời từng bước của sequence diagram theo mẫu "A làm B tới C". |

## 1.2 Yêu cầu chức năng

Toàn bộ chức năng (77 chức năng, 9 nhóm) được mô tả chi tiết trong **[feature_list.md](feature_list.md)**: tên, mức ưu tiên, input, output, mô tả, cùng quy tắc xử lý của từng nhóm. API tương ứng nằm trong **[API_list.md](API_list.md)**.

Tổng quan các nhóm:

| Nhóm | Mục đích | MVP | Nên có | Mở rộng |
| :--- | :--- | :---: | :---: | :---: |
| 1. Quản lý Workspace & nguồn mã | Khai báo dự án và các thư mục mã nguồn cần phân tích | 9 | 2 | 3 |
| 2. Quét & phân tích mã nguồn | Đọc code, dựng "bản đồ" thành phần, quét lại khi code đổi | 8 | 0 | 4 |
| 3. Khám phá cấu trúc dự án | Xem cây API Backend, cây thành phần Frontend, tìm kiếm, xem code | 4 | 2 | 0 |
| 4. Dò vết & trực quan hóa luồng | Dò luồng End-to-End và vẽ sequence diagram tương tác | 6 | 1 | 1 |
| 5. Liên kết Frontend – Backend & độ tin cậy | Nối FE với BE, thể hiện mức tin cậy, chốt tay, xác minh runtime | 6 | 0 | 2 |
| 6. Diễn giải & dữ liệu mẫu | Panel phải: tab Diễn giải và tab Kịch bản | 11 | 0 | 0 |
| 7. Nhóm chức năng (Feature) | Gom nhiều thành phần thành một chức năng để xem tổng thể | 0 | 0 | 8 |
| 8. So sánh & đối chiếu hệ thống cũ – mới | Split view, ghép luồng cũ/mới, checklist đối soát khi migrate | 0 | 0 | 5 |
| 9. Cấu hình AI & hệ thống | Cấu hình AI, bộ lọc nhiễu, theo dõi sử dụng AI | 4 | 1 | 0 |
| **Tổng** | | **48** | **6** | **23** |

## 1.3 Yêu cầu phi chức năng

| ID | Nhóm | Yêu cầu | Chỉ tiêu |
| :--- | :--- | :--- | :--- |
| NFR-01 | Hiệu năng | Quét lần đầu một Workspace khoảng 2.000 file nguồn | ≤ 3 phút |
| NFR-02 | Hiệu năng | Quét lại khi ≤ 20 file thay đổi | ≤ 15 giây |
| NFR-03 | Hiệu năng | Tải cây BE/FE; tìm kiếm | ≤ 1 giây |
| NFR-04 | Hiệu năng | Dò vết và vẽ trace ≤ 50 bước (không tính AI) | ≤ 2 giây |
| NFR-05 | Hiệu năng | Diễn giải AI lần đầu / đã cache | Chữ đầu tiên ≤ 5 giây, hoàn tất ≤ 20 giây (có streaming) / ≤ 1 giây |
| NFR-06 | Tài nguyên | RAM của Engine khi quét Workspace khoảng 2.000 file | ≤ 2 GB |
| NFR-07 | Độ chính xác | Phát hiện endpoint so với OpenAPI | ≥ 98% (mục tiêu 100%) |
| NFR-08 | Độ chính xác | Liên kết FE → BE so với ground truth | Precision ≥ 95%, recall ≥ 85% |
| NFR-09 | Độ chính xác | Trace đầy đủ, đúng tới bảng dữ liệu (mẫu 30 luồng) | ≥ 90% |
| NFR-10 | Tính tất định | Cùng mã nguồn và cấu hình thì luôn cho cùng IR và cùng diagram | Bắt buộc (có golden test) |
| NFR-11 | Bảo mật | Chỉ **đọc** mã nguồn; không build, không chạy, không cài dependency của dự án được phân tích | Bắt buộc |
| NFR-12 | Bảo mật dữ liệu | Không kết nối DB và không đọc dữ liệu thật của dự án. Dữ liệu mẫu chỉ sinh từ DTO | Bắt buộc |
| NFR-13 | Bảo mật AI | Được gửi **mã nguồn** tới AI bên ngoài (Gemini, Claude…) theo thực tế team đang dùng. **Trước khi gửi phải che secret**: giá trị của khóa chứa `password`, `secret`, `token`, `key`, `credential` trong `application*.yml`/`.properties`/`.env`, chuỗi kết nối DB, private key. Không gửi file `.env`, keystore, file dữ liệu | Bắt buộc |
| NFR-14 | Bảo mật AI | Chỉ dùng **API chính thức** (trả phí hoặc gói doanh nghiệp) có cam kết không dùng dữ liệu để huấn luyện; không dùng giao diện chat miễn phí | Bắt buộc |
| NFR-15 | Bảo mật | API key chỉ nằm trong biến môi trường của Engine, không lưu vào DB, không gửi xuống trình duyệt, không ghi log | Bắt buộc |
| NFR-16 | Bảo mật | Mặc định Engine chỉ lắng nghe `127.0.0.1`; trình duyệt thư mục bị giới hạn trong `SOURCE_ROOT` và chặn path traversal | Bắt buộc |
| NFR-17 | Kiểm toán | Ghi log mỗi lần gọi AI: mục đích, provider, model, số token, độ trễ, trạng thái. **Không lưu nội dung prompt** mà chỉ lưu hash | Bắt buộc |
| NFR-18 | Độ tin cậy | Tool vẫn hoạt động đầy đủ, trừ phần diễn giải AI, khi AI tắt, hết quota hoặc mất mạng | Bắt buộc |
| NFR-19 | Độ tin cậy | File lỗi cú pháp không làm hỏng lần quét (trạng thái `PARTIAL` kèm danh sách lỗi) | Bắt buộc |
| NFR-20 | Chi phí AI | Mỗi node gọi AI tối đa một lần cho mỗi phiên bản code (cache theo hash). Giới hạn token cho mỗi lời gọi; có hạn mức ngày cấu hình được | Bắt buộc |
| NFR-21 | Mở rộng | Thêm công nghệ mới bằng cách viết Adapter mới, **không sửa phần lõi** | Kiến trúc plugin |
| NFR-22 | Khả dụng | Giao diện và nội dung diễn giải bằng tiếng Việt (tên định danh trong code giữ nguyên). Hỗ trợ trình duyệt Chrome/Edge/Firefox bản mới | — |
| NFR-23 | Triển khai | Khởi chạy bằng một lệnh `docker compose up`, cấu hình qua `.env`. Chạy được trên Windows (Docker Desktop) và Linux | — |
| NFR-24 | Bảo trì | Unit test cho từng Adapter, golden test cho IR của các fixture, CI chạy lint và test mỗi PR. Độ phủ test của phần lõi ≥ 70% | — |

## 1.4 Tiêu chí hoàn thành MVP (Definition of Done)

MVP được coi là hoàn thành khi đáp ứng đủ các điều kiện sau:

1. `docker compose up` trên một máy sạch, rồi tạo Workspace, khai báo Source BE + FE của dự án demo và quét thành công qua Web UI.
2. Cây BE và cây FE hiển thị đầy đủ, đạt NFR-07.
3. Chọn bất kỳ nút nào có gọi API: sequence diagram hiển thị luồng từ Người dùng tới Bảng dữ liệu; tab Diễn giải và tab Kịch bản đầy đủ theo Nhóm 6 trong [feature_list.md](feature_list.md).
4. Các chỉ tiêu NFR-01 → NFR-09 được đo và ghi kết quả vào `docs/evaluation.md` (chỉ ghi số liệu tổng hợp, không ghi code của dự án demo).
5. Tắt AI (`LLM_PROVIDER=none`) thì mọi chức năng trừ phần do AI sinh vẫn hoạt động.
6. Golden test và unit test chạy xanh trên CI.

## 1.5 Phạm vi người dùng

Đây là **tool cá nhân**: mỗi người tự chạy tool trên máy mình bằng Docker Compose. Không có đăng nhập hay phân quyền; người chạy tool có toàn quyền trên các Workspace của mình. Muốn chuyển cấu hình sang máy khác thì dùng chức năng xuất/nhập Workspace (file JSON).

| Chân dung | Nhu cầu chính | Chức năng hay dùng |
| :--- | :--- | :--- |
| **Dev mới onboard** | Nắm nhanh bức tranh tổng thể và các luồng chính | Cây BE/FE, sequence, tab Kịch bản |
| **Dev Frontend** | Biết nút bấm gọi API nào, gửi gì và nhận gì | Dò vết từ FE, Đầu vào/Đầu ra JSON, dữ liệu mẫu |
| **Dev Backend / Tech lead** | Hiểu logic và tác động dữ liệu của API trước khi sửa; biết FE nào đang phụ thuộc | Dò vết từ endpoint, "Được gọi từ", Ràng buộc |
| **BA / QA** | Hiểu nghiệp vụ và các nhánh lỗi để viết test case | Tab Diễn giải, Kịch bản, nhánh lỗi trên diagram |
| **Team migration** | Đối chiếu luồng cũ (PHP) và luồng mới | Split view, checklist đối soát (Nhóm 8, mở rộng) |

---

# 2. Pha thiết kế hệ thống

## 2.1 Thiết kế kiến trúc

### 2.1.1 Tổng quan

Hệ thống gồm ba thành phần chạy trong một container, cộng một CSDL, đóng gói bằng Docker Compose:

- **Analysis Engine** ("người đọc code") — **Java 21 + Spring Boot**: đọc mã nguồn, dựng graph IR, lưu vào DB và phục vụ API cho UI. Tự phân tích mã **Java/Spring** bằng JavaParser.
- **Node Analyzer** (tiến trình phụ — *sidecar*) — **Node.js + TypeScript**: phân tích các công nghệ thuộc hệ JavaScript/TypeScript (**Angular**; về sau là PHP, React…). Engine gọi nó qua dòng lệnh khi quét và nhận về IR dạng JSON.
- **Web UI** ("người xem bản đồ") — **Angular**: hiển thị cây, diagram và panel diễn giải; mọi dữ liệu lấy qua API của Engine.

**Mô hình kiến trúc: Monolith, tổ chức theo chức năng (package-by-feature), với lõi phân tích tách thành module thư viện riêng.**
- **Monolith đơn giản:** Engine là **một** ứng dụng Spring Boot. Mã nguồn chia package theo nhóm chức năng (workspace, scan, graph, linking, tracing, explanation, ai…). Mỗi package theo mẫu quen thuộc `Controller → Service → Repository`. Không áp dụng DDD hay 4 lớp.
- **Interface chỉ đặt ở nơi cần thay thế được:**
  - `LlmProvider`: đổi Gemini / Claude / tắt AI;
  - `SourceReader`: điểm duy nhất đọc mã nguồn người dùng, để kiểm soát an toàn;
  - `StackAdapter`: mỗi công nghệ một bộ phân tích.
- **Lõi phân tích là module Maven riêng, Java thuần (không Spring):** `flowtracer-ir` (kiểu dữ liệu), `flowtracer-core` (Linker, Tracer, diagram, kịch bản, dữ liệu mẫu tất định), `flowtracer-adapter-spring`. Nhờ tách riêng, lõi dễ viết unit test và golden test, và dùng lại được cho CLI (mở rộng).
- **Vì sao có sidecar Node.js:** bộ biên dịch TypeScript và `@angular/compiler` (cách duy nhất phân tích Angular chính xác) chỉ chạy trên Node.js. Sidecar là CLI không trạng thái, giao tiếp bằng JSON theo một schema chung (`contracts/`). Nó không phải một service chạy thường trực.
- **Không chọn microservices:** tool chạy cho một người trên máy cá nhân.

Cấu trúc thư mục chi tiết: [project_structure.md](project_structure.md).

```
  Người dùng (trình duyệt)
          │  HTTP/JSON + SSE (chỉ localhost)
          ▼
┌─ Container: flowtracer-app ─────────────────────────────────────────────
│
│  [Web UI – Angular]  (file tĩnh, Engine phục vụ cùng cổng)
│     cây BE/FE · canvas diagram · panel Diễn giải / Kịch bản
│          │
│          ▼
│  [Analysis Engine – Java 21 + Spring Boot]
│     REST API + SSE
│      ├─ Scan Service ──► Stack Detector
│      │     ├──► Spring Adapter (JavaParser, trong Engine)
│      │     └──► Sidecar Adapter ──(gọi dòng lệnh, JSON)──► [Node Analyzer – Node.js + TypeScript]
│      │                                                        Angular │ PHP-legacy (mở rộng)
│      ├─ Linker ................ nối lời gọi API ở FE với endpoint ở BE
│      ├─ Tracer ──► Diagram Builder ....... dò vết, dựng sequence diagram
│      └─ Explanation Service ──► AI Gateway .. diễn giải, kịch bản, dữ liệu mẫu
│
└───────┬────────────────────────┬────────────────────────┬───────────────
        │ chỉ đọc                │ đọc/ghi                │ HTTPS (khi bật AI)
        ▼                        ▼                        ▼
 [Thư mục mã nguồn]        [PostgreSQL]             [AI Provider]
  SOURCE_ROOT (read-only)   container riêng          Gemini (mặc định) / Claude /
                                                     OpenAI-compatible / tắt AI
```

### 2.1.2 Mô tả các thành phần

1. **Web UI**
   - *Công nghệ:* Angular 21 (standalone components, signals), Angular Material + CDK, renderer sequence bằng SVG tự viết. Kiểu dữ liệu API sinh tự động từ OpenAPI của Engine.
   - *Nhiệm vụ:* bố cục 3 cột, cây BE/FE, canvas diagram, panel Diễn giải/Kịch bản, trang cài đặt. Không chứa logic phân tích.
   - *Giao tiếp:* REST/JSON với Engine; SSE để nhận tiến độ quét và nội dung AI đang sinh.

2. **API (Controller trong Engine)**
   - *Công nghệ:* Spring Web MVC, validation bằng Jakarta Bean Validation, tài liệu API bằng springdoc-openapi.
   - *Nhiệm vụ:* nhận request, kiểm tra đầu vào, gọi Service. Danh sách API: [API_list.md](API_list.md).

3. **Scan Service**
   - Liệt kê file theo luật include/exclude, tính content hash, so với lần quét trước.
   - Gọi Adapter: Spring chạy song song trên **virtual threads**; Angular chạy qua sidecar.
   - Ghi IR theo lô (batch), chạy Linker, cập nhật trạng thái lần quét và đẩy tiến độ qua SSE.

4. **Stack Detector:** đọc các file đặc trưng của từng Source (xem Nhóm 1 trong [feature_list.md](feature_list.md)) và chọn Adapter.

5. **Adapters (một bộ phân tích cho mỗi công nghệ)**
   - *Spring Adapter (Java, trong Engine):*
     - **JavaParser + JavaSymbolSolver** phân giải kiểu, import, kế thừa, overload.
     - Luật riêng cho Spring: DI qua field `final` + Lombok, interface → implementation, Spring Data, JPQL/SQL ghép động.
   - *Angular Adapter (trong Node Analyzer):*
     - **TypeScript Compiler API** cho `.ts`, **`@angular/compiler` (`parseTemplate`)** cho template.
     - Đọc thêm `angular.json`, route, federation, `proxy.conf.json`, `environment*.ts`, file i18n.
   - *PHP-legacy Adapter (mở rộng, trong Node Analyzer):* tree-sitter-php cùng luật quy ước của framework cũ.
   - Mọi Adapter chỉ **sinh IR** (theo schema chung) và không ghi DB trực tiếp. Quy tắc phân tích chi tiết: Nhóm 2 trong [feature_list.md](feature_list.md).

6. **Linker:** nối `HTTP_CALL` (FE) với `API_ENDPOINT` (BE). Kết quả là edge `HTTP_REQUEST` có mức tin cậy và danh sách ứng viên. Thuật toán: Nhóm 5 trong [feature_list.md](feature_list.md).

7. **Tracer:** đi trên graph từ node xuất phát, áp liên kết chốt tay, bộ lọc nhiễu và giới hạn độ sâu, rồi sinh `Trace` (participant, bước có thứ tự, khối `alt`/`opt`).

8. **Diagram Builder:** chuyển `Trace` thành *view model* cho renderer SVG và thành mã Mermaid để xuất.

9. **Explanation Service:** sinh nội dung tab Diễn giải, tab Kịch bản và dữ liệu mẫu. Phần tất định tự làm; chỉ gọi AI Gateway cho các mục đích được phép (§2.7).

10. **AI Gateway**
    - Dùng **Spring AI** (`ChatClient`) sau interface `LlmProvider`: Gemini (mặc định), Anthropic, OpenAI-compatible (dùng được cho gateway tương thích, kể cả AI nội bộ nếu sau này được phép), hoặc `none`.
    - Trách nhiệm chung: **che secret** trước khi gửi, **cache** theo hash đầu vào, **giới hạn token** và hạn mức ngày, **ghi log** (NFR-17), **kiểm tra đầu ra** theo JSON schema, retry có backoff.

11. **CSDL (PostgreSQL 16):** lưu Workspace, Source, kết quả quét, IR, cache AI, dữ liệu người dùng (§2.3).
    - **Spring Data JPA** cho các bảng thông thường.
    - **JdbcClient ghi theo lô** cho `ir_node` / `ir_edge`, vì số lượng bản ghi lớn.
    - Migration bằng **Flyway**.

12. **Nguồn mã (Source Mount):** thư mục gốc trên máy (`SOURCE_ROOT`) được gắn vào container ở chế độ **read-only**. Engine và sidecar chỉ đọc bên trong phạm vi này.

### 2.1.3 Công nghệ sử dụng và lý do

| Thành phần | Lựa chọn | Lý do |
| :--- | :--- | :--- |
| Engine | Java 21 LTS + Spring Boot 4.x, Maven multi-module | Stack phổ biến ở dự án doanh nghiệp mục tiêu; phân tích Java "cùng nhà" với JavaParser; virtual threads cho xử lý song song |
| Phân tích Java | JavaParser + JavaSymbolSolver | Phân giải kiểu/tham chiếu có sẵn, chính xác hơn tự viết |
| Phân tích Angular | Node Analyzer (sidecar): TypeScript Compiler API + `@angular/compiler` | Cách duy nhất hiểu chính xác TypeScript và template Angular |
| Hợp đồng dữ liệu | JSON Schema chung trong `contracts/` | Engine (Java) và sidecar (TypeScript) cùng tuân theo; Engine kiểm tra đầu ra của sidecar |
| Web UI | Angular 21 + Angular Material/CDK | Cùng stack với dự án thực tế của team |
| Diagram | Renderer SVG tự viết + xuất Mermaid | Kiểm soát hoàn toàn tương tác (highlight, đồng bộ với Kịch bản, thu gọn) |
| CSDL | PostgreSQL 16 + Spring Data JPA + JdbcClient (batch) + Flyway | JSONB cho thuộc tính node, tìm kiếm toàn văn sẵn có; ghi hàng loạt IR nhanh; migration có phiên bản |
| AI | Spring AI, Gemini mặc định | Một API chung cho nhiều provider; đổi provider chỉ bằng cấu hình |
| Đóng gói | Docker Compose (`flowtracer-app` gồm JRE 21 + Node 22, và `postgres`) | Một lệnh khởi chạy |
| Kiểm thử | JUnit 5 + AssertJ + Testcontainers + ArchUnit (Engine); Vitest (sidecar); Angular testing (UI) | ArchUnit tự động chặn vi phạm ranh giới module; Testcontainers test với PostgreSQL thật |

### 2.1.4 Hợp đồng Adapter

Mọi Adapter triển khai cùng một interface (Java). Phần lõi (`flowtracer-core`) **không được** chứa logic riêng của bất kỳ công nghệ nào. Adapter chạy trong sidecar được Engine bọc bằng `SidecarStackAdapter`, cũng triển khai interface này.

```java
// Phác thảo interface (module flowtracer-ir)
public interface StackAdapter {
    /** Định danh ổn định, ví dụ "spring", "angular", "php-legacy" */
    String id();
    /** Vai trò Source mà adapter hỗ trợ */
    Set<SourceRole> roles();

    /** Mức độ phù hợp (0..1) dựa trên các file đặc trưng của Source */
    DetectResult detect(DetectContext ctx);

    /** Pha 1 – từng file độc lập, chạy song song: trích "dữ kiện" thô (class, hàm, lời gọi, annotation…).
     *  Kết quả được cache theo mã băm file, nên file không đổi thì không phải đọc lại. */
    FileFacts extract(ExtractContext ctx, SourceFile file);

    /** Pha 2 – một lần cho cả Source: ghép dữ kiện của mọi file để phân giải tham chiếu chéo
     *  (DI, interface → impl, hằng số URL…) và sinh IR hoàn chỉnh. */
    IrFragment resolve(ResolveContext ctx, List<FileFacts> facts);

    /** Bộ lọc nhiễu mặc định của công nghệ */
    default List<NoiseFilter> defaultNoiseFilters() { return List.of(); }
}
```

Với adapter trong sidecar, Engine chạy một lệnh cho mỗi Source:
- `node analyzer-node analyze --adapter angular --source <path> --cache <dir>`;
- sidecar tự làm cả pha 1 (có cache) và pha 2, rồi trả `IrFragment` dạng JSON;
- tiến độ được in từng dòng ra stderr.

### 2.1.5 Nguyên tắc sử dụng AI trong kiến trúc

- **Diagram và trace không bao giờ do AI sinh ra.** Chúng là kết quả tất định từ IR.
- AI chỉ **bổ sung văn bản** (Mục đích, làm mượt Kịch bản, dữ liệu mẫu sinh một lần từ cấu trúc DTO) hoặc **chọn trong danh sách ứng viên** khi liên kết chưa chắc chắn.
- Mọi đầu ra của AI đều được **kiểm tra** (JSON schema, số bước, ứng viên hợp lệ). Kiểm tra không đạt thì dùng kết quả tất định.

## 2.2 Mô hình dữ liệu trung gian (IR)

IR là "bản đồ" chung cho mọi công nghệ. Mỗi Adapter sinh IR; Linker, Tracer và Explain chỉ làm việc với IR.

### 2.2.1 Loại Node

| `kind` | Ý nghĩa | Thuộc tính chính (`attrs`) |
| :--- | :--- | :--- |
| `UI_APP` | Một ứng dụng FE (một app trong microfrontend) | `name`, `port`, `isShell` |
| `UI_SCREEN` | Màn hình (component được định tuyến) | `routePath`, `title`, `component` |
| `UI_TAB` | Tab trong màn hình | `label` |
| `UI_DIALOG` | Dialog/modal | `component`, `label` |
| `UI_FORM` | Form | `fields[]` (tên, loại control, validators) |
| `UI_ELEMENT` | Phần tử tương tác | `elementType` (button, input, select, table, pagination…), `event`, `label`, `i18nKey` |
| `FE_METHOD` | Method phía FE | `layer` (COMPONENT / FACADE / USECASE / SERVICE / REPOSITORY), `className`, `signature` |
| `HTTP_CALL` | Điểm gọi HTTP phía FE | `httpMethod`, `urlExpr` (biểu thức gốc), `urlPattern` (đã chuẩn hóa), `bodyType` |
| `API_ENDPOINT` | Endpoint BE | `httpMethod`, `path`, `params[]`, `requestType`, `responseType`, `summary`, `tag`, `security` |
| `BE_METHOD` | Method phía BE | `layer` (CONTROLLER / SERVICE / REPOSITORY / OTHER), `className`, `signature`, `transactional` |
| `DATA_QUERY` | Một truy vấn dữ liệu | `queryKind` (DERIVED / JPQL / NATIVE / DYNAMIC / LIB_CALL), `text` (đã rút gọn), `operation` (READ / WRITE / DELETE) |
| `DB_TABLE` | Bảng hoặc collection | `name`, `entity`, `discriminator` |
| `DATA_TYPE` | DTO, Entity hoặc model | `fields[]` (tên JSON, kiểu, ràng buộc), `jsonNaming` |
| `EXTERNAL` | Hệ thống ngoài hoặc đích không xác định | `url`, `reason` |

Mọi node đều có: `key` (ổn định), `sourceId`, `filePath`, `startLine`, `endLine`, `displayName` (tên thân thiện tiếng Việt nếu phân giải được).

### 2.2.2 Loại Edge

| `kind` | Từ → Tới | Ý nghĩa |
| :--- | :--- | :--- |
| `CONTAINS` | App → Screen → Tab/Dialog/Form → Element | Quan hệ phân cấp (dùng dựng cây FE) |
| `TRIGGERS` | `UI_ELEMENT` → `FE_METHOD` | Sự kiện gọi handler |
| `CALLS` | Method → Method | Lời gọi hàm (FE hoặc BE) |
| `OPENS` / `NAVIGATES` | `FE_METHOD` → `UI_DIALOG` / `UI_SCREEN` | Mở dialog / chuyển màn hình |
| `SENDS` | `FE_METHOD` → `HTTP_CALL` | Method phát ra lời gọi HTTP |
| `HTTP_REQUEST` | `HTTP_CALL` → `API_ENDPOINT` | Do Linker tạo |
| `HANDLED_BY` | `API_ENDPOINT` → `BE_METHOD` | Endpoint được xử lý bởi method controller |
| `EXECUTES` | `BE_METHOD` → `DATA_QUERY` | Method thực thi truy vấn |
| `READS` / `WRITES` | `DATA_QUERY` → `DB_TABLE` | Tác động dữ liệu |
| `USES_TYPE` | Method/Endpoint → `DATA_TYPE` | Kiểu dữ liệu vào/ra |

Mọi edge đều có: `confidence`, `reason` (vì sao có mức tin cậy này), `callOrder` (thứ tự lời gọi trong method nguồn, dùng xếp bước trên sequence), `guard` (điều kiện bao quanh nếu có), `candidates[]` (khi có nhiều ứng viên), `filePath`/`line` của điểm gọi.

### 2.2.3 Ví dụ IR (rút gọn)

```json
{
  "nodes": [
    { "key": "ng:element:admin-app/category-list#btn-create", "kind": "UI_ELEMENT",
      "displayName": "Nút Thêm mới", "attrs": { "elementType": "button", "event": "click", "i18nKey": "btn_create" },
      "filePath": "projects/admin-app/src/app/modules/category/presentation/category-list.component.html", "startLine": 19 },
    { "key": "ng:http:admin-app/HttpCategoryRepository#create:0", "kind": "HTTP_CALL",
      "attrs": { "httpMethod": "POST", "urlExpr": "`${API_BASE}/categories`", "urlPattern": "/api/v1/categories" } },
    { "key": "java:endpoint:POST /api/v1/categories", "kind": "API_ENDPOINT",
      "attrs": { "httpMethod": "POST", "path": "/api/v1/categories", "requestType": "CategorySaveRequest" } },
    { "key": "java:table:category", "kind": "DB_TABLE", "attrs": { "name": "category", "entity": "CategoryEntity" } }
  ],
  "edges": [
    { "from": "ng:http:admin-app/HttpCategoryRepository#create:0", "to": "java:endpoint:POST /api/v1/categories",
      "kind": "HTTP_REQUEST", "confidence": "INFERRED",
      "reason": "URL ghép từ hằng số API_BASE và áp luật proxy '^/gateway' → ''" }
  ]
}
```

### 2.2.4 Cấu trúc Trace (kết quả trả cho UI)

```json
{
  "traceId": "…",
  "start": "ng:element:admin-app/category-form-dialog#btn-save",
  "detailLevel": "COMPONENT",
  "participants": [
    { "id": "p1", "label": "Người dùng", "layer": "ACTOR" },
    { "id": "p2", "label": "Hộp thoại Thêm danh mục", "layer": "FE", "nodeKey": "…" },
    { "id": "p3", "label": "API Danh mục", "layer": "BE", "nodeKey": "…" }
  ],
  "steps": [
    { "index": 1, "from": "p1", "to": "p2", "kind": "TRIGGERS", "label": "Bấm \"Lưu\"", "confidence": "CERTAIN", "edgeId": "…" },
    { "index": 2, "from": "p2", "to": "p3", "kind": "HTTP_REQUEST", "label": "POST /api/v1/categories", "confidence": "INFERRED", "reason": "…" }
  ],
  "blocks": [
    { "type": "alt", "label": "Mã danh mục đã tồn tại", "fromStep": 4, "toStep": 5 }
  ],
  "warnings": [ "1 lời gọi không xác định được đích (xem bước 6)" ]
}
```

## 2.3 Thiết kế cơ sở dữ liệu

Thiết kế chi tiết nằm ở **[database.md](database.md)** (mô tả từng bảng, từng cột, quan hệ). Sơ đồ nằm ở **[docs/diagrams/database.dbml](../docs/diagrams/database.dbml)**.

Tóm tắt:
- **Mã nguồn dự án** giữ nguyên tại chỗ, tool chỉ đọc. Mọi thứ còn lại nằm trong **PostgreSQL của tool**, không phải CSDL của dự án được phân tích.
- **12 bảng**, chia làm 4 nhóm:
  - *Cấu hình phân tích:* `workspace`, `source`.
  - *Kết quả phân tích:* `scan_run`, `source_file`, `ir_node`, `ir_edge`. Sinh lại được bằng cách quét lại.
  - *AI:* `ai_artifact` (bộ nhớ đệm nội dung AI), `llm_call_log` (nhật ký gọi AI, không lưu nội dung prompt).
  - *Dữ liệu người dùng:* `link_override`, `app_setting`, `feature`, `feature_member`. Không sinh lại được.
- Dữ liệu người dùng và cache AI trỏ tới thành phần bằng **`key` ổn định** (không dùng khóa ngoại tới `ir_node.id`), để không bị mất khi quét lại.

## 2.4 Kịch bản hoạt động của hệ thống

Các kịch bản dưới đây mô tả **cách các bộ phận của tool phối hợp với nhau**, trình bày theo từng bước.

Tên gọi các bộ phận trong kịch bản:
- **Giao diện**: Web UI.
- **Bộ máy phân tích**: Analysis Engine (nhận yêu cầu từ Giao diện).
- **Bộ quét**: Scan Orchestrator.
- **Bộ phân tích**: các Adapter (Spring, Angular…).
- **Bộ liên kết**: Linker.
- **Bộ dò vết**: Tracer.
- **Bộ diễn giải**: Explain Service.
- **Cổng AI**: LLM Gateway.
- **CSDL**: PostgreSQL của tool.

### 2.4.1 Kịch bản 1: Tạo Workspace và quét lần đầu

1. **Người dùng** bấm "Tạo Workspace" trên **Giao diện**, đặt tên và chọn thư mục Backend, Frontend bằng trình duyệt thư mục.
2. **Giao diện** gửi thông tin Workspace tới **Bộ máy phân tích**.
3. **Bộ máy phân tích** nhận diện công nghệ của từng thư mục (ví dụ: Backend là Spring Boot 3.3, Frontend là Angular 21) và lưu Workspace vào **CSDL**.
4. **Giao diện** hiển thị kết quả nhận diện. **Người dùng** kiểm tra rồi bấm "Quét".
5. **Bộ máy phân tích** tạo một lần quét mới và báo ngay cho **Giao diện** là đã bắt đầu; việc quét chạy ở chế độ nền.
6. **Giao diện** mở kênh nhận tiến độ và hiển thị thanh tiến độ.
7. **Bộ quét** lập danh sách file cần đọc (bỏ qua thư viện, file build, file nhị phân) và tính mã băm từng file.
8. **Bộ quét** chia file thành từng lô và giao cho **Bộ phân tích**. Với mỗi lô:
   - **Bộ phân tích** đọc file và trả về các thành phần cùng quan hệ tìm được.
   - **Bộ quét** lưu kết quả vào **CSDL** và báo tiến độ lên **Giao diện**.
   - 8a. *Nếu một file bị lỗi cú pháp:* **Bộ quét** ghi file đó vào danh sách lỗi và tiếp tục với các file khác.
9. **Bộ liên kết** nối các lời gọi API ở Frontend với endpoint tương ứng ở Backend, ghi kèm mức tin cậy vào **CSDL**.
10. **Bộ quét** đánh dấu lần quét hoàn tất (thành công, hoặc thành công một phần nếu có file lỗi) và báo cho **Giao diện**.
11. **Giao diện** tải cây API Backend và cây thành phần Frontend, hiển thị ở cột trái.

### 2.4.2 Kịch bản 2: Chọn một nút trên giao diện dự án để xem luồng và diễn giải

1. **Người dùng** chọn nút "Lưu" trong cây Frontend.
2. **Giao diện** yêu cầu **Bộ máy phân tích** dò luồng bắt đầu từ nút này.
3. **Bộ dò vết** lấy từ **CSDL** các thành phần, quan hệ liên quan và những liên kết người dùng đã chốt tay. Sau đó nó đi lần lượt: nút → hàm xử lý → lời gọi API → máy chủ xử lý → bảng dữ liệu.
   - 3a. *Nếu gặp chỗ có nhiều khả năng ngang nhau:* **Bộ dò vết** nhờ **Cổng AI** chọn.
     - **Cổng AI** tìm trong bộ nhớ đệm trước. Nếu chưa có, nó che thông tin bí mật, gửi đoạn code và danh sách ứng viên tới AI, kiểm tra câu trả lời có nằm trong danh sách không, rồi lưu kết quả.
     - AI không chắc chắn thì giữ nhãn "Không xác định".
4. **Bộ dò vết** trả chuỗi bước cho **Giao diện**, và **Giao diện** vẽ sequence diagram ở canvas giữa.
5. Cùng lúc, **Giao diện** lấy nội dung tab **Diễn giải** từ **Bộ diễn giải**:
   - **Bộ diễn giải** tự lập đầu vào, đầu ra và ràng buộc trực tiếp từ code.
   - Mục "Mục đích" lấy từ chú thích có sẵn trong code. Chỉ khi không có, **Bộ diễn giải** mới nhờ **Cổng AI** viết (hoặc dùng bản đã lưu).
   - Dữ liệu mẫu lấy từ bản đã lưu. Nếu chưa có, **Cổng AI** sinh **một lần** từ cấu trúc DTO; **Bộ diễn giải** kiểm tra dữ liệu khớp cấu trúc rồi lưu lại.
   - Nội dung hiện dần lên panel.
6. Cùng lúc, **Giao diện** lấy nội dung tab **Kịch bản**: **Bộ diễn giải** dựng câu mô tả cho từng bước từ chuỗi bước. Nếu bật làm mượt, nó nhờ **Cổng AI** viết lại câu cho tự nhiên và kiểm tra số bước không bị đổi.
7. **Người dùng** đọc diagram và hai tab. Di chuột vào một bước trong Kịch bản thì mũi tên tương ứng trên diagram sáng lên, và ngược lại.

### 2.4.3 Kịch bản 3: Quét lại sau khi code thay đổi

1. **Người dùng** sửa code trong IDE, quay lại tool và bấm "Quét lại".
2. **Giao diện** yêu cầu **Bộ máy phân tích** quét lại Workspace.
3. **Bộ quét** tính lại mã băm các file và so với mã băm đã lưu trong **CSDL** để tìm file mới, file đã sửa và file đã xóa.
   - 3a. *Nếu không có thay đổi:* **Bộ quét** báo "Không có thay đổi" và kết thúc.
4. **Bộ quét** xóa khỏi **CSDL** các thành phần và quan hệ thuộc file đã sửa hoặc đã xóa.
5. **Bộ phân tích** đọc lại các file mới và file đã sửa; **Bộ quét** lưu kết quả mới vào **CSDL**.
6. **Bộ liên kết** nối lại phần Frontend – Backend bị ảnh hưởng. Liên kết chốt tay và nhóm chức năng của người dùng vẫn giữ nguyên.
7. **Bộ quét** báo hoàn tất cho **Giao diện**, kèm số file thêm, sửa, xóa.
   - Nội dung AI của phần code không đổi vẫn được dùng lại.
   - Phần code đã đổi sẽ được sinh nội dung AI mới khi người dùng mở tới.

## 2.5 API nội bộ

Danh sách đầy đủ nằm ở **[API_list.md](API_list.md)**, chia 9 nhóm A–I tương ứng với 9 nhóm chức năng. Quy ước chính:
- Tiền tố `/api`, dữ liệu JSON, lỗi theo dạng `{ "error": { "code", "message", "details" } }`.
- Tiến độ quét và nội dung AI đang sinh được đẩy về dạng luồng (SSE).
- Không có đăng nhập; Engine chỉ lắng nghe `127.0.0.1`.
- Thành phần được chỉ định bằng `node_key` ổn định. Kết quả dò vết có `trace_id` tính từ đầu vào nên tính lại được bất cứ lúc nào.

## 2.6 Thiết kế giao diện (mô tả bằng lời)

> Thiết kế hình ảnh chi tiết (màu sắc, spacing, mockup) sẽ làm sau. Phần này chỉ mô tả bố cục và hành vi để dev có thể dựng khung.

**Thanh trên cùng:** tên Workspace (dropdown để chuyển), trạng thái quét (thời điểm quét gần nhất, số cảnh báo), nút "Quét lại", ô tìm kiếm (`Ctrl+K`), nút Cài đặt, chỉ báo AI (bật/tắt/lỗi).

**Cột trái (có thể thu gọn, kéo được độ rộng):**
- Tab **Backend**: cây `Module → Endpoint` (badge method, path, cảnh báo nếu có node không xác định).
- Tab **Frontend**: cây `Ứng dụng → Màn hình → Tab → Dialog/Form → Phần tử` (icon theo loại; badge "API" hoặc "UI").
- Tab **Chức năng** *(mở rộng)*: danh sách nhóm chức năng đã lưu và gợi ý.
- Ô lọc nhanh ở đầu mỗi tab. Node đang mở được highlight.

**Canvas giữa:**
- Thanh công cụ: chuyển mức chi tiết (Tầng / Thành phần / Chi tiết), Sequence ↔ Flowchart *(mở rộng)*, zoom, vừa khung, xuất (SVG/PNG/Mermaid), "Mở thêm để so sánh" *(mở rộng)*.
- Chú giải nét vẽ theo mức tin cậy.
- Cảnh báo phía trên diagram nếu trace có bước không xác định (bấm để nhảy tới bước đó).
- Trạng thái rỗng: hướng dẫn "Chọn một endpoint hoặc một phần tử ở cột trái".

**Cột phải (có thể thu gọn):** tiêu đề là tên node (tên thân thiện kèm loại), và hai tab:
- **Diễn giải:** Mục đích → Thuộc chức năng → Đầu vào (khối JSON) → Đầu ra (khối JSON và các trường hợp lỗi) → Ràng buộc → Dữ liệu mẫu (khối JSON, nút sao chép).
- **Kịch bản:** danh sách bước đánh số, nhánh `alt` thụt vào; hover/click đồng bộ với diagram; công tắc "Hiện chi tiết kỹ thuật".
- Mục do AI sinh có biểu tượng nhỏ "AI" và nút "Sinh lại".

**Trang Cài đặt Workspace:** danh sách Source (vai trò, đường dẫn, include/exclude, công nghệ nhận diện), luật liên kết, bộ lọc nhiễu, danh sách liên kết chốt tay, xuất/nhập JSON.

**Các trạng thái chung:** đang quét (thanh tiến độ và giai đoạn), lỗi quét (danh sách file lỗi), AI tắt/lỗi (mục Mục đích hiện thông báo, phần còn lại bình thường).

## 2.7 Chính sách sử dụng AI

**Mục đích được phép** (muốn thêm mục đích mới phải cập nhật bảng này trước khi triển khai):

| Mục đích (`purpose`) | Khi nào gọi | Đầu vào gửi AI | Đầu ra (đã kiểm tra) | Mặc định |
| :--- | :--- | :--- | :--- | :--- |
| `PURPOSE_TEXT` | Node được mở, thiếu `@Operation`/Javadoc/đặc tả | Chữ ký, trace rút gọn, đoạn code liên quan đã che secret | 1–3 câu tiếng Việt | Bật |
| `SCENARIO_POLISH` | Tab Kịch bản được mở | Khung câu tất định | Câu đã làm mượt, **cùng số bước** | Bật |
| `EDGE_RESOLUTION` | Trace đi qua cạnh có nhiều ứng viên | Code tại điểm gọi, danh sách ứng viên | `{choice ∈ ứng viên \| null, confidence, reason}` | Bật |
| `MOCK_DATA` | Mở dữ liệu mẫu của node lần đầu (hoặc DTO đã thay đổi) | Cấu trúc DTO: tên trường, kiểu, ràng buộc (không có dữ liệu thật) | JSON request/response mẫu thỏa cấu trúc; sinh **một lần** rồi lưu cache | Bật |
| `FEATURE_SUMMARY` *(mở rộng)* | Mở nhóm chức năng | Mục đích của các thành viên | Đoạn tóm tắt | Bật |
| `LEGACY_MATCH` *(mở rộng)* | Không ghép được cũ/mới bằng tài liệu hay độ tương đồng | Chữ ký, trace rút gọn hai phía | Xếp hạng ứng viên | Bật |
| `MIGRATION_CHECKLIST` *(mở rộng)* | Người dùng yêu cầu đối soát | Hai trace và hai bộ ràng buộc | Checklist có trỏ vị trí code | Bật |

**Quy tắc:**
- Prompt được quản lý có phiên bản (`prompt_version` là một phần của khóa cache).
- Đầu ra bắt buộc có cấu trúc (JSON mode / schema) và được kiểm tra trước khi dùng.
- Che secret theo NFR-13 và giới hạn kích thước ngữ cảnh (chỉ gửi đoạn code liên quan, không gửi cả repo).
- Hết quota hoặc lỗi thì trả kết quả tất định kèm thông báo, không chặn UI.
- Cấu hình qua biến môi trường: `LLM_PROVIDER`, `LLM_MODEL`, `GEMINI_API_KEY` / `ANTHROPIC_API_KEY` / `LLM_BASE_URL` + `LLM_API_KEY`, `LLM_DAILY_TOKEN_LIMIT`. Trạng thái bật/tắt từng mục đích chỉnh được trên giao diện.

---

# Phụ lục

## Phụ lục A — Ví dụ đầu ra mong đợi (miền giả định "Danh mục")

**Người dùng chọn:** cây Frontend › Ứng dụng quản trị › Màn hình "Danh mục" › Hộp thoại "Thêm danh mục" › Nút **Lưu**.

**Sequence diagram (mức "Thành phần")** gồm 8 cột, từ trái sang phải: Người dùng · Hộp thoại Thêm danh mục · Điều phối Danh mục (FE) · Kết nối API Danh mục (FE) · API Danh mục · Dịch vụ Danh mục · Truy cập dữ liệu Danh mục · Bảng category. Mũi tên gửi yêu cầu API từ Frontend sang Backend vẽ **nét đứt** (mức `INFERRED`, vì URL được ghép từ hằng số và luật proxy). Nhánh "Mã đã tồn tại" vẽ thành khối `alt`. Mỗi mũi tên ứng với một bước trong tab Kịch bản bên dưới.

**Tab "Diễn giải":**

- **Mục đích:** Thêm một danh mục mới vào danh sách danh mục dùng chung của hệ thống.
- **Thuộc chức năng:** Quản lý khai báo › Danh mục
- **Đầu vào:**
  ```json
  {
    "body": {
      "code": "string — bắt buộc, tối đa 50 ký tự, không trùng với danh mục đang hoạt động",
      "title": "string — bắt buộc, tối đa 255 ký tự",
      "brief": "string — không bắt buộc",
      "status": "integer — 1: hoạt động, 0: không hoạt động"
    }
  }
  ```
- **Đầu ra:**
  ```json
  {
    "error_code": "string",
    "message": "string",
    "data": { "id": "string" }
  }
  ```
  Lỗi có thể gặp: mã đã tồn tại; thiếu trường bắt buộc.
- **Ràng buộc:**
  - Mã và tên không được để trống.
  - Mã không được trùng với danh mục đang hoạt động trong cùng đơn vị.
  - Người dùng phải có quyền quản lý khai báo.
- **Dữ liệu mẫu:**
  ```json
  {
    "request": { "code": "SGK", "title": "Sách giáo khoa", "brief": "Sách phục vụ giảng dạy", "status": 1 },
    "response": { "error_code": "0", "message": "Thêm mới thành công", "data": { "id": "c1f2a9d0-7b3e-4a51-9c2d-5e8f1a2b3c4d" } }
  }
  ```

**Tab "Kịch bản":**

1. **Người dùng** bấm nút *Lưu* trên **Hộp thoại Thêm danh mục**.
2. **Hộp thoại Thêm danh mục** chuyển thông tin vừa nhập cho **Điều phối Danh mục** để lưu.
3. **Điều phối Danh mục** nhờ **Kết nối API Danh mục** gửi yêu cầu tạo mới.
4. **Kết nối API Danh mục** gửi yêu cầu *tạo danh mục* tới **API Danh mục** trên máy chủ, kèm mã "SGK" và tên "Sách giáo khoa".
5. **API Danh mục** chuyển yêu cầu cho **Dịch vụ Danh mục**.
6. **Dịch vụ Danh mục** kiểm tra trong **Bảng category** xem mã "SGK" đã tồn tại chưa.
   - 6a. *Nếu đã tồn tại*: **Dịch vụ Danh mục** trả lỗi "Mã danh mục đã tồn tại", **Hộp thoại** hiển thị thông báo lỗi và giữ nguyên dữ liệu đã nhập.
7. **Dịch vụ Danh mục** lưu danh mục mới vào **Bảng category**.
8. **API Danh mục** trả về mã định danh của danh mục mới.
9. **Điều phối Danh mục** báo thành công, **Hộp thoại** đóng lại và **Màn hình Danh mục** tải lại danh sách.

## Phụ lục B — Hồ sơ dự án demo (đã ẩn định danh)

> Mã nguồn dự án demo chỉ nằm trên máy của thành viên được cho phép. **Không sao chép bất kỳ phần nào vào repo này.**

| Thành phần | Đặc điểm kỹ thuật | Hệ quả với tool |
| :--- | :--- | :--- |
| **Backend** | Spring Boot 3.3, Java 17, Maven multi-module. Khoảng 24 controller, 380 file Java. Phân tầng `Controller → Service (interface) → ServiceImpl → RepositoryImpl / Spring Data JPA → Entity`. DI qua Lombok `@RequiredArgsConstructor` và field `final`. Response bọc trong envelope chung `ApiResponse<T>`. MariaDB, Flyway, springdoc (khoảng một nửa controller có `@Operation`). Validation chủ yếu viết tay trong service bằng `throw` kèm khóa thông báo i18n | Phân giải DI và interface → impl đơn giản; cần bóc envelope; ràng buộc lấy từ `throw` + file thông báo; OpenAPI dùng để đối chiếu |
| **Truy cập dữ liệu** | JPQL ghép động bằng `StringBuilder`/text block, thực thi qua lớp truy vấn dùng chung của **thư viện nội bộ dạng jar**; Spring Data JPA với `@Query`. Nhiều module dùng chung **một bảng legacy có cột phân loại** | Cần luật "điểm thực thi truy vấn"; node bảng phải hiển thị điều kiện phân loại |
| **Frontend** | Angular 21, **microfrontend Native Federation** (1 shell và nhiều remote app) cùng một shared library. Mỗi module theo Clean Architecture: `presentation (component) → application (facade) → domain (repository interface + InjectionToken) → infrastructure (HttpClient)`. Base URL API là hằng số trong shared library; dev proxy có luật `pathRewrite`. i18n vi/en tách theo module. UI kit nội bộ (component nút riêng thay cho `<button>`) | Phải phân giải `InjectionToken` qua providers; hằng số URL xuyên project; áp luật proxy; bắt event trên component UI kit; nhãn lấy từ file i18n |
| **Hệ thống cũ (PHP)** | Nằm **bên trong repo BE** (thư mục tài liệu). Framework PHP tự phát triển, khoảng 5.400 file `.php` và 19.500 template `.tpl`, dữ liệu MongoDB, kế thừa nhiều tầng, service gọi bằng chuỗi | Source `LEGACY` lồng trong Source `BACKEND`; bộ phân tích riêng theo quy ước (Nhóm 2 và Nhóm 8, mở rộng) |
| **Tài liệu đặc tả theo module** | Mỗi module BE có tài liệu API (có ghi file PHP nguồn tương ứng), tài liệu màn hình và **bảng ánh xạ control ↔ API** | Ground truth để đo NFR-08; nguồn tất định để ghép luồng cũ ↔ mới; nguồn "Mục đích" trước khi gọi AI |
