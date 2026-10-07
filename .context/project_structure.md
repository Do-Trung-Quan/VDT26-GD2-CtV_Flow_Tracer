# PROJECT_STRUCTURE.md — CtV Flow Tracer

> **Đề tài:** AI Code-to-Visual Flow Tracer — công cụ phân tích mã nguồn tĩnh và trực quan hóa luồng End-to-End.
> **Kiến trúc:** **Monolith** Spring Boot tổ chức **theo chức năng (package-by-feature)**. Mỗi chức năng theo mẫu `Controller → Service → Repository`. **Lõi phân tích** tách thành các module Maven Java thuần. Có một **sidecar Node.js** để phân tích Angular. Toàn bộ đóng gói bằng Docker Compose.
> Tài liệu liên quan: [overview.md](overview.md) · [feature_list.md](feature_list.md) · [API_list.md](API_list.md) · [database.md](database.md) · [CLAUDE.md](../CLAUDE.md)

---

## 1. Quy ước & nguyên tắc tổ chức mã nguồn

- **Ba khối chạy chung một container:**
  - `engine/`: Java 21 + Spring Boot, là ứng dụng chính.
  - `analyzer-node/`: Node.js + TypeScript, CLI phân tích Angular (và PHP ở bản mở rộng).
  - `web/`: Angular 21, là giao diện.
  - Engine gọi sidecar qua dòng lệnh; hai bên trao đổi JSON theo schema trong `contracts/`.
- **Lõi phân tích tách khỏi ứng dụng:**
  - Các module `flowtracer-ir`, `flowtracer-core`, `flowtracer-adapter-spring` là **Java thuần**: **không** Spring, **không** truy cập CSDL, **không** gọi mạng, **không** tự đọc file (nội dung file được truyền vào).
  - Nhờ vậy lõi dễ viết unit test và golden test, và dùng lại được cho CLI.
- **Hướng phụ thuộc giữa module Maven:** `adapter-spring → ir` · `core → ir` · `app → core, adapter-spring, ir`. `core` **không** phụ thuộc `adapter-*`; việc đăng ký adapter nằm ở `app`.
- **Trong `flowtracer-app`: monolith, package-by-feature.**
  - Mỗi package chức năng (`workspace`, `scan`, `graph`…) có bố cục chuẩn gồm các thư mục con: `controller/` · `service/` · `repository/` · `entity/` · `dto/` (và thư mục đặc thù khi cần: `pipeline/`, `adapter/`, `provider/`, `model/`).
  - Quy tắc phụ thuộc trong một chức năng: `controller → service → repository → entity`. Controller **không** gọi thẳng Repository; Repository **không** gọi Service/Controller; Entity **không** phụ thuộc Service/Controller/DTO. Được kiểm tra bằng ArchUnit.
  - Controller mỏng: chỉ nhận và kiểm tra request rồi gọi Service. Logic nằm ở Service. Repository chỉ truy cập dữ liệu.
- **Ranh giới giữa các package chức năng:**
  - Package khác chỉ được gọi **Service công khai**.
  - **Cấm** dùng Repository hay Entity của package chức năng khác.
  - Được kiểm tra tự động bằng **ArchUnit**.
- **Interface chỉ đặt ở 3 chỗ cần thay thế được:**
  - `SourceReader`: điểm duy nhất đọc mã nguồn người dùng.
  - `LlmProvider`: đổi Gemini / Claude / OpenAI-compatible / tắt AI.
  - `StackAdapter`: mỗi công nghệ một bộ phân tích.
  - Những chỗ còn lại dùng class cụ thể, không tạo interface thừa.
- **Đặt tên:**
  - Java: class `PascalCase`, method/biến `camelCase`, hằng số `UPPER_SNAKE_CASE`, package chữ thường.
  - TypeScript/Angular: file `kebab-case`.
  - Hậu tố rõ vai trò: `*Controller`, `*Service`, `*Repository`, `*Request`/`*Response` (DTO), `*Extractor`, `*Resolver`, `*Builder`, `*Test`, `*IT` (test tích hợp).
- **Tất định:** cùng mã nguồn và cấu hình thì cùng IR, cùng diagram. Lõi phân tích không dùng `Random` không seed, thời gian hiện tại, hay thứ tự `HashMap` / thứ tự duyệt file không sắp xếp.

---

## 2. Cây thư mục tổng quan (gốc repo)

```text
VDT26-GD2-CtV_Flow_Tracer/
├── .context/                        # Thư mục tài liệu đặc tả dự án — đọc ở đây trước khi code (phạm vi, chức năng, API, CSDL…)
├── CLAUDE.md                        # Bộ quy tắc AI agent (Claude Code) phải tuân theo khi sửa code trong repo
├── README.md                        # Trang giới thiệu dự án — mô tả ngắn và cách chạy nhanh
│
├── contracts/                       # Nơi quy định "định dạng dữ liệu" mà Engine (Java) và sidecar (Node.js) gửi cho nhau
├── engine/                          # Mã nguồn chương trình chính (Java) — đọc code dự án, lưu kết quả, cung cấp API cho giao diện
├── analyzer-node/                   # Chương trình phụ (Node.js) — chuyên đọc code Angular, được Engine gọi khi quét
├── web/                             # Mã nguồn giao diện web (Angular) — màn hình 3 cột người dùng thao tác
│
├── fixtures/                        # Các dự án mẫu nhỏ do team TỰ VIẾT — dùng để kiểm thử tool. KHÔNG chứa code công ty
├── scripts/                         # Các công cụ chạy tay: đo tốc độ, đo độ chính xác, chặn lỡ commit từ khóa nội bộ
├── docker/                          # File cấu hình đóng gói và chạy toàn bộ tool bằng Docker
├── docs/                            # Tài liệu phụ: file mẫu, sơ đồ CSDL, nhật ký tiến độ, thiết kế giao diện, kết quả đo
├── .github/workflows/ci.yml         # Kịch bản kiểm tra tự động — mỗi lần đẩy code lên GitHub sẽ tự build và chạy test
│
├── .editorconfig                    # Quy ước định dạng file (thụt lề, xuống dòng…) để mọi editor viết code giống nhau
├── .env.example                     # File mẫu các biến cấu hình (đường dẫn mã nguồn, mật khẩu CSDL, API key…) — sao chép thành .env rồi điền
└── .gitignore                       # Danh sách file/thư mục Git bỏ qua, không đưa lên repo (thư viện tải về, file build, .env, tài liệu nội bộ…)
```

---

## 3. `contracts/` — Hợp đồng dữ liệu dùng chung

**Vai trò:** nguồn sự thật cho dữ liệu trao đổi giữa Engine và sidecar.
- Java: record trong `flowtracer-ir` phải khớp schema; test hợp đồng kiểm tra điều này.
- TypeScript: kiểu trong `analyzer-node` được **sinh tự động** từ schema.

```text
contracts/
├── ir-fragment.schema.json          # Bản mô tả định dạng của "kết quả phân tích một thư mục code" (danh sách thành phần + quan hệ)
│                                    #   — sidecar phải trả kết quả đúng định dạng này, Engine dựa vào đây để kiểm tra
├── analyze-request.schema.json      # Bản mô tả định dạng "yêu cầu phân tích" Engine gửi sang sidecar (phân tích thư mục nào, những file nào)
├── progress-event.schema.json       # Bản mô tả định dạng "thông báo tiến độ" sidecar gửi về trong lúc chạy (đang ở bước nào, xong bao nhiêu file)
├── sidecar-protocol.md              # Tài liệu hướng dẫn cách Engine gọi sidecar và ý nghĩa các mã kết quả trả về (thành công, lỗi, quá thời gian)
└── examples/
    └── ir-fragment.example.json     # Một kết quả phân tích mẫu đúng định dạng — dùng để kiểm tra cả Java lẫn Node.js hiểu dữ liệu giống nhau
```

---

## 4. `engine/` — Analysis Engine (Maven multi-module)

```text
engine/
├── pom.xml                          # File cấu hình build tổng của Engine — khai báo 4 module bên dưới và phiên bản các thư viện dùng chung
├── mvnw, mvnw.cmd, .mvn/            # Công cụ build đi kèm repo — chạy ./mvnw (Windows: mvnw.cmd) là build được, không cần tự cài Maven
├── flowtracer-ir/                   # Module định nghĩa "ngôn ngữ chung": các kiểu dữ liệu mô tả thành phần, quan hệ, luồng — mọi phần khác dùng chung
├── flowtracer-core/                 # Module chứa các thuật toán chính: nối FE với BE, dò luồng, vẽ diagram, viết kịch bản, sinh dữ liệu mẫu
├── flowtracer-adapter-spring/       # Module chuyên đọc hiểu code Java/Spring Boot của dự án được phân tích
└── flowtracer-app/                  # Module ứng dụng chạy thật: nhận yêu cầu từ giao diện, điều phối quét, lưu CSDL, gọi AI
```

### 4.1. `flowtracer-ir/` — Ngôn ngữ chung của hệ thống

**Lưu ý:** mọi thay đổi ở đây ảnh hưởng toàn hệ thống. Phải cập nhật `contracts/ir-fragment.schema.json`, §2.2 của overview và golden test trong **cùng** thay đổi.

```text
flowtracer-ir/src/main/java/com/ctv/flowtracer/ir/
├── NodeKind.java                    # Danh sách các LOẠI THÀNH PHẦN tool nhận biết được: màn hình, nút bấm, hàm, API, bảng dữ liệu…
├── EdgeKind.java                    # Danh sách các LOẠI QUAN HỆ giữa hai thành phần: "chứa", "bấm thì gọi", "gọi hàm", "gửi yêu cầu API",
│                                    #   "đọc bảng", "ghi bảng"…
├── Confidence.java                  # Danh sách các MỨC TIN CẬY của một quan hệ: chắc chắn / suy luận / AI chọn / không xác định
├── SourceRole.java                  # Danh sách VAI TRÒ của một thư mục code: Backend / Frontend / hệ thống cũ
├── IrNode.java                      # Kiểu dữ liệu mô tả MỘT THÀNH PHẦN trên bản đồ (tên, loại, nằm ở file nào dòng nào…)
├── IrEdge.java                      # Kiểu dữ liệu mô tả MỘT QUAN HỆ giữa hai thành phần (từ đâu tới đâu, loại gì, chắc chắn đến đâu, vì sao)
├── SourceLocation.java, Guard.java, Candidate.java   # Ba kiểu dữ liệu phụ: vị trí trong code (file + dòng); điều kiện bao quanh lời gọi
│                                    #   (vd "nếu mã đã tồn tại"); một "ứng viên" khi tool chưa chắc quan hệ trỏ tới đâu
├── NodeKeys.java                    # Quy tắc đặt "mã định danh" cố định cho mỗi thành phần, để nhận ra nó qua các lần quét
│                                    #   → ĐỔI QUY TẮC sẽ làm mất liên kết chốt tay, nhóm chức năng và dữ liệu AI đã lưu
├── IrFragment.java                  # Kiểu dữ liệu chứa TOÀN BỘ KẾT QUẢ phân tích một thư mục code (mọi thành phần + quan hệ + cảnh báo)
├── LinkRule.java, NoiseFilter.java, Diagnostic.java   # Ba kiểu dữ liệu phụ: luật giúp nối URL Frontend với API Backend; mẫu thành phần
│                                    #   cần ẩn khỏi diagram cho gọn (ghi log, hàm tiện ích…); một cảnh báo/lỗi khi phân tích (vd file lỗi cú pháp)
├── trace/
│   ├── Trace.java, Participant.java, TraceStep.java, TraceBlock.java   # Các kiểu dữ liệu mô tả MỘT LUỒNG đã dò được: toàn bộ luồng; một cột
│   │                                #   trên sequence diagram; một mũi tên; một khối điều kiện (vd nhánh lỗi)
│   └── DetailLevel.java             # Danh sách các MỨC CHI TIẾT khi vẽ diagram: theo tầng / theo thành phần / chi tiết từng hàm
├── scenario/
│   └── ScenarioStep.java            # Kiểu dữ liệu mô tả MỘT BƯỚC trong tab Kịch bản (số thứ tự, câu mô tả bằng lời, chi tiết kỹ thuật kèm theo)
└── spi/                             # "Hợp đồng" mà mọi bộ phân tích theo công nghệ (Spring, Angular…) phải tuân theo
    ├── StackAdapter.java            # Khuôn mẫu chung của một bộ phân tích: nhận diện công nghệ → đọc từng file → ghép lại thành kết quả
    ├── DetectContext.java, DetectResult.java, ExtractContext.java, ResolveContext.java   # Dữ liệu đưa vào và kết quả trả ra của từng bước
    │                                #   trên (nhận diện / đọc từng file / ghép kết quả)
    ├── SourceFile.java              # Kiểu dữ liệu mô tả MỘT FILE CODE được đưa cho bộ phân tích (đường dẫn, nội dung, mã băm)
    ├── SourceContentLoader.java     # Cách DUY NHẤT để bộ phân tích xin đọc thêm file khác (vd pom.xml, proxy.conf.json) — bộ phân tích
    │                                #   không tự mở file, việc đọc file do ứng dụng làm để kiểm soát an toàn
    └── FileFacts.java               # Khuôn chung cho "những gì đọc được từ một file" — được lưu tạm để lần quét sau không phải đọc lại
```

### 4.2. `flowtracer-core/` — Thuật toán lõi

**Lưu ý:** mọi class đều **thuần và tất định**; không biết Spring hay Angular là gì.

```text
flowtracer-core/src/main/java/com/ctv/flowtracer/core/
├── discovery/                       # Nhóm xử lý "chọn file nào để đọc"
│   ├── DefaultExcludes.java         # Danh sách thư mục/file LUÔN bỏ qua khi quét vì không phải code nghiệp vụ
│   │                                #   (thư viện tải về như node_modules, file build như target/dist, thư mục của IDE…)
│   ├── FileFilter.java              # Bộ lọc quyết định một file có được đọc hay không: theo luật chọn/bỏ của người dùng,
│   │                                #   theo kích thước tối đa, và bỏ file nhị phân (ảnh, file nén…)
│   └── ContentHash.java             # Tính "dấu vân tay" (mã băm) của nội dung file — file không đổi thì dấu vân tay không đổi,
│                                    #   nhờ đó lần quét sau biết file nào cần đọc lại
├── detection/
│   └── StackDetector.java           # Tự nhận diện một thư mục code dùng công nghệ gì (Spring? Angular?) để chọn đúng bộ phân tích
├── graph/                           # Nhóm xử lý "bản đồ" trong bộ nhớ
│   ├── GraphIndex.java              # Bản đồ thành phần + quan hệ dựng sẵn trong bộ nhớ để tra cứu nhanh
│   │                                #   (thành phần này gọi tới ai, được ai gọi, tìm theo loại…)
│   └── Subgraph.java                # Một phần nhỏ của bản đồ (quanh thành phần người dùng chọn) được lấy từ CSDL ra để dò luồng
├── linker/                          # CỐT LÕI: nối lời gọi API ở Frontend với API tương ứng ở Backend
│   ├── UrlPattern.java              # Chuẩn hóa một đường dẫn API thành dạng so sánh được (vd /items/123 và /items/{id} cùng dạng)
│   ├── LinkRuleEngine.java          # Áp các luật biến đổi đường dẫn trước khi so sánh (bỏ tiền tố proxy, thêm tiền tố của Backend…)
│   ├── EndpointMatcher.java         # So một lời gọi API ở Frontend với danh sách API Backend để tìm API khớp nhất
│   │                                #   (đường dẫn cụ thể được ưu tiên hơn đường dẫn có biến)
│   └── Linker.java                  # Điều phối việc nối: với mỗi lời gọi API, kết luận nối tới đâu và chắc chắn đến mức nào, kèm lý do
├── tracer/                          # CỐT LÕI: dò luồng từ đầu tới cuối
│   ├── Tracer.java                  # Bộ dò luồng chính: xuất phát từ nút bấm hoặc API, đi lần lượt qua các lời gọi tới tận bảng dữ liệu
│   ├── OverrideApplier.java         # Áp các quyết định chốt tay của người dùng trước khi dò (ghim liên kết đúng, bỏ liên kết sai)
│   ├── NoiseFilterApplier.java      # Ẩn các bước không mang ý nghĩa nghiệp vụ (ghi log, chuyển đổi dữ liệu…) để diagram gọn
│   ├── CycleGuard.java              # Chống dò vô tận: dừng khi gặp vòng lặp hoặc đi quá sâu (mặc định 12 bước lồng nhau)
│   ├── BlockBuilder.java            # Gom các bước nằm trong điều kiện (vd "nếu mã đã tồn tại thì báo lỗi") thành khối nhánh trên diagram
│   ├── DetailLevelCollapser.java    # Gộp các cột của diagram theo mức chi tiết người dùng chọn (theo tầng / thành phần / từng hàm)
│   └── TraceIds.java                # Tạo mã định danh cho một luồng đã dò — cùng yêu cầu luôn ra cùng mã, nên không cần lưu luồng vào CSDL
├── diagram/                         # Nhóm xử lý "vẽ diagram"
│   ├── SequenceViewModelBuilder.java # Chuyển luồng đã dò thành dữ liệu sẵn sàng để vẽ (vị trí cột, mũi tên, nét liền/nét đứt…)
│   ├── MermaidExporter.java         # Xuất luồng thành mã Mermaid — để dán sequence diagram vào tài liệu, wiki
│   └── SvgExporter.java             # Xuất diagram thành file ảnh SVG để tải về
├── scenario/                        # Nhóm xử lý "viết tab Kịch bản bằng lời"
│   ├── DisplayNames.java            # Đặt tên dễ đọc cho từng thành phần (vd CategoryServiceImpl → "Dịch vụ Danh mục"),
│   │                                #   ưu tiên nhãn tiếng Việt có sẵn trong code
│   ├── StepTemplatesVi.java         # Các mẫu câu tiếng Việt cho từng loại bước: "bấm…", "gửi yêu cầu…", "đọc dữ liệu…", "trả kết quả…"
│   └── ScenarioBuilder.java         # Ghép luồng đã dò với mẫu câu để ra danh sách bước "A làm gì tới B", có bước con cho nhánh lỗi
├── explain/                         # Nhóm xử lý "viết tab Diễn giải"
│   ├── IoSchemaBuilder.java         # Lập khối JSON Đầu vào / Đầu ra của một API hoặc nút bấm từ các class dữ liệu trong code
│   ├── ConstraintsExtractor.java    # Liệt kê các ràng buộc bằng lời (bắt buộc nhập, không được trùng, cần quyền gì…) từ code kiểm tra
│   └── JsonSchemaBuilder.java       # Tạo bản mô tả cấu trúc dữ liệu (JSON Schema) — để AI sinh dữ liệu mẫu đúng cấu trúc và để kiểm tra lại
└── mock/                            # Nhóm xử lý "dữ liệu mẫu"
    ├── SchemaValidator.java         # Kiểm tra một dữ liệu mẫu có đúng cấu trúc yêu cầu không (dùng để kiểm tra dữ liệu AI sinh ra)
    └── FallbackMockGenerator.java   # Bộ sinh dữ liệu mẫu dự phòng KHÔNG dùng AI — dùng khi AI tắt hoặc lỗi; cùng cấu trúc luôn ra cùng dữ liệu
```

### 4.3. `flowtracer-adapter-spring/` — Bộ phân tích Java / Spring Boot

**Cách chạy 2 pha:**
- **Pha 1 (`extract`):** chạy song song từng file trên virtual threads, dùng **JavaParser + JavaSymbolSolver** (type solver trên các thư mục mã nguồn của Source) để ghi sẵn tên đầy đủ (FQN) của kiểu, method được gọi… vào dữ kiện.
- **Pha 2 (`resolve`):** kiểm lại FQN với `ClassIndex` và áp các luật riêng của Spring.

```text
flowtracer-adapter-spring/src/main/java/com/ctv/flowtracer/adapter/spring/
├── SpringStackAdapter.java          # "Cửa vào" của bộ phân tích Spring — nối 3 bước: nhận diện → đọc từng file → ghép kết quả
├── SpringDetector.java              # Kiểm tra một thư mục có phải dự án Spring Boot không (xem pom.xml / build.gradle) và lấy phiên bản
├── facts/
│   └── JavaFileFacts.java           # Kiểu dữ liệu chứa "những gì đọc được từ MỘT file Java": class, hàm, API khai báo, lời gọi hàm,
│                                    #   chỗ báo lỗi, đoạn câu truy vấn, các trường dữ liệu — được lưu tạm để lần sau không phải đọc lại
├── extract/                         # BƯỚC 1 — đọc từng file Java riêng lẻ (chạy song song nhiều file cùng lúc)
│   ├── JavaFileExtractor.java       # Điều phối việc đọc một file: phân tích cú pháp (vẫn chạy được khi file lỗi) rồi gọi các bộ trích dưới đây
│   ├── TypeDeclarationExtractor.java # Lấy thông tin các class trong file: tên, kế thừa ai, và vai trò Spring (là Controller, Service, bảng dữ liệu…)
│   ├── MemberExtractor.java         # Lấy các thuộc tính và hàm của class: tên, kiểu, ghi chú mô tả, yêu cầu quyền, có dùng giao dịch CSDL không
│   ├── MappingExtractor.java        # Lấy các API mà Controller khai báo: phương thức (GET/POST…), đường dẫn, tham số, mô tả Swagger
│   ├── CallSiteExtractor.java       # Lấy mọi lời gọi hàm bên trong một hàm: gọi tới đâu, theo thứ tự nào, có nằm trong điều kiện if/try không
│   ├── ThrowExtractor.java          # Lấy các chỗ báo lỗi (throw): loại lỗi, thông báo lỗi, xảy ra trong điều kiện nào — dùng cho nhánh lỗi
│   ├── StringFragmentExtractor.java # Gom các đoạn chuỗi trong hàm — để ghép lại câu truy vấn CSDL được viết tay bằng cách nối chuỗi
│   └── DtoFieldExtractor.java       # Lấy các trường của class dữ liệu: tên trong JSON, kiểu, ràng buộc (bắt buộc, độ dài…) — dùng cho Đầu vào/Đầu ra
├── resolve/                         # BƯỚC 2 — ghép kết quả của mọi file để hiểu chúng liên quan nhau thế nào
│   ├── JavaResolver.java            # Điều phối bước 2 và tạo ra kết quả phân tích cuối cùng của thư mục Backend
│   ├── ClassIndex.java              # Danh bạ mọi class trong dự án — tra từ tên class ra nơi khai báo
│   ├── InjectionResolver.java       # Xác định mỗi class dùng những class nào khác (Spring tự "tiêm" vào qua constructor hoặc @Autowired)
│   ├── ImplementationResolver.java  # Với một interface, tìm class thực sự làm việc; có nhiều class cùng khả năng thì ghi lại tất cả làm ứng viên
│   ├── CallResolver.java            # Xác định mỗi lời gọi hàm thực sự chạy vào hàm nào; không xác định được thì ghi rõ lý do
│   ├── EndpointBuilder.java         # Tạo danh sách API hoàn chỉnh (ghép đường dẫn cấp class + cấp hàm) và nối mỗi API với hàm xử lý nó
│   ├── DataAccessResolver.java      # Nhận ra các hàm truy cập CSDL (findBy…, save…, delete…) và đó là thao tác đọc hay ghi
│   ├── QueryTextAnalyzer.java       # Đọc nội dung câu truy vấn (JPQL/SQL) để biết nó chạm vào bảng nào, kể cả câu được ghép từ nhiều đoạn
│   ├── EntityTableResolver.java     # Biết class dữ liệu nào ứng với bảng nào trong CSDL
│   ├── DtoSchemaBuilder.java        # Tạo mô tả cấu trúc dữ liệu vào/ra của API (tên trường, kiểu, ràng buộc)
│   └── MessageResolver.java         # Đổi mã thông báo lỗi trong code thành câu tiếng Việt (tra file messages_vi.properties của dự án)
├── config/
│   ├── ApplicationConfigReader.java # Đọc vài thiết lập cần thiết trong file cấu hình của dự án (tiền tố đường dẫn API, kiểu đặt tên trường JSON)
│   │                                #   — CHỈ đọc đúng các khóa này, không giữ mật khẩu hay giá trị bí mật
│   └── QuerySinkRules.java          # Luật do người dùng khai báo: "hàm X của thư viện Y là nơi chạy truy vấn" — dùng khi thư viện không có mã nguồn
├── SpringLinkRules.java             # Tự phát hiện luật đường dẫn phía Backend (vd tiền tố chung của mọi API) để nối với Frontend
└── SpringNoiseFilters.java          # Danh sách mặc định các thành phần cần ẩn khỏi diagram ở dự án Spring (ghi log, chuyển đổi dữ liệu, getter/setter…)

flowtracer-adapter-spring/src/test/java/...
├── extract/*Test.java, resolve/*Test.java   # Kiểm thử từng bước đọc/ghép bằng các đoạn Java TỰ VIẾT
└── SpringGoldenTest.java            # Kiểm thử tổng: phân tích dự án mẫu fixtures/spring-shop-api rồi so với kết quả chuẩn đã lưu
```

### 4.4. `flowtracer-app/` — Ứng dụng Spring Boot (monolith, package-by-feature)

#### 4.4.1. Cây tổng quan

```text
flowtracer-app/
├── pom.xml                          # File cấu hình build của ứng dụng — khai báo các thư viện cần dùng (web, CSDL, AI, test…)
└── src/
    ├── main/java/com/ctv/flowtracer/app/
    │   ├── FlowTracerApplication.java   # Điểm khởi động — chạy file này là Engine bật lên
    │   ├── config/                  # Các thiết lập chung của ứng dụng (đọc biến môi trường, cấu hình web, luồng chạy nền…)
    │   ├── common/                  # Tiện ích dùng chung cho mọi chức năng (cách trả lỗi thống nhất, đẩy dữ liệu thời gian thực…)
    │   ├── source/                  # Bộ đọc mã nguồn người dùng — nơi DUY NHẤT được mở file code của dự án được phân tích
    │   ├── workspace/               # Chức năng nhóm 1: quản lý Workspace và các thư mục mã nguồn
    │   ├── scan/                    # Chức năng nhóm 2: quét và phân tích mã nguồn
    │   ├── graph/                   # Chức năng nhóm 3: lưu "bản đồ" thành phần, dựng cây BE/FE, tìm kiếm
    │   ├── linking/                 # Chức năng nhóm 5: nối Frontend với Backend, liên kết chốt tay
    │   ├── tracing/                 # Chức năng nhóm 4: dò luồng và xuất diagram
    │   ├── explanation/             # Chức năng nhóm 6: nội dung tab Diễn giải, Kịch bản, dữ liệu mẫu
    │   ├── ai/                      # Chức năng nhóm 9: cổng gọi AI duy nhất và cấu hình AI
    │   ├── feature/                 # (MỞ RỘNG) Chức năng nhóm 7: nhóm chức năng
    │   └── comparison/              # (MỞ RỘNG) Chức năng nhóm 8: so sánh hệ thống cũ – mới
    │
    │   # Mỗi thư mục chức năng (workspace … comparison) đều có cùng bố cục chuẩn:
    │   #   controller/  — nhận yêu cầu HTTP từ giao diện, chỉ gọi Service
    │   #   service/     — xử lý nghiệp vụ; package khác chỉ được gọi Service công khai
    │   #   repository/  — đọc/ghi CSDL
    │   #   entity/      — các class ứng với bảng trong CSDL (và enum đi kèm)
    │   #   dto/         — dữ liệu giao diện gửi lên (*Request) và Engine trả về (*Response)
    │   #   (+ thư mục đặc thù khi cần: pipeline/, adapter/, provider/, model/)
    ├── main/resources/
    │   ├── application.yml          # File cấu hình chính của Engine (cổng chạy, kết nối CSDL, thư mục mã nguồn, AI…) — giá trị lấy từ biến môi trường
    │   ├── application-dev.yml      # Cấu hình bổ sung khi chạy lúc phát triển (in log chi tiết hơn)
    │   ├── db/migration/            # Các file SQL tạo/cập nhật cấu trúc CSDL, tự chạy khi Engine khởi động — KHÔNG sửa file đã chạy, muốn đổi thì thêm file mới
    │   ├── prompts/                 # Các câu lệnh mẫu (prompt) gửi cho AI, mỗi mục đích một file, có đánh số phiên bản
    │   └── static/                  # Chỗ chứa bản build của giao diện web khi đóng gói (Engine phục vụ luôn giao diện) — không đưa lên Git
    └── test/java/com/ctv/flowtracer/app/
        ├── ArchitectureTest.java    # Bài kiểm tra tự động "luật kiến trúc": chặn các chỗ code gọi sai ranh giới (vd đọc file ngoài bộ đọc an toàn,
        │                            #   gọi AI ngoài cổng AI, Controller gọi thẳng Repository, chức năng này dùng thẳng dữ liệu của chức năng khác)
        ├── SchemaMappingIT.java     # Bài kiểm tra trên CSDL PostgreSQL thật (chạy bằng Docker): cấu trúc bảng khớp với code, đọc/ghi dữ liệu được
        ├── ContractExampleTest.java # Bài kiểm tra: file ví dụ trong contracts/ được Java đọc đúng (bảo đảm Engine và sidecar hiểu dữ liệu giống nhau)
        └── WorkspaceFlowIT.java     # Bài kiểm tra luồng chính: tạo Workspace từ dự án mẫu → quét → xem cây → dò luồng → diễn giải (AI tắt)
```

#### 4.4.2. Nền tảng (`config/` · `common/` · `source/`)

```text
config/
├── FlowTracerProperties.java        # Gom toàn bộ thiết lập riêng của tool (thư mục mã nguồn, thư mục dữ liệu, số luồng quét,
│                                    #   cách gọi sidecar, chọn AI, hạn mức AI/ngày) thành một chỗ để các phần khác dùng
├── WebConfig.java                   # Thiết lập web: mọi API bắt đầu bằng /api; giao diện web được phục vụ ở địa chỉ gốc "/"
├── ExecutorConfig.java              # Tạo "nhóm luồng chạy nền" để quét mã nguồn song song mà không làm treo các yêu cầu khác
└── OpenApiConfig.java               # Thông tin cho trang tài liệu API tự sinh (Swagger) — giao diện web dùng nó để tự sinh kiểu dữ liệu

common/
├── error/                           # Cách báo lỗi thống nhất cho toàn bộ API
│   ├── ErrorCode.java               # Danh sách mã lỗi của hệ thống (trùng tên Workspace, đang quét, đường dẫn không hợp lệ, AI đang tắt…)
│   │                                #   kèm mã HTTP tương ứng
│   ├── AppException.java            # Loại lỗi dùng khi vi phạm quy tắc nghiệp vụ — mang theo mã lỗi và câu thông báo tiếng Việt
│   └── GlobalExceptionHandler.java  # Bắt mọi lỗi xảy ra và trả về giao diện theo cùng một dạng { error: { code, message, details } };
│                                    #   giấu chi tiết kỹ thuật và thông tin bí mật
├── sse/
│   └── SseHub.java                  # Bộ đẩy dữ liệu thời gian thực tới giao diện (tiến độ quét, nội dung AI đang viết dần)
└── web/
    ├── NodeKeyCodec.java            # Mã hóa mã định danh thành phần để đặt an toàn trên URL (vì mã có ký tự đặc biệt như : # /)
    └── PageResponse.java            # Dạng dữ liệu trả về cho danh sách có phân trang (danh sách, tổng số, trang hiện tại, số dòng mỗi trang)

source/                              # AN TOÀN: nơi DUY NHẤT được đọc mã nguồn người dùng
├── SourceReader.java                # Khuôn mẫu các thao tác đọc mã nguồn được phép: liệt kê thư mục, duyệt file, đọc nội dung, đọc vài dòng
└── LocalSourceReader.java           # Bộ đọc mã nguồn thực tế trên máy — CHỈ đọc, không ghi; chặn mọi đường dẫn cố tình đi ra ngoài
                                     #   thư mục mã nguồn được phép (vd dùng "../")
```

#### 4.4.3. `workspace/` — Workspace & nguồn mã

**Phạm vi:** Nhóm 1, API A và API I4–I5. **Bảng:** `workspace`, `source`.
**Lưu ý:**
- Khai báo Source lồng nhau thì tự thêm exclude vào Source cha.
- Không cho xóa Workspace khi đang quét.
- Xuất/nhập Workspace gom dữ liệu từ `LinkOverrideService` và `FeatureService` qua service công khai của chúng.

```text
workspace/
├── controller/
│   ├── WorkspaceController.java     # Nhận các yêu cầu từ giao diện về Workspace: xem danh sách, tạo, sửa, xóa, xuất/nhập file (API A1–A5, A12–A13)
│   ├── SourceController.java        # Nhận các yêu cầu thêm/sửa/xóa thư mục mã nguồn của Workspace (API A7–A9; tải zip/Git là MỞ RỘNG)
│   ├── DetectionController.java     # Nhận yêu cầu "nhận diện công nghệ" cho các thư mục mã nguồn (API A10; gợi ý tách mã cũ là MỞ RỘNG)
│   ├── FolderBrowseController.java  # Nhận yêu cầu duyệt thư mục trên máy — để người dùng chọn thư mục bằng giao diện (API A6)
│   └── NoiseFilterController.java   # Nhận yêu cầu xem/sửa danh sách thành phần cần ẩn khỏi diagram (API I4–I5)
├── service/
│   ├── WorkspaceService.java        # Xử lý nghiệp vụ Workspace: tạo (không cho trùng tên), sửa, xóa (không cho xóa khi đang quét), đọc thiết lập
│   │                                #   — các chức năng khác muốn biết thông tin Workspace phải hỏi qua đây
│   ├── SourceService.java           # Xử lý nghiệp vụ thêm/sửa/xóa thư mục mã nguồn
│   ├── NestedSourceRules.java       # Quy tắc khi một thư mục nằm bên trong thư mục khác (vd mã PHP cũ nằm trong repo Backend):
│   │                                #   tự loại thư mục con khỏi thư mục cha để không file nào bị đọc 2 lần; chặn khai báo trùng
│   ├── StackDetectionService.java   # Chạy bước nhận diện công nghệ cho từng thư mục và lưu kết quả (vd "Spring Boot 3.3")
│   └── WorkspaceTransferService.java # Xuất cấu hình Workspace ra file JSON và nhập lại từ file — để sao lưu hoặc chuyển sang máy khác
├── repository/
│   ├── WorkspaceRepository.java     # Nơi đọc/ghi bảng workspaces trong CSDL
│   └── SourceRepository.java        # Nơi đọc/ghi bảng sources trong CSDL
├── entity/                          # Các class ứng với bảng trong CSDL
│   ├── Workspace.java               # Một dòng của bảng workspaces — một dự án đang được phân tích
│   ├── WorkspaceSettings.java       # Gói thiết lập riêng của một Workspace (luật nối đường dẫn, bộ lọc ẩn, độ sâu dò luồng…), lưu thành JSON
│   ├── DetectedStack.java           # Một công nghệ đã nhận diện được trên thư mục (tên, phiên bản, mức độ phù hợp)
│   └── Source.java                  # Một dòng của bảng sources — một thư mục mã nguồn kèm vai trò (Backend/Frontend/hệ thống cũ)
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── CreateWorkspaceRequest.java, UpdateWorkspaceRequest.java   # Yêu cầu tạo / sửa Workspace
    ├── SourceRequest.java           # Yêu cầu thêm / sửa một thư mục mã nguồn
    ├── WorkspaceResponse.java       # Thông tin Workspace trả về (kèm danh sách thư mục mã nguồn, lần quét gần nhất)
    ├── FolderEntryResponse.java     # Một thư mục con trả về cho trình duyệt thư mục
    ├── NoiseFiltersRequest.java, NoiseFiltersResponse.java        # Danh sách mẫu thành phần cần ẩn — gửi lên / trả về
    └── WorkspaceExportFile.java     # Định dạng file xuất/nhập Workspace
```

#### 4.4.4. `scan/` — Quét & phân tích mã nguồn

**Phạm vi:** Nhóm 2, API B. **Bảng:** `scan_run`, `source_file`.

**Pipeline một lần quét** (chạy nền trên virtual thread, mỗi giai đoạn báo tiến độ qua SSE):

`DISCOVER` → `DIFF` → `EXTRACT` → `RESOLVE` → `PERSIST` → `LINK` → `FINALIZE`

- **DISCOVER:** liệt kê file theo luật lọc, tính mã băm.
- **DIFF:** so với lần trước để biết file thêm/sửa/xóa. File *phụ thuộc* file đã đổi (theo import) cũng được đánh dấu phân tích lại.
- **EXTRACT:** Source Spring chạy pha 1 song song, lấy cache với file không đổi. Source Angular gọi sidecar một lần cho cả Source; sidecar tự lo pha 1 và pha 2.
- **RESOLVE:** chạy pha 2 cho Source Spring.
- **PERSIST:** `GraphWriteService.replaceSourceGraph` trong một transaction: upsert node theo key, xóa node không còn, thay edge của Source.
- **LINK:** `LinkingService.relinkWorkspace`.
- **FINALIZE:** cập nhật trạng thái và thống kê.

**Lưu ý:** mỗi Workspace chỉ có một lần quét chạy tại một thời điểm; file lỗi → `PARTIAL`; hủy hoặc lỗi thì giữ nguyên IR của lần quét thành công trước.

```text
scan/
├── controller/
│   ├── ScanController.java          # Nhận các yêu cầu về quét: bắt đầu quét, xem trạng thái, hủy, xem lịch sử, xem danh sách file lỗi
│   │                                #   (API B1, B3–B6; tự quét khi code đổi là MỞ RỘNG)
│   └── ScanEventsController.java    # Mở kênh để giao diện nhận tiến độ quét theo thời gian thực (thanh tiến độ) (API B2)
├── service/
│   ├── ScanService.java             # Xử lý nghiệp vụ quét: không cho 2 lần quét chạy cùng lúc, cho chạy nền, cho hủy, cho xem kết quả
│   ├── RunningScanRegistry.java     # Sổ ghi nhớ "Workspace nào đang quét" — dùng để chặn quét trùng, chặn xóa Workspace đang quét, và để hủy
│   └── FactsCache.java              # Kho lưu tạm "những gì đọc được từ mỗi file" trên ổ đĩa — file không đổi thì lần sau lấy lại, khỏi đọc lại
├── pipeline/                        # CỐT LÕI: các bước của một lần quét, chạy lần lượt
│   ├── ScanPipeline.java            # Người điều phối: chạy lần lượt các bước dưới đây, xử lý lỗi, cập nhật trạng thái, dừng khi bị hủy
│   ├── DiscoverStep.java            # Bước 1 — lập danh sách file cần đọc và tính "dấu vân tay" (mã băm) của từng file
│   ├── DiffStep.java                # Bước 2 — so với lần quét trước để biết file nào mới, đã sửa, đã xóa, giữ nguyên
│   ├── ExtractStep.java             # Bước 3 — đọc các file cần đọc lại (Java đọc ngay trong Engine; Angular giao cho sidecar)
│   ├── ResolveStep.java             # Bước 4 — ghép kết quả các file Java lại để hiểu chúng liên quan nhau thế nào
│   ├── PersistStep.java             # Bước 5 — lưu kết quả phân tích vào CSDL (lưu trọn vẹn một lần, lỗi giữa chừng thì giữ nguyên dữ liệu cũ)
│   ├── LinkStep.java                # Bước 6 — nối lại các lời gọi API ở Frontend với API ở Backend
│   └── ProgressReporter.java        # Gửi thông báo tiến độ của từng bước lên giao diện
├── adapter/                         # Quản lý các bộ phân tích theo công nghệ
│   ├── AdapterRegistry.java         # Danh sách các bộ phân tích tool đang có (Spring, Angular…) — nơi DUY NHẤT đăng ký thêm bộ phân tích mới
│   ├── SidecarStackAdapter.java     # Bộ phân tích "đại diện" cho các công nghệ chạy trong sidecar (Angular…) — bề ngoài giống bộ Spring,
│   │                                #   bên trong chuyển việc cho sidecar làm
│   └── SidecarRunner.java           # Chạy chương trình sidecar: gửi danh sách file cần phân tích, nhận kết quả về, đọc tiến độ,
│                                    #   dừng nếu chạy quá lâu, và kiểm tra kết quả đúng định dạng trong contracts/
├── repository/
│   ├── ScanRunRepository.java       # Nơi đọc/ghi bảng scan_runs trong CSDL
│   └── SourceFileRepository.java    # Nơi đọc/ghi bảng source_files trong CSDL
├── entity/                          # Các class ứng với bảng trong CSDL
│   ├── ScanRun.java                 # Một dòng của bảng scan_runs — một lần quét; tự chặn chuyển trạng thái sai
│   │                                #   (vd lần quét đã xong không thể quay lại "đang chạy")
│   ├── ScanTrigger.java, ScanStatus.java, ParseStatus.java   # Danh sách giá trị: kiểu quét (lần đầu / quét lại); trạng thái lần quét;
│   │                                #   kết quả đọc một file (thành công / lỗi / bỏ qua)
│   └── SourceFile.java              # Một dòng của bảng source_files — một file đã đọc, kèm dấu vân tay và kết quả đọc (thành công hay lỗi)
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── StartScanRequest.java        # Yêu cầu bắt đầu quét (quét lần đầu hay quét lại)
    ├── ScanRunResponse.java         # Thông tin một lần quét (trạng thái, thống kê, thời gian)
    ├── ScanProgressEvent.java       # Một thông báo tiến độ gửi lên giao diện (đang ở bước nào, xong bao nhiêu file)
    └── ScanErrorItem.java           # Một file không đọc được kèm lý do
```

#### 4.4.5. `graph/` — Lưu trữ & khám phá "bản đồ" IR

**Phạm vi:** Nhóm 3, API C. **Bảng:** `ir_node`, `ir_edge`. Package này là **chủ sở hữu duy nhất của IR**: package khác đọc/ghi IR qua `GraphQueryService` / `GraphWriteService`.
**Lưu ý:** hai bảng IR dùng **JdbcClient ghi theo lô**, không dùng JPA, vì mỗi lần quét có hàng chục nghìn bản ghi.

```text
graph/
├── controller/
│   ├── TreeController.java          # Nhận yêu cầu lấy cây API Backend và cây thành phần Frontend để hiện ở cột trái (API C1–C2)
│   ├── SearchController.java        # Nhận yêu cầu tìm kiếm nhanh (theo đường dẫn API, nhãn nút, tên màn hình, tên bảng…) (API C3)
│   ├── NodeController.java          # Nhận yêu cầu xem chi tiết một thành phần và xem đoạn code của nó (API C4–C5)
│   └── OpenApiCheckController.java  # Nhận yêu cầu đối chiếu kết quả phân tích với tài liệu Swagger của dự án (API C6)
├── service/
│   ├── GraphQueryService.java       # Cung cấp cách ĐỌC bản đồ cho các chức năng khác (lấy một thành phần, lấy vùng xung quanh để dò luồng, tìm kiếm…)
│   ├── GraphWriteService.java       # Cung cấp cách GHI bản đồ cho các chức năng khác (thay toàn bộ kết quả của một thư mục sau khi quét…)
│   ├── TreeBuilder.java             # Dựng cây hiển thị: Backend theo Module → API; Frontend theo Ứng dụng → Màn hình → … → Nút bấm
│   ├── SourceSnippetService.java    # Lấy đoạn code quanh một thành phần để người dùng xem nhanh (đọc qua bộ đọc mã nguồn an toàn)
│   └── OpenApiCheckService.java     # Tải tài liệu Swagger của dự án, so với danh sách API tool tìm được, báo chỗ lệch và lấy thêm mô tả API
├── repository/
│   ├── IrNodeRepository.java        # Nơi đọc/ghi bảng ir_nodes (các thành phần) — ghi theo lô lớn vì mỗi lần quét có rất nhiều dòng
│   └── IrEdgeRepository.java       # Nơi đọc/ghi bảng ir_edges (các quan hệ giữa thành phần)
├── model/
│   └── IrNodeRow.java, IrEdgeRow.java   # Một dòng của bảng ir_nodes / ir_edges: thành phần (hoặc quan hệ) kèm các mã định danh trong CSDL
│                                    #   (hai bảng này ghi bằng JdbcClient nên không có entity JPA)
└── dto/                             # Dữ liệu trả về cho giao diện
    ├── TreeNodeResponse.java        # Một nút trên cây Backend/Frontend (nhãn, loại, các nút con, cảnh báo)
    ├── SearchResultResponse.java    # Một kết quả tìm kiếm
    ├── NodeDetailResponse.java      # Chi tiết một thành phần: thông tin, "được gọi từ", "gọi tới"
    ├── SourceSnippetResponse.java   # Một đoạn code kèm đường dẫn file và số dòng
    └── OpenApiReportResponse.java   # Báo cáo đối chiếu với Swagger: API khớp, chỉ có trong code, chỉ có trong Swagger
```

#### 4.4.6. `linking/` — Liên kết Frontend – Backend & độ tin cậy

**Phạm vi:** Nhóm 5, API E. **Bảng:** `link_override`. Luật liên kết nằm trong `workspace.settings`.
**Lưu ý CỐT LÕI:** AI chỉ được **chọn trong `candidates`**; không đạt thì giữ `UNRESOLVED`. Liên kết chốt tay luôn thắng kết quả tự động.

```text
linking/
├── controller/
│   ├── EdgeController.java          # Nhận yêu cầu xem chi tiết một liên kết (vì sao nối, chắc chắn đến đâu) và yêu cầu "nhờ AI chọn" (API E1–E2)
│   ├── LinkOverrideController.java  # Nhận yêu cầu xem / thêm / bỏ các liên kết người dùng chốt tay (API E3–E5)
│   ├── LinkRuleController.java      # Nhận yêu cầu xem / sửa các luật nối đường dẫn Frontend với Backend (API E6–E7)
│   └── RuntimeController.java       # (MỞ RỘNG) Nhận yêu cầu đối chiếu với ứng dụng đang chạy thật và dữ liệu theo dõi lúc chạy (API E8–E9)
├── service/
│   ├── LinkingService.java          # Xử lý việc nối toàn bộ lời gọi API Frontend với API Backend của một Workspace (chạy sau mỗi lần quét)
│   ├── EdgeResolutionService.java   # Xử lý "nhờ AI chọn": khi một liên kết có nhiều khả năng, gửi đoạn code và danh sách khả năng cho AI chọn
│   ├── AiChoiceRule.java            # Quy tắc chấp nhận câu trả lời của AI: chỉ nhận nếu AI chọn đúng một trong các khả năng có sẵn và đủ tự tin,
│   │                                #   ngược lại giữ là "không xác định"
│   ├── LinkOverrideService.java     # Xử lý liên kết chốt tay (tạo, xóa, liệt kê) — chức năng dò luồng và xuất/nhập Workspace cũng dùng qua đây
│   ├── LinkRuleService.java         # Xử lý luật nối đường dẫn: gộp luật tool tự tìm và luật người dùng nhập; sửa luật thì nối lại ngay
│   └── RuntimeVerificationService.java  # (MỞ RỘNG) Xác nhận liên kết bằng dữ liệu lúc ứng dụng chạy thật — chỉ giữ thông tin kỹ thuật, bỏ dữ liệu nghiệp vụ
├── repository/
│   └── LinkOverrideRepository.java  # Nơi đọc/ghi bảng link_overrides trong CSDL
├── entity/
│   ├── LinkOverride.java            # Một dòng của bảng link_overrides — một quyết định chốt tay: "ghim" liên kết đúng hoặc "bỏ" liên kết sai
│   └── OverrideDecision.java        # Danh sách hai loại quyết định: GHIM / BỎ
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── EdgeDetailResponse.java      # Chi tiết một liên kết: mức tin cậy, lý do, các khả năng, vị trí trong code
    ├── ResolveEdgeRequest.java      # Yêu cầu "nhờ AI chọn" (có bỏ qua kết quả đã lưu hay không)
    ├── LinkOverrideRequest.java, LinkOverrideResponse.java   # Yêu cầu chốt tay một liên kết / thông tin liên kết đã chốt
    └── LinkRulesRequest.java, LinkRulesResponse.java         # Luật nối đường dẫn gửi lên / trả về
```

#### 4.4.7. `tracing/` — Dò vết & trực quan hóa

**Phạm vi:** Nhóm 4, API D. **Bảng:** không có. Trace tính lại được bất cứ lúc nào (`trace_id` tất định) và được giữ trong cache bộ nhớ.

```text
tracing/
├── controller/
│   ├── TraceController.java         # Nhận yêu cầu "dò luồng từ thành phần này" và yêu cầu xuất diagram ra file (API D1–D2)
│   └── FlowchartController.java     # (MỞ RỘNG) Nhận yêu cầu vẽ flowchart (API D3)
├── service/
│   ├── TracingService.java         # Xử lý dò luồng: lấy vùng bản đồ liên quan → áp liên kết chốt tay và bộ lọc ẩn → chạy bộ dò luồng;
│   │                                #   gặp chỗ chưa chắc chắn thì nhờ AI chọn rồi dò lại — các chức năng khác cũng gọi dò luồng qua đây
│   ├── TraceCache.java              # Bộ nhớ tạm giữ các luồng vừa dò để mở lại nhanh; tự xóa khi Workspace được quét lại
│   └── TraceExportService.java      # Xuất luồng thành mã Mermaid hoặc file ảnh SVG để tải về
└── dto/                             # Dữ liệu giao diện gửi lên (dò từ đâu, mức chi tiết) và Engine trả về (luồng đã dò)
    ├── BuildTraceRequest.java       # Yêu cầu dò luồng: bắt đầu từ thành phần nào, mức chi tiết, dò sâu tối đa bao nhiêu bước
    ├── TraceResponse.java           # Luồng đã dò: các cột, các mũi tên, các khối nhánh, cảnh báo
    └── ExportFormat.java            # Danh sách định dạng xuất: Mermaid / SVG
```

#### 4.4.8. `explanation/` — Diễn giải & dữ liệu mẫu

**Phạm vi:** Nhóm 6, API F: panel phải, tab Diễn giải và tab Kịch bản. **Bảng:** không có (nội dung AI lưu trong cache của `ai`).
**Lưu ý CỐT LÕI:**
- Phần tất định (đầu vào/đầu ra, ràng buộc, khung kịch bản) luôn có.
- Mục đích chỉ gọi AI khi không có `@Operation`, Javadoc hay tài liệu đặc tả.
- Dữ liệu mẫu do AI sinh **một lần** từ JSON Schema, kiểm tra khớp rồi cache; lỗi hoặc tắt AI thì dùng bộ sinh dự phòng.
- Kịch bản do AI làm mượt phải giữ nguyên số bước và thành phần.

```text
explanation/
├── controller/
│   ├── ExplanationController.java   # Nhận yêu cầu lấy nội dung tab Diễn giải của một thành phần (có thể nhận dần từng phần khi AI đang viết) (API F1)
│   ├── MockDataController.java      # Nhận yêu cầu lấy dữ liệu mẫu (request/response mẫu) của một API hoặc nút bấm (API F2)
│   ├── ScenarioController.java      # Nhận yêu cầu lấy nội dung tab Kịch bản của một luồng đã dò (API F3)
│   └── RegenerateController.java    # Nhận yêu cầu "Sinh lại" nội dung AI khi người dùng thấy chưa tốt (API F4)
├── service/
│   ├── ExplanationService.java      # Gom đủ các mục của tab Diễn giải: mục đích, thuộc chức năng, đầu vào/đầu ra, các lỗi có thể gặp, ràng buộc
│   ├── PurposeResolver.java         # Tìm câu "Mục đích" theo thứ tự: mô tả Swagger → ghi chú trong code → tài liệu đặc tả → cuối cùng mới nhờ AI viết
│   ├── SpecDocReader.java           # Tìm đoạn mô tả API/màn hình trong các file tài liệu .md có sẵn trong repo của dự án
│   ├── MockDataService.java         # Lấy dữ liệu mẫu: đã lưu thì dùng lại; chưa có thì nhờ AI sinh một lần theo cấu trúc dữ liệu, kiểm tra đúng
│   │                                #   cấu trúc rồi lưu; AI tắt/lỗi thì dùng bộ sinh dự phòng không cần AI
│   ├── ScenarioService.java         # Tạo tab Kịch bản: dựng các câu theo mẫu từ luồng đã dò; nếu bật thì nhờ AI viết lại câu cho tự nhiên hơn
│   └── ScenarioPolishRule.java      # Quy tắc chấp nhận câu AI viết lại: phải giữ nguyên số bước, thứ tự và tên thành phần; sai thì dùng câu gốc
└── dto/                             # Dữ liệu trả về cho giao diện
    ├── ExplanationResponse.java     # Nội dung tab Diễn giải (mục đích, đầu vào/ra dạng JSON, ràng buộc…)
    ├── MockDataResponse.java        # Dữ liệu mẫu kèm nguồn sinh (do AI hay do bộ sinh dự phòng)
    ├── ScenarioResponse.java        # Các bước tab Kịch bản (số thứ tự, câu mô tả, chi tiết kỹ thuật)
    └── RegenerateRequest.java       # Yêu cầu sinh lại nội dung AI (sinh lại việc gì, cho thành phần nào)
```

#### 4.4.9. `ai/` — Cổng AI dùng chung

**Phạm vi:** Nhóm 9 (phần AI), API I1–I3, I6. **Bảng:** `ai_artifact`, `llm_call_log`, `app_setting`.
**Lưu ý CỐT LÕI:** mọi lời gọi AI đi qua `AiGateway.generate(...)` theo đúng thứ tự:
1. Mục đích có được bật không?
2. Tra **cache** theo `input_hash`.
3. Kiểm tra **hạn mức** token/ngày.
4. **Che secret** trong đầu vào.
5. Gọi `LlmProvider`.
6. **Kiểm tra đầu ra** theo schema.
7. Lưu cache và ghi nhật ký (không lưu nội dung prompt).

**Cấm** package khác dùng Spring AI hoặc gọi provider trực tiếp.

```text
ai/
├── controller/
│   ├── AiSettingsController.java    # Nhận yêu cầu xem/sửa cấu hình AI (bật/tắt từng mục đích) và kiểm tra kết nối AI (API I1–I3)
│   └── AiUsageController.java       # Nhận yêu cầu xem thống kê sử dụng AI: số lần gọi, số token, tỷ lệ lỗi (API I6)
├── service/
│   ├── AiGateway.java               # CỔNG DUY NHẤT để gọi AI — mọi chức năng muốn dùng AI đều đi qua đây và tuân 7 bước kiểm soát ở trên
│   ├── PromptRegistry.java          # Nơi lấy câu lệnh mẫu gửi AI (prompt) cho từng việc, kèm phiên bản và định dạng kết quả mong muốn
│   ├── CacheKeys.java               # Tạo "dấu vân tay" cho mỗi yêu cầu AI — cùng nội dung thì cùng dấu, nên lấy lại kết quả cũ khỏi gọi AI lần nữa
│   ├── TokenBudget.java             # Theo dõi hạn mức AI mỗi ngày — vượt hạn mức thì ngừng gọi AI và báo lỗi
│   ├── SecretRedactor.java          # Che mật khẩu, token, khóa bí mật, chuỗi kết nối CSDL trong đoạn code TRƯỚC khi gửi cho AI
│   └── AiSettingsService.java       # Xử lý cấu hình AI: lưu trạng thái bật/tắt từng việc, kiểm tra kết nối, tổng hợp thống kê sử dụng
├── provider/                        # Các "nhà cung cấp" AI có thể dùng
│   ├── LlmProvider.java             # Khuôn mẫu chung của một nhà cung cấp AI — nhờ vậy đổi Gemini sang Claude chỉ cần đổi cấu hình
│   ├── SpringAiLlmProvider.java     # Kết nối thật tới Gemini / Claude / dịch vụ tương thích OpenAI (thông qua thư viện Spring AI)
│   ├── NoopLlmProvider.java         # Nhà cung cấp "tắt AI" — luôn trả lời "AI không khả dụng" để các chức năng dùng cách không cần AI
│   ├── LlmProviderConfig.java       # Chọn nhà cung cấp theo cấu hình; API key chỉ đọc từ biến môi trường, không lưu, không in ra log
│   └── LlmRequest.java, LlmResult.java   # Dạng dữ liệu một yêu cầu gửi AI và một câu trả lời nhận về (kèm số token đã dùng)
├── repository/
│   ├── AiArtifactRepository.java    # Nơi đọc/ghi bảng ai_artifacts (kết quả AI đã lưu)
│   ├── LlmCallLogRepository.java    # Nơi đọc/ghi bảng llm_call_logs (nhật ký gọi AI)
│   └── AppSettingRepository.java    # Nơi đọc/ghi bảng app_settings (cấu hình chung chỉnh trên giao diện)
├── entity/
│   ├── AiArtifact.java              # Một dòng của bảng ai_artifacts — một kết quả AI đã được kiểm tra và lưu để dùng lại
│   ├── LlmCallLog.java              # Một dòng của bảng llm_call_logs — nhật ký một lần gọi AI (không lưu nội dung gửi đi)
│   ├── AppSetting.java              # Một dòng của bảng app_settings — một cấu hình chung (vd bật/tắt từng mục đích dùng AI)
│   ├── AiPurpose.java               # Danh sách các việc ĐƯỢC PHÉP nhờ AI làm (viết mục đích, làm mượt kịch bản, sinh dữ liệu mẫu, chọn liên kết…)
│   └── LlmCallStatus.java           # Danh sách kết quả một lần gọi AI: thành công / lỗi / bị loại vì sai định dạng / vượt hạn mức
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── AiSettingsResponse.java      # Cấu hình AI hiện tại (không bao giờ chứa API key)
    ├── UpdateAiPurposesRequest.java # Yêu cầu bật/tắt từng việc dùng AI
    ├── AiConnectionTestResponse.java # Kết quả kiểm tra kết nối AI (thành công/thất bại, độ trễ)
    └── AiUsageResponse.java         # Thống kê sử dụng AI

main/resources/prompts/              # Câu lệnh mẫu gửi cho AI — mỗi việc một file, đổi nội dung thì tăng số phiên bản (v1 → v2)
├── purpose-text.v1.st               # Lệnh nhờ AI viết 1–3 câu tiếng Việt đời thường mô tả mục đích của một API/nút bấm
├── scenario-polish.v1.st            # Lệnh nhờ AI viết lại các câu kịch bản cho tự nhiên, giữ nguyên số bước và thành phần
├── mock-data.v1.st                  # Lệnh nhờ AI sinh dữ liệu mẫu đúng cấu trúc dữ liệu cho trước (không dùng dữ liệu thật)
├── edge-resolution.v1.st            # Lệnh nhờ AI chọn một trong các khả năng của một liên kết chưa chắc chắn, kèm lý do
└── (mở rộng) feature-summary.v1.st · legacy-match.v1.st · migration-checklist.v1.st   # Lệnh cho tóm tắt nhóm chức năng, ghép luồng cũ–mới, checklist đối soát
```

#### 4.4.10. `feature/` và `comparison/` — MỞ RỘNG

```text
feature/                             # (MỞ RỘNG) Chức năng nhóm 7: gom nhiều API/nút bấm liên quan thành một "nhóm chức năng" để xem tổng thể
├── controller/
│   └── FeatureController.java       # Nhận yêu cầu tạo/sửa/xóa/xem nhóm chức năng, lấy gợi ý nhóm, xem diagram và diễn giải cả nhóm (API G1–G9)
├── service/
│   ├── FeatureService.java          # Xử lý nghiệp vụ nhóm chức năng — chức năng xuất/nhập Workspace cũng lấy dữ liệu nhóm qua đây
│   ├── FeatureSuggester.java        # Tự gợi ý nhóm chức năng (không dùng AI): gom các API mà cùng một màn hình gọi, cùng module, cùng bảng dữ liệu
│   └── FeatureTraceService.java     # Ghép luồng của từng thành viên thành một diagram lớn cho cả nhóm chức năng
├── repository/
│   ├── FeatureRepository.java       # Nơi đọc/ghi bảng features trong CSDL
│   └── FeatureMemberRepository.java # Nơi đọc/ghi bảng feature_members trong CSDL
├── entity/
│   ├── Feature.java                 # Một dòng của bảng features — một nhóm chức năng
│   ├── FeatureMember.java           # Một dòng của bảng feature_members — một thành phần thuộc nhóm, kèm thứ tự trong quy trình
│   └── FeatureOrigin.java           # Nhóm do người dùng tự tạo hay lấy từ gợi ý
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về cho chức năng này

comparison/                          # (MỞ RỘNG) Chức năng nhóm 8: so sánh luồng hệ thống cũ (PHP) với hệ thống mới — không có bảng riêng
├── controller/
│   └── ComparisonController.java    # Nhận yêu cầu gợi ý thành phần để so sánh, so hai luồng, tìm luồng cũ tương ứng, tạo checklist (API H1–H4)
├── service/
│   ├── LegacyMatcher.java           # Tìm luồng ở hệ thống cũ ứng với một API mới: ưu tiên tài liệu ghi sẵn → độ giống tên/trường/bảng → cuối cùng nhờ AI
│   ├── TraceDiffService.java        # So hai luồng: thành phần/bảng nào chỉ có ở một bên, bước nào tương ứng nhau
│   └── MigrationChecklistService.java   # Nhờ AI lập danh sách điểm lệch giữa luồng cũ và mới (thiếu kiểm tra dữ liệu, thiếu nhánh lỗi, khác quyền…)
└── dto/                             # Dữ liệu trả về cho giao diện
```

---

## 5. `analyzer-node/` — Node Analyzer (sidecar)

**Vai trò:** CLI **không trạng thái**. Engine gọi mỗi lần quét một Source Angular (hoặc PHP ở bản mở rộng).
**Giao thức:**
- Nhận `analyze-request` qua stdin: danh sách file do Engine đã lọc sẵn, kèm mã băm.
- Tự làm pha 1 (có cache theo mã băm) và pha 2.
- In `IrFragment` ra stdout; in tiến độ từng dòng ra stderr.

**Lưu ý:** chỉ **đọc** đúng các file được liệt kê, bên trong `sourceRoot`; không gọi mạng; không ghi gì ngoài `cacheDir`.

```text
analyzer-node/
├── package.json                     # File khai báo của sidecar: tên chương trình, thư viện cần dùng, các lệnh build/test/lint
├── tsconfig.json                    # Thiết lập biên dịch TypeScript (chuẩn ngôn ngữ, kiểm tra kiểu chặt chẽ…)
├── src/
│   ├── cli.ts                       # Điểm khởi động của sidecar: nhận yêu cầu từ Engine, chọn bộ phân tích phù hợp, chạy, trả kết quả
│   │                                #   và mã kết quả (thành công / lỗi đầu vào / lỗi phân tích) theo contracts/sidecar-protocol.md
│   ├── protocol/                    # Phần "giao tiếp với Engine"
│   │   ├── generated/               # Các kiểu dữ liệu TỰ SINH từ contracts/ (lệnh npm run gen:contracts) — không sửa tay
│   │   ├── node-keys.ts             # Quy tắc đặt mã định danh thành phần phía sidecar — PHẢI giống hệt NodeKeys.java của Engine
│   │   └── progress.ts              # Gửi thông báo tiến độ (đang ở bước nào, xong bao nhiêu file) về cho Engine
│   ├── runtime/                     # Phần "chạy" chung cho mọi bộ phân tích
│   │   ├── file-access.ts           # Bộ đọc file AN TOÀN: chỉ đọc đúng các file Engine cho phép, chặn đọc ra ngoài thư mục mã nguồn
│   │   ├── facts-cache.ts           # Kho lưu tạm "những gì đọc được từ mỗi file" — file không đổi thì lần sau lấy lại, khỏi đọc lại
│   │   └── pipeline.ts              # Điều phối 2 bước: đọc từng file (nhiều file cùng lúc) → ghép kết quả → sắp xếp ổn định trước khi trả về
│   └── adapters/                    # Các bộ phân tích theo công nghệ
│       ├── adapter.ts               # Khuôn mẫu chung mà mọi bộ phân tích trong sidecar phải tuân theo (đọc từng file + ghép kết quả)
│       ├── registry.ts              # Danh sách các bộ phân tích sidecar đang có (hiện chỉ Angular) — Engine chọn theo tên
│       ├── angular/                 # Bộ phân tích Angular (MVP)
│       │   ├── index.ts             # "Cửa vào" của bộ phân tích Angular — nối bước đọc từng file với bước ghép kết quả
│       │   ├── facts.ts             # Kiểu dữ liệu chứa "những gì đọc được từ MỘT file Angular" (class, route, lời gọi API, nút bấm, nhãn…)
│       │   ├── workspace/           # Đọc cấu hình tổng của dự án Angular
│       │   │   ├── angular-json.reader.ts      # Đọc angular.json để biết dự án có những ứng dụng/thư viện nào và nằm ở đâu
│       │   │   ├── tsconfig-paths.reader.ts    # Đọc các đường dẫn tắt khi import (vd 'shared-lib') để biết chúng trỏ tới thư mục nào
│       │   │   └── federation.reader.ts        # Với dự án microfrontend: biết ứng dụng con nào chia sẻ màn hình nào, ứng dụng chính nạp chúng ở đâu
│       │   ├── extract/             # BƯỚC 1 — đọc từng file riêng lẻ
│       │   │   ├── extract-file.ts             # Điều phối: xem file thuộc loại nào (code .ts, giao diện .html, file ngôn ngữ…) rồi gọi bộ đọc tương ứng
│       │   │   ├── class-decl.extractor.ts     # Lấy thông tin component/service: tên, giao diện đi kèm, các thành phần nó dùng
│       │   │   ├── route.extractor.ts          # Lấy bảng điều hướng: đường dẫn nào mở màn hình nào
│       │   │   ├── provider.extractor.ts       # Lấy khai báo "khi cần X thì dùng class Y" — để biết lời gọi thực sự chạy vào đâu
│       │   │   ├── method-call.extractor.ts    # Lấy các lời gọi hàm trong mỗi hàm, theo thứ tự, kèm điều kiện bao quanh
│       │   │   ├── http-call.extractor.ts      # Lấy các chỗ gọi API lên Backend: phương thức (GET/POST…), đường dẫn, dữ liệu gửi kèm
│       │   │   ├── ui-action.extractor.ts      # Lấy các thao tác chỉ trên giao diện: mở hộp thoại, chuyển trang, đổi tab, xóa form
│       │   │   ├── constant.extractor.ts       # Lấy các hằng số dạng chuỗi — nguyên liệu để tính ra đường dẫn API đầy đủ
│       │   │   ├── template.extractor.ts       # Đọc file giao diện: tìm các nút/ô nhập có sự kiện (bấm, đổi giá trị…), form, tab, nhãn hiển thị
│       │   │   └── i18n.extractor.ts           # Đọc file ngôn ngữ tiếng Việt: mã nhãn → chữ hiển thị
│       │   ├── resolve/             # BƯỚC 2 — ghép kết quả mọi file
│       │   │   ├── resolve-workspace.ts        # Điều phối bước 2 và tạo kết quả phân tích cuối cùng của thư mục Frontend
│       │   │   ├── symbol-table.ts             # Danh bạ: mỗi tên được import thực sự được khai báo ở file nào
│       │   │   ├── di.resolver.ts              # Xác định mỗi component/service thực sự dùng class nào (có nhiều khả năng thì ghi lại làm ứng viên)
│       │   │   ├── call.resolver.ts            # Nối chuỗi lời gọi: component → các lớp xử lý trung gian → chỗ gọi API
│       │   │   ├── url-evaluator.ts            # Tính ra đường dẫn API đầy đủ từ hằng số và chuỗi ghép; phần không biết trước thì đánh dấu là biến
│       │   │   ├── screen-tree.builder.ts      # Dựng cây giao diện: Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Nút bấm
│       │   │   ├── element.builder.ts          # Tạo từng nút bấm/ô nhập trên cây, nối với hàm nó gọi, gắn nhãn tiếng Việt
│       │   │   └── label.resolver.ts           # Đổi mã nhãn thành chữ tiếng Việt hiển thị trên giao diện
│       │   ├── config/
│       │   │   ├── proxy-config.reader.ts      # Đọc proxy.conf.json để biết đường dẫn API bị đổi thế nào trước khi tới Backend (vd bỏ tiền tố /gateway)
│       │   │   └── environment.reader.ts       # Đọc file môi trường (environment*.ts) để lấy địa chỉ gốc của API
│       │   └── noise-filters.ts                # Danh sách mặc định các thành phần cần ẩn khỏi diagram ở dự án Angular (ghi log, xử lý hiển thị…)
│       └── php-legacy/              # (MỞ RỘNG) Bộ phân tích mã PHP của hệ thống cũ
└── test/
    ├── angular/*.spec.ts            # Kiểm thử từng bộ đọc/ghép Angular bằng các đoạn code TỰ VIẾT
    ├── contract.spec.ts             # Kiểm thử: dữ liệu ví dụ trong contracts/ đúng định dạng quy định
    ├── proxy-config.spec.ts         # Kiểm thử bộ đọc proxy.conf.json
    └── golden.spec.ts               # Kiểm thử tổng: phân tích dự án mẫu fixtures/angular-shop-web rồi so với kết quả chuẩn đã lưu
```

---

## 6. `web/` — Web UI (Angular 21)

**Tổ chức:** theo tính năng; standalone components, **signals** cho state, `OnPush`. Kiểu dữ liệu API **sinh từ OpenAPI** của Engine. Chuỗi giao diện tiếng Việt tập trung trong `core/i18n`.

```text
web/
├── angular.json, package.json, tsconfig*.json   # Các file cấu hình dự án Angular: cách build/chạy/test, thư viện cần dùng, thiết lập TypeScript
├── eslint.config.js                 # Luật kiểm tra chất lượng code giao diện (chạy bằng npm run lint)
├── proxy.conf.json                  # Khi chạy giao diện lúc phát triển: chuyển mọi yêu cầu /api sang Engine đang chạy ở cổng 8600
└── src/
    ├── main.ts, index.html, styles.scss   # Điểm khởi động của giao diện, trang HTML gốc, và kiểu dáng (màu, font) dùng chung
    └── app/
        ├── app.ts, app.html, app.scss   # Khung ngoài cùng của giao diện (thanh tiêu đề + vùng hiển thị từng màn hình)
        ├── app.config.ts            # Bật các tính năng chung của ứng dụng: điều hướng giữa màn hình, gọi API lên Engine
        ├── app.routes.ts            # Bảng điều hướng: địa chỉ nào mở màn hình nào (danh sách Workspace, màn hình chính, cài đặt, cấu hình AI)
        ├── core/                    # Các dịch vụ dùng chung cho cả giao diện
        │   ├── api/
        │   │   ├── generated/       # Kiểu dữ liệu API TỰ SINH từ tài liệu Swagger của Engine (lệnh npm run gen:api) — không sửa tay
        │   │   └── *.api.ts         # Các lớp gọi API lên Engine, mỗi nhóm API (A–I) một file
        │   ├── sse/sse-client.service.ts        # Nhận dữ liệu thời gian thực từ Engine (tiến độ quét, nội dung AI đang viết), tự kết nối lại khi rớt
        │   ├── interceptors/api-error.interceptor.ts # Bắt lỗi từ API và đổi thành thông báo tiếng Việt dễ hiểu cho người dùng
        │   └── i18n/vi.ts           # Toàn bộ chữ hiển thị trên giao diện (tiếng Việt), gom một chỗ để dễ sửa
        ├── shared/ui/               # Các mảnh giao diện dùng lại nhiều nơi: khối hiển thị JSON, khối xem code, nhãn GET/POST, chú giải
        │                            #   nét vẽ, nhãn "AI", vùng chia cột kéo được, cây danh sách dài, trạng thái trống/đang tải
        └── features/                # Mỗi thư mục là một nhóm màn hình
            ├── workspaces/          # Màn hình danh sách Workspace, tạo Workspace, chọn thư mục, trang cài đặt Workspace, thanh tiến độ quét
            ├── explorer/            # MÀN HÌNH CHÍNH 3 CỘT
            │   ├── explorer.page.ts            # Bố cục màn hình chính: thanh trên + cột trái + vùng vẽ diagram + cột phải
            │   ├── explorer.store.ts           # Nơi giữ trạng thái màn hình (đang mở Workspace nào, đang chọn thành phần nào, đang xem luồng nào…)
            │   ├── top-bar/                    # Thanh trên: trạng thái quét, nút Quét lại, đèn báo AI, ô tìm kiếm nhanh (Ctrl+K)
            │   ├── left-panel/                 # Cột trái: tab cây Backend, tab cây Frontend, tab nhóm chức năng (mở rộng)
            │   ├── canvas/                     # Vùng giữa: vẽ diagram
            │   │   ├── diagram-canvas.component.ts   # Vùng vẽ: phóng to/thu nhỏ, kéo, hiện hướng dẫn khi chưa chọn gì, cảnh báo bước chưa xác định
            │   │   ├── diagram-toolbar.component.ts  # Thanh công cụ của diagram: đổi mức chi tiết, xuất file, mở so sánh (mở rộng)
            │   │   └── sequence/                     # Các phần vẽ sequence diagram: tính vị trí các cột/mũi tên, vẽ cột, mũi tên, khối nhánh
            │   └── right-panel/                # Cột phải: tab Diễn giải, tab Kịch bản, ô chi tiết liên kết (Nhờ AI chọn / Chốt tay), hộp xem code
            ├── compare/             # (MỞ RỘNG) Màn hình so sánh 2 diagram cạnh nhau và checklist đối soát cũ – mới
            └── ai-settings/         # Màn hình cấu hình AI: xem đang dùng AI nào, bật/tắt từng việc, kiểm tra kết nối, xem thống kê
```

---

## 7. Thư mục phụ trợ

```text
fixtures/                            # Các dự án mẫu nhỏ TỰ VIẾT 100% — dùng để kiểm thử tool và demo công khai
├── README.md                        # Quy tắc dùng fixtures: không sao chép code công ty; mỗi dự án mẫu cố ý chứa các "ca khó" cần kiểm thử
├── spring-shop-api/                 # Dự án Backend Spring Boot mẫu, chứa các tình huống khó: một interface có 2 class xử lý, câu truy vấn
│                                    #   ghép từ nhiều đoạn, dữ liệu trả về được bọc chung, thông báo lỗi tiếng Việt, bảng dùng chung có cột phân loại
├── angular-shop-web/                # Dự án Frontend Angular mẫu, chứa các tình huống khó: nhiều ứng dụng con, màn hình nạp sau, hộp thoại,
│                                    #   đường dẫn API ghép từ hằng số ở thư viện khác, proxy đổi đường dẫn, nhãn tiếng Việt, nút bấm tự viết
├── php-legacy-mini/                 # (MỞ RỘNG) Dự án PHP mẫu mô phỏng hệ thống cũ
└── golden/                          # Các "kết quả chuẩn" đã lưu khi phân tích dự án mẫu — test so kết quả mới với đây để phát hiện thay đổi ngoài ý muốn

scripts/                             # Các công cụ chạy tay
├── benchmark.mjs                    # Đo tốc độ: quét lần đầu, quét lại, dò luồng mất bao lâu (so với chỉ tiêu NFR-01 → NFR-04)
├── evaluate-accuracy.mjs            # Đo độ chính xác trên dự án demo (CHỈ chạy trên máy cá nhân) — chỉ in ra con số tổng hợp, không in code
└── check-forbidden-terms.mjs        # Chặn lỡ commit các từ khóa nội bộ của công ty (danh sách từ khóa để trong file .forbidden-terms, không đưa lên Git)

docker/
├── Dockerfile                       # Công thức đóng gói toàn bộ tool (giao diện + Engine + sidecar) thành một "image" Docker chạy được ở mọi máy
└── docker-compose.yml               # Kịch bản chạy tool bằng một lệnh: bật Engine (cổng 8600, chỉ máy cục bộ truy cập) và CSDL PostgreSQL;
                                     #   gắn thư mục mã nguồn cần phân tích vào ở chế độ chỉ đọc

docs/
├── templates/ · UI/                 # File mẫu tham khảo cho tài liệu; thiết kế giao diện (làm sau)
├── diagrams/database.dbml           # Sơ đồ CSDL (xem bằng trang dbdiagram.io)
├── progress/                        # Nhật ký công việc — mỗi giai đoạn trong roadmap một file báo cáo
├── evaluation.md                    # Kết quả đo tốc độ và độ chính xác (chỉ ghi số liệu tổng hợp)
└── AI_Viettel/                      # Tài liệu nội bộ Viettel — đã được Git bỏ qua, KHÔNG bao giờ đưa lên repo
```

---

## 8. Phụ lục A — Package chức năng ↔ bảng ↔ API ↔ màn hình

| Nhóm chức năng | Package `flowtracer-app` | Bảng sở hữu | Nhóm API | Thư mục Web UI |
| :--- | :--- | :--- | :--- | :--- |
| 1. Workspace & nguồn mã | `workspace` | `workspace`, `source` | A (+ I4–I5) | features/workspaces |
| 2. Quét & phân tích | `scan` (+ adapter-spring, analyzer-node) | `scan_run`, `source_file` | B | features/workspaces, explorer/top-bar |
| 3. Khám phá cấu trúc | `graph` | `ir_node`, `ir_edge` | C | explorer/left-panel, top-bar |
| 4. Dò vết & trực quan hóa | `tracing` (+ core tracer, diagram) | — | D | explorer/canvas |
| 5. Liên kết FE–BE & độ tin cậy | `linking` (+ core linker) | `link_override` | E | explorer/right-panel, workspace settings |
| 6. Diễn giải & dữ liệu mẫu | `explanation` (+ core scenario, explain, mock) | — | F | explorer/right-panel |
| 7. Nhóm chức năng *(mở rộng)* | `feature` | `feature`, `feature_member` | G | explorer/left-panel |
| 8. So sánh cũ – mới *(mở rộng)* | `comparison` (+ analyzer-node php-legacy) | — | H | features/compare |
| 9. Cấu hình AI & hệ thống | `ai` (+ actuator health) | `ai_artifact`, `llm_call_log`, `app_setting` | I | features/ai-settings |

## 9. Phụ lục B — Service công khai được phép gọi chéo package

Ngoài các service dưới đây, package chức năng **không** gọi thành phần nào của package khác. Kiểm tra bằng ArchUnit.

| Service công khai | Package | Được dùng bởi |
| :--- | :--- | :--- |
| `SourceReader` | source | workspace, scan, graph, explanation |
| `WorkspaceService` | workspace | scan, linking, tracing, explanation |
| `GraphQueryService` / `GraphWriteService` | graph | scan, linking, tracing, explanation, feature, comparison |
| `LinkingService` / `LinkOverrideService` / `EdgeResolutionService` | linking | scan, tracing, workspace (xuất/nhập) |
| `TracingService` | tracing | explanation, feature, comparison |
| `AiGateway` | ai | linking, explanation, feature, comparison |
| `FeatureService` | feature | workspace (xuất/nhập), comparison, explanation |
