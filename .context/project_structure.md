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
├── detect-request.schema.json       # Yêu cầu "nhận diện công nghệ" (lệnh detect): tên bộ phân tích + NỘI DUNG các file đặc trưng của một thư mục
├── detect-result.schema.json        # Kết quả nhận diện: điểm phù hợp, tên công nghệ, phiên bản, dấu hiệu đã dùng
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
├── Confidence.java                  # Danh sách các MỨC TIN CẬY của một quan hệ: chắc chắn / suy luận / chưa xác định (liên kết chốt tay lưu riêng, không phải một mức)
├── SourceRole.java                  # Danh sách VAI TRÒ của một thư mục code: Backend / Frontend / hệ thống cũ
├── IrNode.java                      # Kiểu dữ liệu mô tả MỘT THÀNH PHẦN trên bản đồ (tên, loại, nằm ở file nào dòng nào…)
├── IrEdge.java                      # Kiểu dữ liệu mô tả MỘT QUAN HỆ giữa hai thành phần (từ đâu tới đâu, loại gì, chắc chắn đến đâu, vì sao)
├── SourceLocation.java, Guard.java, Candidate.java   # Ba kiểu dữ liệu phụ: vị trí trong code (file + dòng); điều kiện bao quanh lời gọi
│                                    #   (vd "nếu mã đã tồn tại"); một "ứng viên" khi tool chưa chắc quan hệ trỏ tới đâu
├── NodeKeys.java                    # Quy tắc đặt "mã định danh" cố định cho mỗi thành phần, để nhận ra nó qua các lần quét
│                                    #   → ĐỔI QUY TẮC sẽ làm mất liên kết chốt tay và dữ liệu AI đã lưu
├── IrFragment.java                  # Kiểu dữ liệu chứa TOÀN BỘ KẾT QUẢ phân tích một thư mục code (mọi thành phần + quan hệ + cảnh báo)
├── LinkRule.java, NoiseFilter.java, Diagnostic.java   # Ba kiểu dữ liệu phụ: luật giúp nối URL Frontend với API Backend; mẫu thành phần
│                                    #   cần ẩn khỏi diagram cho gọn (ghi log, hàm tiện ích…); một cảnh báo/lỗi khi phân tích (vd file lỗi cú pháp)
├── trace/
│   ├── Trace.java, Participant.java, TraceStep.java, TraceBlock.java   # Các kiểu dữ liệu mô tả MỘT LUỒNG đã dò được: toàn bộ luồng; một cột
│   │                                #   trên sequence diagram (kèm VAI TRÒ: User / Frontend / Controller / Service / Repository / Database / bên ngoài); một mũi tên; một khối điều kiện (vd nhánh lỗi)
├── scenario/
│   └── Scenario.java                # Nội dung tab Kịch bản: các actor tham gia (tên cột + mô tả) và các dòng (bước n = mũi tên n, hoặc dòng điều kiện "Nếu …:")
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
│   ├── StackDetector.java           # Tự nhận diện một thư mục code dùng công nghệ gì (Spring? Angular?) để chọn đúng bộ phân tích
│   ├── FolderDetector.java          # Nhận diện thư mục người dùng chọn: tìm các gốc dự án trong 4 tầng, dò ngược lên thư mục cha
│   │                                #   (INSIDE_PROJECT), giữ pom.xml gốc của dự án nhiều module, gợi ý thư mục mã PHP cũ (chỉ báo)
│   ├── FolderAccess.java            # Cách lõi "nhìn" cây thư mục (liệt kê thư mục con, file, đọc file) — ứng dụng cấp qua SourceReader
│   └── FolderDetection.java         # Kết quả: VALID / INSIDE_PROJECT / NOT_FOUND, các gốc dự án, gốc gợi ý, gợi ý mã cũ
├── graph/                           # Nhóm xử lý "bản đồ" trong bộ nhớ
│   ├── GraphIndex.java              # Bản đồ thành phần + quan hệ dựng sẵn trong bộ nhớ để tra cứu nhanh
│   │                                #   (thành phần này gọi tới ai, được ai gọi, tìm theo loại…)
│   └── Subgraph.java                # Một phần nhỏ của bản đồ (quanh thành phần người dùng chọn) được lấy từ CSDL ra để dò luồng
├── linker/                          # CỐT LÕI: nối lời gọi API ở Frontend với API tương ứng ở Backend
│   ├── UrlPattern.java              # Chuẩn hóa một đường dẫn API thành dạng so sánh được (vd /items/123 và /items/{id} cùng dạng)
│   ├── LinkRuleEngine.java          # Áp các luật biến đổi đường dẫn trước khi so sánh (bỏ tiền tố proxy, thêm tiền tố của Backend…)
│   ├── EndpointMatcher.java         # So một lời gọi API ở Frontend với danh sách API Backend để tìm API khớp nhất
│   │                                #   (đường dẫn cụ thể được ưu tiên hơn đường dẫn có biến)
│   ├── CandidateScorer.java         # Trọng số điểm khớp tất định và quy ra mức Cao / Trung bình / Thấp
│   └── Linker.java                  # Điều phối việc nối: với mỗi lời gọi API, kết luận nối tới đâu và chắc chắn đến mức nào, kèm lý do
├── tracer/                          # CỐT LÕI: dò luồng từ đầu tới cuối
│   ├── Tracer.java                  # Bộ dò luồng chính: áp liên kết chốt tay rồi giao cho TraceRun (từ API Backend, hoặc từ phần tử giao diện có gọi API);
│   │                                #   phần tử không gọi máy chủ thì giao cho UiOnlyTracer
│   ├── UiOnlyTracer.java            # Dò luồng CHỈ có giao diện (User ↔ Frontend): bấm nút → mở hộp thoại / chuyển màn hình / cập nhật giao diện, không gọi máy chủ
│   ├── UnsupportedTraceStartException.java # Điểm xuất phát không dò được bằng bộ dò đã chọn — thông điệp hiển thị cho người dùng
│   ├── TraceRun.java                # Một lần dò: đi qua các lời gọi theo thứ tự thực thi tới tận bảng dữ liệu; phát bước, khối alt/opt/loop, chiều trả về
│   ├── TraceItem.java               # Phần tử của dòng sự kiện TraceRun phát ra: một bước, hoặc dấu mở / chuyển nhánh / đóng khối
│   ├── OverrideApplier.java         # Áp các quyết định chốt tay của người dùng trước khi dò (ghim liên kết đúng, bỏ liên kết sai)
│   ├── NoiseFilterApplier.java      # Ẩn các bước không mang ý nghĩa nghiệp vụ (ghi log, chuyển đổi dữ liệu…) để diagram gọn
│   ├── CycleGuard.java              # Chống dò vô tận: dừng khi gặp vòng lặp hoặc đi quá sâu (mặc định 12 bước lồng nhau)
│   ├── BlockBuilder.java            # Đánh số bước và đổi các dấu mở/đóng khối thành khối nhánh trên diagram (vd "nếu mã đã tồn tại thì báo lỗi")
│   ├── ParticipantBuilder.java      # Dựng các CỘT của diagram từ các thành phần trong luồng: gộp toàn bộ Frontend thành 1 cột, mỗi Controller/Service/
│   │                                #   Repository một cột (ServiceImpl gộp với interface), mọi bảng vào cột Database; đặt tên kiểu "Service Danh mục"
│   ├── SelfStepBuilder.java         # Gom các đoạn kiểm tra trong thân một hàm (if…throw, kiểm tra quyền, hàm private) thành một "bước tự xử lý"
│   └── TraceIds.java                # Tạo mã định danh cho một luồng đã dò — cùng yêu cầu luôn ra cùng mã, nên không cần lưu luồng vào CSDL
├── diagram/                         # Nhóm xử lý "vẽ diagram"
│   ├── SequenceViewModelBuilder.java # Chuyển luồng đã dò thành dữ liệu sẵn sàng để vẽ (vị trí cột, mũi tên, nét liền/nét đứt…)
│   ├── PlantUmlExporter.java        # Xuất luồng thành mã PlantUML (strictuml, autonumber, activate) — để dán sequence diagram vào tài liệu, wiki
│   └── SvgExporter.java             # Xuất diagram thành file ảnh SVG để tải về
├── scenario/                        # Nhóm xử lý "viết tab Kịch bản bằng lời"
│   ├── StepTemplatesVi.java         # Các mẫu câu tiếng Việt cho từng loại bước: "bấm…", "gửi yêu cầu … đến", "chuyển tiếp … đến", "gọi … trên", "truy vấn … trên", "trả về … cho"
│   └── ScenarioBuilder.java         # Ghép luồng đã dò với mẫu câu để ra "Bước n: A làm gì tới B" — bước n khớp mũi tên số n; khối alt mở bằng dòng "Nếu …:" / "Ngược lại:"
├── explain/                         # Nhóm xử lý "viết tab Diễn giải" (Giai đoạn 6)
│   ├── TypeExprs.java               # Đọc "biểu thức kiểu" của DTO do bộ phân tích ghi (kiểu, generic, tham số kiểu T được thay bằng đối số)
│   ├── IoSchemaBuilder.java         # Lập khối JSON Đầu vào / Đầu ra của một API từ các class dữ liệu (mỗi trường một câu "kiểu — bắt buộc, tối đa …"), kèm lỗi có thể gặp
│   ├── ConstraintsExtractor.java    # Liệt kê ràng buộc thô bằng lời: quyền, kiểm tra dữ liệu đầu vào, nhánh báo lỗi (throw + điều kiện + thông báo), cột không được trùng
│   ├── JsonSchemaBuilder.java       # Tạo bản mô tả cấu trúc dữ liệu (JSON Schema draft 2020-12) — để kiểm tra dữ liệu mẫu và để AI sinh đúng cấu trúc
│   ├── PurposeRule.java             # "Mục đích" dự phòng: @Operation → Javadoc → câu khung; mô tả ngắn của thành phần chứa
│   ├── BackendFlow.java             # Các hàm Backend, bảng dữ liệu, hệ thống khác mà một API đi qua (để rút ràng buộc, lỗi, "Gọi tới")
│   └── UiFlowResolver.java          # Một thao tác giao diện dẫn tới đâu: API gọi, hộp thoại mở, màn hình chuyển tới
└── mock/                            # Nhóm xử lý "dữ liệu mẫu"
    ├── SchemaValidator.java         # Kiểm tra một dữ liệu mẫu có đúng cấu trúc yêu cầu không (dùng để kiểm tra dữ liệu AI sinh ra)
    └── FallbackMockGenerator.java   # Bộ sinh dữ liệu mẫu dự phòng KHÔNG dùng AI — dùng khi AI tắt hoặc lỗi; cùng cấu trúc luôn ra cùng dữ liệu
```

### 4.3. `flowtracer-adapter-spring/` — Bộ phân tích Java / Spring Boot

**Cách chạy 2 pha:**
- **Pha 1 (`extract`):** chạy song song từng file trên virtual threads, dùng **JavaParser** để ghi sẵn vào dữ kiện: kiểu khai báo của biến / tham số / field (tên ngắn như viết trong mã), import, tên hàm được gọi và thứ tự gọi. Đổi tên ngắn thành FQN làm ở pha 2 (`ClassIndex`) vì cần biết mọi class của Source.
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
│   ├── AstSupport.java              # Hàm tiện ích đọc cây cú pháp: annotation, giá trị chuỗi/hằng số, Javadoc, số dòng
│   ├── TypeDeclarationExtractor.java # Lấy thông tin các class trong file: tên, kế thừa ai, và vai trò Spring (là Controller, Service, bảng dữ liệu…)
│   ├── MemberExtractor.java         # Lấy các thuộc tính và hàm của class: tên, kiểu, ghi chú mô tả, yêu cầu quyền, có dùng giao dịch CSDL không
│   ├── MappingExtractor.java        # Lấy các API mà Controller khai báo: phương thức (GET/POST…), đường dẫn, tham số, mô tả Swagger
│   ├── CallSiteExtractor.java       # Lấy mọi lời gọi hàm VÀ mọi chỗ báo lỗi (throw) trong một hàm theo thứ tự thực thi, chung một dãy số:
│   │                                #   gọi tới đâu, có nằm trong điều kiện if/switch/catch/vòng lặp không, loại lỗi và khóa thông báo
│   ├── GuardResolver.java           # Tìm điều kiện bao quanh gần nhất của một lời gọi / chỗ báo lỗi (then, else, case, catch, thân vòng lặp)
│   ├── StringFragmentExtractor.java # Gom các đoạn chuỗi trong hàm — để ghép lại câu truy vấn CSDL được viết tay bằng cách nối chuỗi
│   └── DtoFieldExtractor.java       # Lấy các trường của class dữ liệu: tên trong JSON, kiểu, ràng buộc (bắt buộc, độ dài…) — dùng cho Đầu vào/Đầu ra
├── resolve/                         # BƯỚC 2 — ghép kết quả của mọi file để hiểu chúng liên quan nhau thế nào
│   ├── JavaResolver.java            # Điều phối bước 2 và tạo ra kết quả phân tích cuối cùng của thư mục Backend
│   ├── ClassIndex.java              # Danh bạ mọi class trong dự án — tra từ tên class (theo import, cùng package) ra nơi khai báo; kế thừa/hiện thực; tìm field, hàm
│   ├── TypeNames.java               # Hàm nhỏ xử lý chuỗi tên kiểu (bỏ generic, lấy đối số generic, tên ngắn)
│   ├── InjectionResolver.java       # Xác định field được tiêm vào (kể cả field của lớp cha), kiểu của nó và @Qualifier / @Resource áp cho nó
│   ├── ImplementationResolver.java  # Với một interface, chọn class thực sự làm việc: 1 bean → CERTAIN; @Qualifier khớp → CERTAIN; @Primary hoặc tên field
│   │                                #   trùng tên bean → INFERRED; còn lại → UNRESOLVED kèm danh sách ứng viên (không chọn hộ)
│   ├── LayerClassifier.java         # Xếp mỗi class vào tầng Controller / Service / Repository / khác, kèm căn cứ (annotation, kế thừa Spring Data, tên)
│   ├── BusinessNames.java           # Tên nghiệp vụ của class để đặt nhãn cột ("Service Danh mục"): @Tag của Controller cùng tên gốc, nếu không thì tên class
│   ├── FlowResolver.java            # Dựng phần xử lý Backend của IR từ các endpoint: HANDLED_BY → CALLS → EXECUTES → READS/WRITES; chỉ phần đi tới được từ endpoint
│   ├── ConstantTable.java           # Giải giá trị annotation viết bằng hằng số (@Table(name = Tables.X), @Query(Queries.Y))
│   ├── SpringDataMethods.java       # Các hàm Spring Data mà repository kế thừa (save, findById…): chữ ký chuẩn, thao tác dữ liệu, kiểu trả về
│   ├── ModuleNameResolver.java      # Tên module của API: @Tag → tên Controller → tên package (kèm nguồn của tên)
│   ├── EndpointBuilder.java         # Tạo danh sách API hoàn chỉnh (ghép đường dẫn cấp class + cấp hàm) và nối mỗi API với hàm xử lý nó
│   ├── DataAccessResolver.java      # Nhận ra các hàm truy cập CSDL (findBy…, save…, delete…) và đó là thao tác đọc hay ghi
│   ├── QueryTextAnalyzer.java       # Đọc nội dung câu truy vấn (JPQL/SQL) để biết nó chạm vào bảng nào, kể cả câu được ghép từ nhiều đoạn
│   ├── EntityTableResolver.java     # Biết class dữ liệu nào ứng với bảng nào trong CSDL
│   ├── DtoSchemaBuilder.java        # Tạo mô tả cấu trúc dữ liệu vào/ra của API (tên trường, kiểu, ràng buộc)
│   └── MessageResolver.java         # Đổi mã thông báo lỗi trong code thành câu tiếng Việt (tra file messages*.properties của dự án, kể cả messages-app_vi; hằng enum mã lỗi tra qua khóa khai báo trong enum)
├── library/                         # Kiểu nằm trong jar của dự án (không có mã nguồn) — Giai đoạn 6
│   ├── ClassFileReader.java         # Đọc khai báo trong file .class (trường, kiểu generic, annotation); không nạp lớp, không chạy mã
│   ├── StubTypes.java               # Đổi chữ ký kiểu của .class sang cú pháp Java, quản lý import
│   └── LibraryStubBuilder.java      # Dựng "mã nguồn rút gọn" cho lớp mà mã dự án dùng (vd lớp bọc kết quả trả về) + khai báo sẵn Page<T> của Spring
├── config/
│   ├── ApplicationConfigReader.java # Đọc vài thiết lập cố định trong file cấu hình của dự án (tiền tố đường dẫn API, kiểu đặt tên trường JSON)
│   │                                #   — không giữ mật khẩu hay giá trị bí mật; hiểu file lưu UTF-8 có BOM
│   ├── PropertyLookup.java          # Giải ${khóa} / ${khóa:mặc định} trong đường dẫn API: tra theo yêu cầu trong application.yml của module
│   │                                #   (cấu hình thắng mặc định trong code như Spring); chặn khóa nhạy cảm; không đoán — ghi rõ lý do khi chưa giải được
│   └── QuerySinkRules.java          # Luật do người dùng khai báo: "hàm X của thư viện Y là nơi chạy truy vấn" — dùng khi thư viện không có mã nguồn
├── SpringLinkRules.java             # Tự phát hiện luật đường dẫn phía Backend (vd tiền tố chung của mọi API) để nối với Frontend
└── SpringNoiseFilters.java          # Danh sách mặc định các thành phần cần ẩn khỏi diagram ở dự án Spring (ghi log, chuyển đổi dữ liệu, getter/setter…)

flowtracer-adapter-spring/src/test/java/...
├── extract/*Test.java, resolve/*Test.java   # Kiểm thử từng bước đọc/ghép bằng các đoạn Java TỰ VIẾT
├── SpringGoldenTest.java            # Kiểm thử tổng: phân tích dự án mẫu fixtures/spring-mini (endpoint, module, đường dẫn) rồi so với kết quả chuẩn đã lưu
└── SpringFlowGoldenTest.java        # Kiểm thử tổng phần luồng xử lý: phân tích fixtures/spring-flow (DI, interface nhiều lớp hiện thực, truy vấn, bảng)
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
    │   └── comparison/              # (MỞ RỘNG — TẠM HOÃN, chưa làm) Chức năng nhóm 8: so sánh hệ thống cũ – mới
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
├── SpaFallbackResolver.java         # Mở thẳng / tải lại một màn hình (vd /w/<id>) thì trả index.html để router Angular xử lý; /api, /v3 và file thiếu vẫn 404
├── ExecutorConfig.java              # Tạo "nhóm luồng chạy nền" để quét mã nguồn song song mà không làm treo các yêu cầu khác
└── OpenApiConfig.java               # Thông tin cho trang tài liệu API tự sinh (Swagger) — giao diện web dùng nó để tự sinh kiểu dữ liệu

common/
├── error/                           # Cách báo lỗi thống nhất cho toàn bộ API
│   ├── ErrorCode.java               # Danh sách mã lỗi của hệ thống (trùng tên Workspace, đang quét, đường dẫn không hợp lệ, AI đang tắt…)
│   │                                #   kèm mã HTTP tương ứng
│   ├── AppException.java            # Loại lỗi dùng khi vi phạm quy tắc nghiệp vụ — mang theo mã lỗi và câu thông báo tiếng Việt
│   └── GlobalExceptionHandler.java  # Bắt mọi lỗi xảy ra và trả về cùng vỏ bọc ApiResponse { status, code, message, data: null, errors };
│                                    #   giấu chi tiết kỹ thuật và thông tin bí mật
├── sse/
│   └── SseHub.java                  # Bộ đẩy dữ liệu thời gian thực tới giao diện (tiến độ quét, nội dung AI đang viết dần)
└── web/
    ├── ApiResponse.java             # Vỏ bọc chuẩn cho MỌI response REST { status, code, message, data, errors } — dữ liệu nằm trong data
    │                                #   (không bọc: luồng SSE và /api/health)
    ├── ValidationPatterns.java      # Biểu thức kiểm tra dùng chung cho DTO (đường dẫn tương đối, glob, chuỗi không rỗng)
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
- Vai trò và công nghệ của Source do tool tự nhận diện từ dấu hiệu dự án; người dùng chỉ chọn thư mục.

```text
workspace/
├── controller/
│   ├── WorkspaceController.java     # Nhận các yêu cầu từ giao diện về Workspace: xem danh sách, tạo, sửa, xóa (API A1–A5)
│   ├── SourceController.java        # Nhận các yêu cầu thêm/sửa/xóa thư mục mã nguồn của Workspace (API A7–A9; tải zip/Git là MỞ RỘNG — A12–A13)
│   ├── DetectionController.java     # Nhận yêu cầu "nhận diện thư mục" vừa chọn: tìm các gốc dự án bên trong và vai trò của chúng (API A10;
│   │                                #   gợi ý tách mã cũ là MỞ RỘNG)
│   ├── FolderBrowseController.java  # Nhận yêu cầu duyệt thư mục trên máy — để người dùng chọn thư mục bằng giao diện (API A6)
│   └── NoiseFilterController.java   # Nhận yêu cầu xem/sửa danh sách thành phần cần ẩn khỏi diagram (API I4–I5)
├── service/
│   ├── WorkspaceService.java        # Xử lý nghiệp vụ Workspace: tạo (không cho trùng tên), sửa, xóa (không cho xóa khi đang quét), đọc thiết lập
│   │                                #   — các chức năng khác muốn biết thông tin Workspace phải hỏi qua đây
│   ├── SourceService.java           # Xử lý nghiệp vụ thêm/sửa/xóa thư mục mã nguồn
│   ├── FolderBrowseService.java     # Duyệt thư mục trong SOURCE_ROOT (chỉ trả thư mục, ẩn thư mục build/ẩn/hệ thống)
│   ├── NestedSourceRules.java       # Quy tắc khi một thư mục nằm bên trong thư mục khác (vd mã PHP cũ nằm trong repo Backend):
│   │                                #   tự loại thư mục con khỏi thư mục cha để không file nào bị đọc 2 lần; chặn khai báo trùng
│   └── StackDetectionService.java   # Nhận diện thư mục: không có gốc dự án → báo không hợp lệ; nằm trong một dự án → gợi ý thư mục gốc;
│                                    #   nhiều gốc (BE + FE chung repo) → tách thành nhiều Source; lưu công nghệ đã nhận diện (vd "Spring Boot 3.3")
├── repository/
│   ├── WorkspaceRepository.java     # Nơi đọc/ghi bảng workspaces trong CSDL
│   └── SourceRepository.java        # Nơi đọc/ghi bảng sources trong CSDL
├── entity/                          # Các class ứng với bảng trong CSDL
│   ├── Workspace.java               # Một dòng của bảng workspaces — một dự án đang được phân tích
│   ├── WorkspaceSettings.java       # Gói thiết lập riêng của một Workspace (luật nối đường dẫn, bộ lọc ẩn, độ sâu dò luồng…), lưu thành JSON
│   ├── DetectedStack.java           # Một công nghệ đã nhận diện được trên thư mục (tên, phiên bản, mức độ phù hợp)
│   └── Source.java                  # Một dòng của bảng sources — một thư mục mã nguồn kèm vai trò (Backend/Frontend/hệ thống cũ)
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── request/                     # Dữ liệu gửi lên — record, kiểm tra đầu vào đầy đủ (Bean Validation, thông điệp tiếng Việt)
    │   ├── CreateWorkspaceRequest.java, UpdateWorkspaceRequest.java   # Tạo / sửa Workspace
    │   ├── CreateSourceRequest.java, UpdateSourceRequest.java         # Thêm / sửa thư mục mã nguồn (đường dẫn, tên, thư mục loại trừ — không có vai trò)
    │   ├── DetectFolderRequest.java                                   # Nhận diện một thư mục
    │   └── UpdateNoiseFiltersRequest.java, NoiseFilterItemRequest.java   # Danh sách bộ lọc nhiễu thủ công
    └── response/                    # Dữ liệu trả về — luôn nằm trong data của ApiResponse
        ├── WorkspaceResponse.java, SourceResponse.java, DetectedStackResponse.java   # Workspace kèm thư mục mã nguồn và công nghệ
        ├── FolderBrowseResponse.java, FolderEntryResponse.java   # Thư mục đang xem + các thư mục con cho trình duyệt thư mục
        ├── DetectionResponse.java, DetectionStatus.java, DetectedRootResponse.java, LegacyHintResponse.java
        │                            #   Nhận diện thư mục: hợp lệ / nằm trong dự án khác / không tìm thấy; các gốc dự án kèm dấu hiệu
        │                            #   nhận diện; gợi ý mã cũ (PHP); cảnh báo
        └── NoiseFiltersResponse.java, NoiseFilterResponse.java   # Bộ lọc nhiễu mặc định + thủ công
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
- **LINK:** `LinkingService.linkAfterScan` — ghi luật tự phát hiện của lần quét rồi nối lại mọi lời gọi HTTP của Workspace (transaction riêng). Chạy cả khi không có file nào đổi (lúc đó chỉ nối lại với luật hiện tại).
- **FINALIZE:** cập nhật trạng thái và thống kê.

**Lưu ý:** mỗi Workspace chỉ có một lần quét chạy tại một thời điểm; file lỗi → `PARTIAL`; hủy hoặc lỗi thì giữ nguyên IR của lần quét thành công trước.

```text
scan/
├── controller/
│   ├── ScanController.java          # Nhận các yêu cầu về quét: bắt đầu quét, xem trạng thái, hủy, xem lịch sử, xem danh sách file lỗi
│   │                                #   (API B1, B3–B6; tự quét khi code đổi là MỞ RỘNG)
│   └── ScanEventsController.java    # Mở kênh để giao diện nhận tiến độ quét theo thời gian thực (thanh tiến độ) (API B2)
├── service/
│   ├── ScanService.java             # Xử lý nghiệp vụ quét: không cho 2 lần quét chạy cùng lúc, cho chạy nền, cho hủy, cho xem kết quả;
│   │                                #   Engine khởi động lại thì lần quét dở dang được đánh dấu thất bại
│   ├── ScanQueryService.java        # Đọc thông tin lần quét cho package khác (thẻ Workspace hiện lần quét gần nhất); đổi thống kê ↔ JSONB
│   ├── RunningScanRegistry.java     # Sổ ghi nhớ "Workspace nào đang quét" — dùng để chặn quét trùng, chặn xóa Workspace đang quét, và để hủy
│   └── FactsCache.java              # Kho lưu tạm "những gì đọc được từ mỗi file" trên ổ đĩa, theo Workspace và mã băm — file không đổi thì
│                                    #   lần sau lấy lại; xóa Workspace thì xóa luôn
├── model/                           # Dữ liệu nội bộ của lần quét
│   ├── ScanHandle.java              # Điều khiển lần quét đang chạy: yêu cầu hủy, chờ kết thúc
│   ├── ScanStage.java               # Các giai đoạn (DISCOVER … FINALIZE) kèm tên tiếng Việt
│   └── ScanStats.java, FileCounts.java, SourceScanStats.java, StageDuration.java, ScanDiagnostic.java
│                                    #   Thống kê lưu ở scan_runs.stats — mọi số đều đếm/đo thật; kèm danh sách chẩn đoán làm căn cứ
├── pipeline/                        # CỐT LÕI: các bước của một lần quét, chạy lần lượt
│   ├── ScanPipeline.java            # Người điều phối: chạy lần lượt các bước dưới đây, đo thời gian từng bước, xử lý lỗi, cập nhật trạng thái,
│   │                                #   dừng khi bị hủy; quét lại không có thay đổi thì bỏ qua bước 3–5
│   ├── ScanContext.java, SourceWork.java, DiscoveredFile.java   # Trạng thái của lần quét / từng Source / từng file đi qua các bước
│   ├── DiscoverStep.java            # Bước 1 — lập danh sách file cần đọc và tính "dấu vân tay" (mã băm) của từng file
│   ├── DiffStep.java                # Bước 2 — so với lần quét trước để biết file nào mới, đã sửa, đã xóa, giữ nguyên
│   ├── ExtractStep.java             # Bước 3 — đọc các file cần đọc lại (Java đọc ngay trong Engine; Angular giao cho sidecar)
│   ├── ResolveStep.java             # Bước 4 — ghép kết quả các file Java lại để hiểu chúng liên quan nhau thế nào
│   ├── PersistStep.java             # Bước 5 — lưu kết quả phân tích vào CSDL (lưu trọn vẹn một lần, lỗi giữa chừng thì giữ nguyên dữ liệu cũ)
│   ├── LinkStep.java                # (Giai đoạn 5) Bước 6 — nối lại các lời gọi API ở Frontend với API ở Backend
│   └── ProgressReporter.java        # Gửi thông báo tiến độ của từng bước lên giao diện
├── adapter/                         # Quản lý các bộ phân tích theo công nghệ
│   ├── AdapterRegistry.java         # Danh sách các bộ phân tích tool đang có (Spring, Angular…) — nơi DUY NHẤT đăng ký thêm bộ phân tích mới
│   ├── SidecarFileFacts.java        # Dữ kiện pha 1 của một file thuộc bộ phân tích chạy trong sidecar: chỉ đường dẫn + mã băm (sidecar tự cache dữ kiện thật)
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
    ├── request/StartScanRequest.java   # Yêu cầu bắt đầu quét (IMPORT: phân tích lại toàn bộ; RESCAN: chỉ file thay đổi)
    └── response/
        ├── ScanRunResponse.java     # Thông tin một lần quét (trạng thái, thời gian, thống kê)
        ├── ScanStatsResponse.java, FileCountsResponse.java, SourceScanStatsResponse.java, StageDurationResponse.java,
        │   ScanDiagnosticResponse.java   # Thống kê: số file theo loại, số thành phần, thời gian từng bước, chẩn đoán
        ├── LastScanResponse.java    # Tóm tắt lần quét gần nhất (hiện trên thẻ Workspace)
        ├── ScanProgressResponse.java   # Một thông báo tiến độ gửi qua SSE (giai đoạn, đã xử lý / tổng)
        └── ScanFileErrorResponse.java  # Một file không phân tích được kèm lý do
```

#### 4.4.5. `graph/` — Lưu trữ & khám phá "bản đồ" IR

**Phạm vi:** Nhóm 3, API C. **Bảng:** `ir_node`, `ir_edge`. Package này là **chủ sở hữu duy nhất của IR**: package khác đọc/ghi IR qua `GraphQueryService` / `GraphWriteService`.
**Lưu ý:** hai bảng IR dùng **JdbcClient ghi theo lô**, không dùng JPA, vì mỗi lần quét có hàng chục nghìn bản ghi.

```text
graph/
├── controller/
│   ├── TreeController.java          # Nhận yêu cầu lấy cây API Backend và cây thành phần Frontend để hiện ở cột trái (API C1–C2)
│   ├── SearchController.java        # Nhận yêu cầu tìm kiếm nhanh Ctrl+K (API C3)
│   ├── NodeController.java          # Nhận yêu cầu xem chi tiết một thành phần: "Được gọi từ", "Gọi tới", chuỗi thành phần chứa (API C4)
│   └── OpenApiController.java       # Nhận yêu cầu đối chiếu kết quả phân tích với tài liệu Swagger của dự án (API C6)
├── service/
│   ├── GraphQueryService.java       # Cung cấp cách ĐỌC bản đồ cho các chức năng khác (cây API Backend, cây Frontend, tìm kiếm nhanh, vùng xung quanh để dò luồng…)
│   ├── GraphWriteService.java       # Cung cấp cách GHI bản đồ cho các chức năng khác (thay toàn bộ kết quả của một thư mục sau khi quét…)
│   ├── TreeBuilder.java             # Dựng cây hiển thị: Backend theo Module → API; Frontend theo Ứng dụng → Màn hình → … → Nút bấm
│   ├── NodeDetailService.java       # "Được gọi từ" (đi ngược theo liên kết tới các phần tử giao diện), "Gọi tới", chuỗi thành phần chứa, thống kê trong thành phần chứa
│   └── OpenApiCheckService.java     # Tìm file đặc tả Swagger có sẵn trong repo (hoặc đọc URL nếu được cung cấp), kiểm tra đúng định dạng OpenAPI,
│                                    #   so với danh sách API tool tìm được và báo chỗ lệch
├── repository/
│   ├── IrNodeRepository.java        # Nơi đọc/ghi bảng ir_nodes (các thành phần) — ghi theo lô lớn vì mỗi lần quét có rất nhiều dòng; có cả tìm kiếm nhanh (ILIKE + tsvector)
│   └── IrEdgeRepository.java       # Nơi đọc/ghi bảng ir_edges (các quan hệ giữa thành phần)
├── model/
│   ├── IrNodeRow.java, IrEdgeRow.java   # Một dòng của bảng ir_nodes / ir_edges: thành phần (hoặc quan hệ) kèm các mã định danh trong CSDL
│   │                                #   (hai bảng này ghi bằng JdbcClient nên không có entity JPA)
│   └── SourceGraphWriteResult.java  # Kết quả ghi IR một Source (số node ghi/xóa, key bị bỏ vì trùng Source khác)
└── dto/response/                    # Dữ liệu trả về cho giao diện
    ├── BackendTreeResponse.java, ApiModuleResponse.java, EndpointNodeResponse.java   # Cây API Backend Module → Endpoint (C1)
    ├── TreeNodeResponse.java        # (Giai đoạn 4) Một nút trên cây Frontend
    ├── SearchResultResponse.java    # Một kết quả tìm kiếm
    ├── NodeDetailResponse.java, NodeRelationResponse.java   # Chi tiết một thành phần: thông tin, "được gọi từ", "gọi tới", chuỗi thành phần chứa (C4)
    └── OpenApiCheckResponse.java, OpenApiOperationResponse.java   # Báo cáo đối chiếu với Swagger: API khớp, chỉ có trong code, chỉ có trong Swagger (C6)
```

#### 4.4.6. `linking/` — Liên kết Frontend – Backend & độ tin cậy

**Phạm vi:** Nhóm 5, API E. **Bảng:** `link_override`. Luật liên kết nằm trong `workspace.settings`.
**Lưu ý CỐT LÕI:** AI **không bao giờ đổi liên kết**, chỉ phân tích giúp người dùng khi họ bấm "Nhờ AI phân tích". Ứng viên AI đề xuất thêm chỉ được hiện khi là node có thật và hợp lệ về kiểu. Liên kết chốt tay luôn thắng kết quả tự động.

```text
linking/
├── controller/
│   ├── EdgeController.java          # Nhận yêu cầu xem chi tiết một liên kết (vì sao nối, chắc chắn đến đâu) và yêu cầu "nhờ AI phân tích" (API E1–E2)
│   ├── LinkOverrideController.java  # Nhận yêu cầu xem / thêm / bỏ các liên kết người dùng chốt tay (API E3–E5)
│   ├── LinkRuleController.java      # Nhận yêu cầu xem / sửa các luật nối đường dẫn Frontend với Backend (API E6–E7)
│   └── RuntimeController.java       # (MỞ RỘNG) Nhận yêu cầu đối chiếu với ứng dụng đang chạy thật và dữ liệu theo dõi lúc chạy (API E8–E9)
├── service/
│   ├── LinkingService.java          # Xử lý việc nối mọi lời gọi HTTP với API đích sau mỗi lần quét: Frontend → Backend, Backend → service khác
│   │                                #   trong Workspace (microservice), hoặc đánh dấu "hệ thống bên ngoài"
│   ├── EdgeAdviceService.java       # Xử lý "nhờ AI phân tích": gửi chuỗi gọi từ thao tác của người dùng tới điểm gọi, đoạn code và danh sách ứng viên
│   │                                #   cho AI, nhận lời giải thích + bằng chứng từng ứng viên; KHÔNG đổi liên kết — người dùng đọc rồi tự chốt tay
│   ├── CallChainService.java        # Tìm chuỗi gọi (phần tử → hàm → … → lời gọi HTTP) theo đúng luồng đang xem; kèm dòng mã dẫn tới mắt xích kế
│   ├── CandidateHintRule.java       # Quy tắc thuần: từ trong tên hàm / chuỗi truyền vào trùng với đường dẫn ứng viên (tín hiệu khớp tên, không cần AI)
│   ├── AdviceConsistencyRule.java   # Quy tắc thuần: "Nên chọn" phải có bằng chứng thật; nhiều "Nên chọn" thì chỉ giữ ứng viên khớp tên nhiều nhất
│   ├── AiAdviceRule.java            # Quy tắc kiểm tra câu trả lời của AI: lời giải thích phải gắn với ứng viên có thật; ứng viên AI đề xuất thêm
│   │                                #   chỉ được giữ khi là thành phần có thật trong bản đồ và hợp lệ về kiểu (đúng interface / đúng phương thức HTTP)
│   ├── LinkOverrideService.java     # Xử lý liên kết chốt tay (tạo, xóa, liệt kê) — chức năng dò luồng cũng dùng qua đây
│   ├── LinkRuleService.java         # Xử lý luật nối đường dẫn: gộp luật tool tự tìm và luật người dùng nhập; sửa luật thì nối lại ngay
│   └── RuntimeVerificationService.java  # (MỞ RỘNG) Xác nhận liên kết bằng dữ liệu lúc ứng dụng chạy thật — chỉ giữ thông tin kỹ thuật, bỏ dữ liệu nghiệp vụ
├── repository/
│   └── LinkOverrideRepository.java  # Nơi đọc/ghi bảng link_overrides trong CSDL
├── entity/
│   ├── LinkOverride.java            # Một dòng của bảng link_overrides — một quyết định chốt tay: "ghim" liên kết đúng hoặc "bỏ" liên kết sai
│   └── OverrideDecision.java        # Danh sách hai loại quyết định: GHIM / BỎ
└── dto/                             # Dữ liệu giao diện gửi lên và Engine trả về
    ├── EdgeDetailResponse.java      # Chi tiết một liên kết: mức tin cậy, lý do, các khả năng, vị trí trong code
    ├── EdgeAdviceRequest.java, EdgeAdviceResponse.java   # Yêu cầu "nhờ AI phân tích" (có bỏ qua kết quả đã lưu hay không) / lời giải thích từng ứng viên
    ├── CreateLinkOverrideRequest.java, LinkOverrideResponse.java   # Yêu cầu chốt tay một liên kết / thông tin liên kết đã chốt
    ├── UpdateLinkRulesRequest.java, LinkRuleItemRequest.java       # Luật nối đường dẫn thủ công gửi lên
    ├── LinkRulesResponse.java, LinkRuleResponse.java, LinkSummaryResponse.java   # Luật trả về; kết quả nối lại sau khi sửa luật
    └── EdgeNodeResponse.java, EdgeCandidateResponse.java    # Một đầu của liên kết; một ứng viên kèm mức khớp
model/
    └── LinkSummary.java             # Kết quả một lần nối (số lời gọi chắc chắn / suy luận / chưa xác định / ra ngoài / thay đổi) — bước LINK báo tiến độ
```

#### 4.4.7. `tracing/` — Dò vết & trực quan hóa

**Phạm vi:** Nhóm 4, API D. **Bảng:** không có. Trace tính lại được bất cứ lúc nào (`trace_id` tất định) và được giữ trong cache bộ nhớ.

```text
tracing/
├── controller/
│   ├── TraceController.java         # Nhận yêu cầu "dò luồng từ thành phần này" và yêu cầu xuất diagram ra file (API D1–D2)
│   └── FlowchartController.java     # (MỞ RỘNG) Nhận yêu cầu vẽ flowchart (API D3)
├── service/
│   ├── TracingService.java         # Xử lý dò luồng (D1) và xuất diagram (D2): lấy vùng bản đồ liên quan → áp liên kết chốt tay và bộ lọc ẩn → chạy bộ dò luồng;
│   │                                #   gặp chỗ chưa chắc chắn thì giữ "chưa xác định" kèm ứng viên (không gọi AI) — các chức năng khác cũng gọi dò luồng qua đây
│   └── TraceCache.java              # Bộ nhớ tạm giữ các luồng vừa dò theo trace_id để mở lại / xuất nhanh (tối đa 256 mục, sống 10 phút)
├── model/
│   ├── CachedTrace.java             # Một luồng đã dò cùng tên thành phần xuất phát, giữ trong bộ nhớ tạm
│   └── ExportFormat.java            # Danh sách định dạng xuất: PlantUML / SVG
└── dto/                             # Dữ liệu giao diện gửi lên (dò từ đâu) và Engine trả về (luồng đã dò)
    ├── request/CreateTraceRequest.java      # Yêu cầu dò luồng: bắt đầu từ thành phần nào, dò sâu tối đa bao nhiêu bước
    └── response/                    # TraceResponse (luồng đã dò) · TraceParticipantResponse · TraceStepResponse · TraceBlockResponse · TraceBranchResponse · TraceExportResponse
```

#### 4.4.8. `explanation/` — Diễn giải & dữ liệu mẫu

**Phạm vi:** Nhóm 6, API F: panel phải, tab Diễn giải và tab Kịch bản. **Bảng:** không có (nội dung AI lưu trong cache của `ai`).
**Lưu ý CỐT LÕI:**
- Phần tất định (đầu vào/đầu ra, ràng buộc, khung kịch bản) luôn có.
- Mục đích (80–100 từ) và câu ràng buộc gọn do AI viết, có cache; AI lỗi/tắt thì dùng `@Operation` → Javadoc → tài liệu và ràng buộc thô.
- Thành phần chứa (module, màn hình…) chỉ có tên, loại và mô tả ngắn.
- Dữ liệu mẫu do AI sinh **một lần** từ JSON Schema, kiểm tra khớp rồi cache; lỗi hoặc tắt AI thì dùng bộ sinh dự phòng.
- Lời kịch bản do AI viết phải giữ nguyên số bước, thứ tự và thành phần.

```text
explanation/
├── controller/
│   ├── ExplanationController.java   # Nhận yêu cầu lấy tab Diễn giải của một thành phần (F1), dữ liệu mẫu (F2) và tab Kịch bản của một luồng đã dò (F3)
│   ├── CodeController.java          # Nhận yêu cầu lấy đoạn mã theo từng lớp trong luồng cho popup "Xem code" (API C5)
│   └── RegenerateController.java    # (Giai đoạn 7) Nhận yêu cầu "Sinh lại" nội dung AI khi người dùng thấy chưa tốt (API F4)
├── service/
│   ├── ExplanationService.java      # Gom đủ các mục của tab Diễn giải (mục đích, đầu vào/đầu ra theo từng API mà thành phần gọi, lỗi, ràng buộc,
│   │                                #   được gọi từ / gọi tới), dữ liệu mẫu và kịch bản; áp liên kết chốt tay như dò vết. Phần tất định — chạy khi AI tắt
│   ├── CodeSnippetService.java      # Duyệt đúng luồng mà dò vết đi và đọc đoạn code của từng lớp (Template, Component, Kết nối API, Controller, Service, Repository)
│   │                                #   qua bộ đọc mã nguồn an toàn
│   ├── PurposeResolver.java         # (Giai đoạn 7) Mục đích: bản AI đã lưu → nhờ AI viết 80–100 từ (kèm ràng buộc gọn) → AI lỗi/tắt thì PurposeRule của lõi
│   ├── SpecDocReader.java           # (chưa làm) Tìm đoạn mô tả API/màn hình trong các file tài liệu .md có sẵn trong repo của dự án
│   ├── MockDataService.java         # (Giai đoạn 7) Dữ liệu mẫu: đã lưu thì dùng lại; chưa có thì nhờ AI sinh một lần, kiểm tra đúng cấu trúc rồi lưu;
│   │                                #   AI tắt/lỗi thì dùng FallbackMockGenerator của lõi
│   └── ScenarioPolishRule.java      # (Giai đoạn 7) Quy tắc chấp nhận câu AI viết lại: phải giữ nguyên số bước, thứ tự và tên thành phần; sai thì dùng câu gốc
└── dto/response/                    # Dữ liệu trả về cho giao diện
    ├── ExplanationResponse.java, PurposeResponse.java, ApiIoResponse.java   # Nội dung tab Diễn giải: mục đích kèm nguồn; đầu vào/ra dạng JSON + lỗi + ràng buộc của từng API
    ├── MockDataResponse.java        # Dữ liệu mẫu kèm nguồn sinh (bộ sinh dự phòng hay AI) và chỗ không khớp cấu trúc
    ├── ScenarioResponse.java        # Tab Kịch bản: các actor tham gia và các dòng (số bước = số mũi tên, câu mô tả, dòng điều kiện)
    ├── CodeTabResponse.java         # Một tab của popup "Xem code": vai trò lớp, file, số dòng, dòng tô sáng, nội dung
    └── RegenerateRequest.java       # (Giai đoạn 7) Yêu cầu sinh lại nội dung AI (sinh lại việc gì, cho thành phần nào)
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
│   └── AiSettingsController.java    # Xem / sửa cấu hình AI (bật/tắt từng mục đích), kiểm tra kết nối, thống kê sử dụng (API I1–I3, I6)
├── service/
│   ├── AiGateway.java               # CỔNG DUY NHẤT để gọi AI — 7 bước kiểm soát ở trên; chỉ đọc bản đã lưu (peek / latest) hoặc chạy đủ (execute)
│   ├── SecretRedactor.java          # Che mật khẩu, token, khóa, chuỗi kết nối CSDL, private key TRƯỚC khi gửi AI; không gửi file .env / keystore / dữ liệu
│   ├── PromptTemplates.java         # Nạp prompt từ resources/prompts/<tên>.v<n>.st (SYSTEM / USER / SCHEMA), điền dữ kiện, giữ phiên bản
│   ├── ProviderHealth.java          # Tình trạng provider theo loại lỗi: 429 = đạt giới hạn (chặn gọi tới giờ nhà cung cấp báo), 503 = quá tải, lỗi khác = bị lỗi
│   │                                #   (chỉ hiển thị); trạng thái "đang kiểm tra" khi vừa khởi động
│   ├── AiStartupCheck.java          # Tự "Kiểm tra kết nối" một lần khi Engine khởi động (chạy nền)
│   ├── AiCallTrace.java             # Ghi ra log Engine (logger flowtracer.ai) mục đích, prompt đã che secret, kết quả, token, thời gian của mọi lời gọi AI
│   └── AiSettingsService.java       # Bật/tắt từng mục đích (app_settings), kiểm tra kết nối, trạng thái AI, mức dùng hôm nay, hạn mức lượt gọi theo ngày do nhà cung cấp báo, thống kê sử dụng
├── model/                           # Kiểu dùng chung cho package khác (không phải entity)
│   ├── AiPurpose.java               # Các việc ĐƯỢC PHÉP nhờ AI: viết mục đích & ràng buộc & lỗi, lời kịch bản, dữ liệu mẫu, phân tích liên kết, đặt tên thành phần FE
│   ├── AiCall.java                  # Một lần nhờ AI: mục đích, đối tượng, prompt, schema, bộ kiểm tra đầu ra
│   ├── AiOutcome.java               # Kết quả: GENERATED / CACHED / DISABLED / UNAVAILABLE / QUOTA_EXCEEDED / REJECTED
│   └── AiRejectedException.java     # Đầu ra AI không đạt kiểm tra → bỏ, dùng kết quả tất định
├── provider/
│   ├── LlmProvider.java             # Khuôn mẫu chung của một nhà cung cấp AI
│   ├── SpringAiLlmProvider.java     # Gemini / Claude / OpenAI-compatible qua Spring AI ChatModel (lỗi chỉ trả loại lỗi, log đã che secret)
│   ├── NoopLlmProvider.java         # provider = none: luôn "không khả dụng"
│   ├── LlmProviderConfig.java       # Chọn provider theo cấu hình; khóa chỉ đọc từ biến môi trường; thiếu khóa thì AI không khả dụng, app vẫn chạy
│   └── LlmRequest.java, LlmResult.java
├── repository/                      # ai_artifacts, llm_call_logs (kèm tổng hợp token / lỗi / độ trễ theo mục đích), app_settings
├── entity/                          # AiArtifact, LlmCallLog, LlmCallStatus, AppSetting
└── dto/
    ├── request/UpdateAiSettingsRequest.java
    └── response/                    # AiSettingsResponse, AiPurposeSettingResponse, AiTestResponse, AiUsageResponse

Phía package khác (gọi AiGateway): explanation/ExplanationAiService (PURPOSE_TEXT, MOCK_DATA, SCENARIO_POLISH), explanation/ElementNamingService
(ELEMENT_NAMING), explanation/AiArtifactService + AiArtifactController (F4), linking/EdgeAdviceService + AiAdviceRule (E2), graph/ElementNameLookup
(đưa tên AI vào cây Frontend C2 và quan hệ C4); core: ScenarioPolishRule, ElementNameRule.

main/resources/prompts/              # Câu lệnh mẫu gửi cho AI — mỗi việc một file, đổi nội dung thì tăng số phiên bản (v1 → v2)
├── purpose-text.v1.st               # Lệnh nhờ AI viết 80–100 từ tiếng Việt dễ hiểu về mục đích của một API/nút bấm, kèm câu ràng buộc gọn
├── scenario-polish.v1.st            # Lệnh nhờ AI viết lời kể cho từng bước kịch bản, giữ nguyên số bước, thứ tự và thành phần
├── mock-data.v1.st                  # Lệnh nhờ AI sinh dữ liệu mẫu đúng cấu trúc dữ liệu cho trước (không dùng dữ liệu thật)
├── element-naming.v1.st             # Lệnh nhờ AI đặt tên tiếng Việt chuẩn cho các phần tử của một màn hình từ chữ trên giao diện, tên hàm và API mà phần tử gọi
├── edge-resolution.v2.st            # Lệnh nhờ AI phân tích các ứng viên của một liên kết chưa chắc chắn (kèm chuỗi gọi, từ khớp tên, bằng chứng): giải thích từng ứng viên, có thể đề xuất thêm một
└── (mở rộng, chưa làm) legacy-match.v1.st   # Lệnh cho ghép luồng cũ–mới
```

#### 4.4.10. `comparison/` — MỞ RỘNG (tạm hoãn)

```text
comparison/                          # (MỞ RỘNG — TẠM HOÃN, chưa làm) Chức năng nhóm 8: so sánh luồng hệ thống cũ (PHP) với hệ thống mới — không có bảng riêng
├── controller/
│   └── ComparisonController.java    # Nhận yêu cầu gợi ý thành phần để so sánh, so hai luồng, tìm luồng cũ tương ứng (API H1–H3)
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
│       │   ├── detect.ts            # Lệnh detect: angular.json + @angular/core trong package.json → Angular, kèm phiên bản
│       │   ├── facts.ts             # Kiểu dữ liệu chứa "những gì đọc được từ MỘT file Angular" (class, route, lời gọi API, nút bấm, nhãn…)
│       │   ├── workspace/           # Đọc cấu hình tổng của dự án Angular
│       │   │   ├── angular-json.reader.ts      # Đọc angular.json để biết dự án có những ứng dụng/thư viện nào và nằm ở đâu
│       │   │   ├── paths.ts                    # Ghép / chuẩn hóa đường dẫn kiểu POSIX trong Source
│       │   │   ├── tsconfig-paths.reader.ts    # Đọc các đường dẫn tắt khi import (vd 'shared-lib') để biết chúng trỏ tới thư mục nào
│       │   │   └── federation.reader.ts        # Với dự án microfrontend: biết ứng dụng con nào chia sẻ màn hình nào, ứng dụng chính nạp chúng ở đâu
│       │   ├── extract/             # BƯỚC 1 — đọc từng file riêng lẻ
│       │   │   ├── extract-file.ts             # Điều phối: xem file thuộc loại nào (code .ts, giao diện .html, file ngôn ngữ…) rồi gọi bộ đọc tương ứng
│       │   │   ├── ts-utils.ts                 # Hàm dùng chung đọc cây cú pháp TypeScript: vị trí dòng, tên kiểu, ghép chuỗi / URL từ biểu thức
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
│       │   │   ├── symbol-table.ts             # Danh bạ: mỗi tên được import thực sự được khai báo ở file nào (đường dẫn tương đối, alias tsconfig, file barrel)
│       │   │   ├── route-tree.builder.ts       # Từ khai báo route (children, loadChildren, loadComponent, NgModule, microfrontend) ra danh sách màn hình + đường dẫn đầy đủ
│       │   │   ├── di.resolver.ts              # Xác định mỗi component/service thực sự dùng class nào (có nhiều khả năng thì ghi lại làm ứng viên)
│       │   │   ├── call.resolver.ts            # Nối chuỗi lời gọi: component → các lớp xử lý trung gian → chỗ gọi API
│       │   │   ├── url-evaluator.ts            # Tính ra đường dẫn API đầy đủ từ hằng số và chuỗi ghép; phần không biết trước thì đánh dấu là biến
│       │   │   ├── screen-tree.builder.ts      # Dựng cây giao diện: Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Nút bấm
│       │   │   ├── element.builder.ts          # Tạo từng nút bấm/ô nhập trên cây, nối với hàm nó gọi, gắn nhãn tiếng Việt
│       │   │   └── label.resolver.ts           # Đổi mã nhãn thành chữ tiếng Việt hiển thị trên giao diện
│       │   └── config/
│       │       └── proxy-config.reader.ts      # Đọc proxy.conf.json để biết đường dẫn API bị đổi thế nào trước khi tới Backend (vd bỏ tiền tố /gateway)
│       │   # Địa chỉ gốc của API (environment*.ts) được đọc như mọi hằng số khác: constant.extractor.ts + url-evaluator.ts
│       └── php-legacy/              # (MỞ RỘNG — TẠM HOÃN, chưa làm) Bộ phân tích mã PHP của hệ thống cũ
└── test/
    ├── angular/                     # Kiểm thử Angular: extract.spec (từng bộ đọc bằng đoạn code TỰ VIẾT), resolve.spec (ghép IR trên fixtures/angular-mini),
    │                                #   golden.spec (so với fixtures/golden/angular-mini.ir.json; UPDATE_GOLDEN=1 để sinh lại)
    ├── support/fixture.ts           # Chạy toàn bộ pipeline sidecar trên một thư mục fixture
    ├── contract.spec.ts             # Kiểm thử: dữ liệu ví dụ trong contracts/ đúng định dạng quy định
    ├── proxy-config.spec.ts         # Kiểm thử bộ đọc proxy.conf.json
    └── angular-detect.spec.ts       # Kiểm thử lệnh detect (Angular)
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
    ├── main.ts, index.html, styles.scss   # Điểm khởi động của giao diện, trang HTML gốc (tiêu đề "Flow Tracer", favicon logo "CtV" ở public/favicon.svg + favicon.ico), và kiểu dáng (màu, font, ô tích) dùng chung
    └── app/
        ├── app.ts, app.html, app.scss   # Khung ngoài cùng của giao diện (thanh tiêu đề + vùng hiển thị từng màn hình)
        ├── app.config.ts            # Bật các tính năng chung của ứng dụng: điều hướng giữa màn hình, gọi API lên Engine
        ├── app.routes.ts            # Bảng điều hướng: địa chỉ nào mở màn hình nào (danh sách Workspace, màn hình chính, cài đặt, cấu hình AI)
        ├── core/                    # Các dịch vụ dùng chung cho cả giao diện
        │   ├── api/
        │   │   ├── generated/schema.d.ts   # Kiểu dữ liệu API TỰ SINH từ /v3/api-docs của Engine (npm run gen:api) — không sửa tay, có commit
        │   │   ├── api-types.ts     # Tên ngắn cho các kiểu sinh ra; vỏ bọc ApiResponse; kiểu sự kiện SSE (không có trong OpenAPI)
        │   │   ├── api-error.ts     # Đọc lỗi từ vỏ bọc (code, message, errors) thành thông báo tiếng Việt
        │   │   └── workspace.api.ts, scan.api.ts, graph.api.ts, trace.api.ts, linking.api.ts, explanation.api.ts   # Gọi API theo nhóm A / B (kể cả SSE tiến độ) / C / D / E / F, lấy data trong vỏ bọc; khóa node trên đường dẫn mã hóa hai lần
        │   ├── ui/                  # Mảnh giao diện dùng chung: icon SVG, modal (Esc đóng modal trên cùng), hộp xác nhận, toast, dropdown chọn giá trị theo thiết kế riêng (select-menu.ts — không dùng <select> mặc định)
        │   ├── explorer-navigation.ts   # Cầu nối bảng Ctrl+K (ở khung ứng dụng) → màn hình chính: "hãy mở thành phần này"
        │   └── workspace-context.ts # Danh sách Workspace, Workspace đang mở, trạng thái mở wizard Import và bảng Ctrl+K
        └── features/                # Mỗi thư mục là một nhóm màn hình
            ├── workspaces/          # Danh sách Workspace (thẻ có trạng thái lần quét gần nhất), wizard Import 3 bước + trình duyệt thư mục,
            │                        #   trang Cài đặt (tab Nguồn mã, Luật liên kết, Bộ lọc nhiễu, Liên kết chốt tay, Đối chiếu OpenAPI, Lịch sử quét — mỗi tab một
            │                        #   khối hướng dẫn); link-rules-tab, noise-filters-tab, link-overrides-tab, openapi-check-tab, link-settings-model,
            │                        #   openapi-check-model (thuần, test được)
            ├── scan/                # Quét: ScanTracker (bắt đầu / theo dõi SSE / hủy, tự hỏi lại khi rớt SSE), khung tiến độ góc phải dưới
            │                        #   (7 giai đoạn kể cả "Nối Frontend – Backend", giai đoạn bỏ qua, tóm tắt có căn cứ), hộp danh sách file lỗi, bảng Lịch sử quét
            ├── explorer/            # MÀN HÌNH CHÍNH 3 CỘT
            │   ├── explorer.page.ts            # Bố cục: cột trái (kéo đổi độ rộng, thu gọn), canvas (cụm nút nổi trạng thái quét + Quét lại),
            │   │                               #   cột phải (thông tin endpoint đang chọn, tóm tắt luồng, cảnh báo của luồng); chọn API → dò vết (D1)
            │   ├── backend-tree.ts, tree-filter.ts   # Cây API Backend Module → Endpoint, lọc nhanh có tô sáng
            │   ├── frontend-tree.ts, frontend-tree-model.ts   # Cây Frontend Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử (Giai đoạn 4): nhãn
            │   │                               #   "Nút Thêm mới" theo bản vẽ, thẻ API / UI, tên kỹ thuật in nghiêng khi không có nhãn, lọc, mở tới một nút (Ctrl+K)
            │   ├── sequence/                   # Sequence diagram (Giai đoạn 3)
            │   │   ├── sequence-model.ts       # Đổi kiểu sinh từ OpenAPI sang kiểu chặt; nhận ra cột "chưa xác định"
            │   │   ├── sequence-layout.ts      # Dàn cột, mũi tên, khung alt/opt/loop lồng nhau, vạch else, thanh kích hoạt (thuần, test được)
            │   │   ├── sequence-svg.ts         # Vẽ thành chuỗi SVG thuần (dùng cho cả màn hình lẫn xuất file); thoát ký tự nhãn
            │   │   ├── sequence-export.ts      # Đo chữ, tải file, đổi SVG → PNG trên trình duyệt
            │   │   ├── sequence-canvas.ts      # Canvas: zoom / kéo / vừa khung, chú giải, thẻ "n bước chưa xác định", mô tả khi di chuột, tô sáng, xuất;
            │   │   │                           #   bấm mũi tên chưa chắc chắn / đã chốt tay → popover liên kết
            │   │   ├── edge-popover.ts         # Popover liên kết (Giai đoạn 5): lý do, ứng viên + mức khớp Cao / Trung bình / Thấp (E1), chọn API khác
            │   │   │                           #   qua ô tìm (C3), Chốt tay (E4) / Bỏ chốt tay (E5); "Nhờ AI phân tích" (E2): lời giải thích từng ứng viên + ứng viên AI đề xuất, không đổi liên kết
            │   │   └── edge-popover-model.ts   # Khi nào mở popover, nhãn mức tin cậy, danh sách ứng viên, giữ popover trọn trong canvas (thuần, test được)
            │   └── panel/                      # Cột phải (Giai đoạn 6)
            │       ├── node-panel.ts           # Tên + nút Xem code + hai tab; Diễn giải (F1, F2), Kịch bản (F3, tô sáng đồng bộ với diagram), thành phần chứa;
            │       │                           #   nhãn "✦ AI", nút Sinh bằng AI / Sinh lại (F4), banner khi AI nghỉ, chữ hiện dần, "Đặt tên bằng AI"
            │       ├── json-block.ts           # Khối JSON nền tối, tô màu cú pháp, nút Sao chép
            │       ├── code-dialog.ts          # Popup Xem code (C5): tab theo lớp, đánh số dòng, tô sáng, chỉ đọc
            │       └── explanation-model.ts    # Tô màu JSON, đầu vào / đầu ra hiển thị, dòng kịch bản, dòng code, nhãn quan hệ (thuần, test được)
            ├── search/              # Bảng tìm nhanh Ctrl+K (API C3): gõ → kết quả nhóm theo loại, ↑ ↓ Enter Esc; chọn thì mở đúng thành phần trên cây
            ├── compare/             # (MỞ RỘNG — TẠM HOÃN, chưa làm) Màn hình so sánh 2 diagram cạnh nhau và danh sách khác biệt cũ – mới
            └── ai-settings/         # Màn hình cấu hình AI: xem đang dùng AI nào, bật/tắt từng việc, kiểm tra kết nối, mức dùng hôm nay, thống kê (lượt gọi, token, lỗi)
            │                        # (+ ai-usage-model.ts: định dạng số, chiều cao cột, khoảng ngày (1 / 7 ngày / tất cả) — thuần, test được)
```

---

## 7. Thư mục phụ trợ

```text
fixtures/                            # Các dự án mẫu nhỏ TỰ VIẾT 100% — dùng để kiểm thử tool và demo công khai
├── README.md                        # Quy tắc dùng fixtures: không sao chép code công ty; mỗi dự án mẫu cố ý chứa các "ca khó" cần kiểm thử
├── detection/                       # Cây thư mục giả cho test nhận diện thư mục (monorepo BE + FE, FE nằm trong repo BE, thư mục không liên quan)
├── spring-mini/                     # Dự án Backend Spring Boot nhỏ cho bộ phân tích endpoint / module / đường dẫn (Giai đoạn 2)
├── spring-flow/                     # Dự án Backend Spring Boot nhỏ cho luồng xử lý (Giai đoạn 3): interface có 1 / 2 lớp hiện thực, @Primary, @Qualifier,
│                                    #   @Query, câu truy vấn ghép bằng StringBuilder, entity không khai @Table, hằng số tên bảng, `if … throw`
├── spring-shop-api/                 # (Kế hoạch) Dự án Backend Spring Boot mẫu lớn hơn: dữ liệu trả về được bọc chung, thông báo lỗi tiếng Việt,
│                                    #   bảng dùng chung có cột phân loại
├── angular-mini/                    # Dự án Frontend Angular nhỏ (Giai đoạn 4): ứng dụng + thư viện dùng chung, route standalone và NgModule, template tách file / viết
│                                    #   trong .ts, @if/@for, form, tab, hộp thoại, facade → InjectionToken → repository HTTP, URL ghép từ hằng số ở thư viện khác,
│                                    #   proxy đổi đường dẫn, nhãn từ điển t('khóa') và | translate (README.md liệt kê từng ca)
├── php-legacy-mini/                 # (MỞ RỘNG — TẠM HOÃN, chưa làm) Dự án PHP mẫu mô phỏng hệ thống cũ
└── golden/                          # Các "kết quả chuẩn" đã lưu khi phân tích dự án mẫu — test so kết quả mới với đây để phát hiện thay đổi ngoài ý muốn

scripts/                             # Các công cụ chạy tay
├── benchmark.mjs                    # Đo tốc độ: quét lần đầu, quét lại (20 file đổi — trên bản sao), tải cây, tìm kiếm, dò luồng, RAM (NFR-01 → NFR-04, NFR-06)
├── evaluate-accuracy.mjs            # Đo độ chính xác trên dự án demo (CHỈ chạy trên máy cá nhân) — chỉ in ra con số tổng hợp, không in code
├── lib/engine-client.mjs            # Phần dùng chung của hai script đo: gọi API Engine, chờ quét, thống kê, đo RAM, so khớp tên
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
| 7. Nhóm chức năng *(đã loại khỏi kế hoạch)* | — (đã gỡ package `feature`) | — | G | — |
| 8. So sánh cũ – mới *(mở rộng, tạm hoãn)* | `comparison` (+ analyzer-node php-legacy) | — | H | features/compare |
| 9. Cấu hình AI & hệ thống | `ai` (+ actuator health) | `ai_artifact`, `llm_call_log`, `app_setting` | I | features/ai-settings |

## 9. Phụ lục B — Service công khai được phép gọi chéo package

Ngoài các service dưới đây, package chức năng **không** gọi thành phần nào của package khác. Kiểm tra bằng ArchUnit.

| Service công khai | Package | Được dùng bởi |
| :--- | :--- | :--- |
| `SourceReader` | source | workspace, scan, graph, explanation |
| `WorkspaceService` | workspace | scan, graph, linking, tracing, explanation |
| `RunningScanRegistry`, `AdapterRegistry`, `ScanQueryService`, `FactsCache` | scan | workspace (chặn sửa/xóa khi đang quét; nhận diện thư mục, bộ lọc nhiễu mặc định; lần quét gần nhất; xóa cache khi xóa Workspace) |
| `GraphQueryService` / `GraphWriteService` / `NodeDetailService` | graph | scan, linking, tracing, explanation, feature, comparison |
| `LinkingService` / `LinkOverrideService` / `EdgeAdviceService` | linking | scan, tracing, explanation |
| `TracingService` | tracing | explanation (kịch bản của luồng đã dò), feature, comparison |
| `AiGateway` | ai | linking, explanation, feature, comparison |
| `FeatureService` | feature | comparison, explanation |
