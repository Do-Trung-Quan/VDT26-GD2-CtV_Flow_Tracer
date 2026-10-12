# THIẾT KẾ CƠ SỞ DỮ LIỆU — DANH SÁCH CÁC THỰC THỂ (CtV FLOW TRACER)

> **Đọc nhanh:**
> - CSDL là **PostgreSQL 16**, chạy cục bộ cùng tool (Docker Compose), truy cập qua Spring Data JPA (bảng thường) và JdbcClient ghi theo lô (bảng IR); migration bằng Flyway.
> - CSDL này là **của tool**, không phải của dự án được phân tích. Tool không bao giờ kết nối tới CSDL của dự án được phân tích.
> - Sơ đồ quan hệ: [database.dbml](../docs/diagrams/database.dbml) (dán vào [dbdiagram.io](https://dbdiagram.io) để xem).

## Dữ liệu nào được lưu ở đâu

| Loại dữ liệu                                                                               | Nơi lưu                                       | Lý do                                                                        |
| :-------------------------------------------------------------------------------------------| :----------------------------------------------| :-----------------------------------------------------------------------------|
| Mã nguồn dự án                                                                             | Giữ nguyên tại chỗ trên máy, tool **chỉ đọc** | Tool không bao giờ sửa hay sao chép code.                                    |
| Kết quả phân tích ("bản đồ" thành phần và quan hệ, trạng thái từng file)                   | CSDL của tool                                 | Sinh lại được bất cứ lúc nào bằng cách quét lại.                             |
| Nội dung do AI sinh (bộ nhớ đệm)                                                           | CSDL của tool                                 | Không phải gọi AI lại khi code không đổi: tiết kiệm chi phí, phản hồi nhanh. |
| Dữ liệu do người dùng tạo (Workspace, Source, liên kết chốt tay, nhóm chức năng, cấu hình) | CSDL của tool; xuất/nhập được ra file JSON    | Không sinh lại được nên phải giữ cẩn thận.                                   |

## Hai khái niệm cần biết trước khi đọc

- **Node và Edge:** khi quét, tool biến mã nguồn thành một **bản đồ**. Mỗi thành phần (nút bấm, màn hình, hàm, endpoint, bảng dữ liệu…) là một **node**. Mỗi quan hệ giữa hai thành phần ("nút này gọi hàm kia", "hàm này ghi vào bảng kia") là một **edge**. Sequence diagram được vẽ bằng cách đi theo các edge.
- **Key ổn định:** mỗi node có một `key` được tạo từ vị trí logic trong code, ví dụ `java:method:com.acme.CategoryService#create(CategoryReq)`. Mỗi lần quét lại, `id` của node có thể đổi nhưng `key` thì giữ nguyên. Vì vậy dữ liệu người dùng (liên kết chốt tay, nhóm chức năng, bộ nhớ đệm AI) luôn trỏ tới node **bằng `key`**, để không bị mất sau khi quét lại.

---

## 1. Workspace

Mỗi bản ghi là một **dự án đang được phân tích** trong tool, ví dụ "Hệ thống Thư viện". Một Workspace gồm nhiều thư mục mã nguồn (Source) và toàn bộ kết quả phân tích, cấu hình đi kèm. Xóa Workspace thì xóa mọi dữ liệu liên quan trong CSDL, nhưng không đụng tới mã nguồn.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính, định danh duy nhất của Workspace |
| name | VARCHAR(200) | Tên Workspace do người dùng đặt, không được trùng với Workspace khác |
| description | TEXT (nullable) | Mô tả ngắn về dự án |
| settings | JSONB | Các thiết lập riêng của Workspace: luật liên kết Frontend – Backend nhập tay, bộ lọc nhiễu, độ sâu dò vết mặc định. Mặc định là `{}` |
| last_scan_run_id | UUID (FK, nullable) | Lần quét gần nhất, tham chiếu ScanRun.id. NULL nếu chưa quét lần nào |
| created_at | TIMESTAMPTZ | Thời điểm tạo |
| updated_at | TIMESTAMPTZ | Thời điểm cập nhật gần nhất |

## 2. Source

Mỗi bản ghi là **một thư mục mã nguồn** thuộc Workspace, kèm **vai trò** của nó: Backend, Frontend hay hệ thống cũ. Source khai báo theo thư mục chứ không theo repo, nên một Source có thể nằm bên trong Source khác (ví dụ mã PHP cũ nằm trong repo Backend). Khi đó Source cha tự loại trừ thư mục của Source con để không file nào bị phân tích hai lần.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id. Xóa Workspace thì Source bị xóa theo |
| name | VARCHAR(200) | Tên hiển thị, ví dụ "Backend", "Frontend web" |
| role | ENUM('BACKEND', 'FRONTEND', 'LEGACY') | Vai trò của thư mục: mã phía máy chủ, mã giao diện, hay mã của hệ thống cũ dùng để so sánh |
| root_path | TEXT | Đường dẫn thư mục, tính tương đối từ thư mục gốc `SOURCE_ROOT` đã cấu hình cho tool |
| include_globs | JSONB | Danh sách mẫu file **chỉ lấy**, ví dụ `["src/**"]`. Rỗng nghĩa là lấy tất cả |
| exclude_globs | JSONB | Danh sách mẫu file **bỏ qua**, ví dụ `["**/test/**"]`. Hệ thống tự thêm thư mục của Source con vào đây |
| parent_source_id | UUID (FK, nullable) | Source cha nếu thư mục này nằm bên trong một Source khác, tham chiếu Source.id |
| detected_stacks | JSONB | Công nghệ nhận diện được, ví dụ `[{"adapter":"spring","version":"3.3"}]`. Người dùng sửa tay được |
| created_at | TIMESTAMPTZ | Thời điểm tạo |
| updated_at | TIMESTAMPTZ | Thời điểm cập nhật gần nhất |

## 3. ScanRun

Mỗi bản ghi là **một lần quét** mã nguồn của Workspace: quét lần đầu hoặc quét lại. Dùng để hiển thị tiến độ, lịch sử quét, và biết kết quả hiện tại đến từ lần quét nào.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace được quét, tham chiếu Workspace.id |
| trigger | ENUM('IMPORT', 'RESCAN') | Kiểu quét: `IMPORT` phân tích toàn bộ, `RESCAN` chỉ phân tích file đã thay đổi |
| status | ENUM('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED') | Trạng thái: chờ, đang chạy, thành công, thành công một phần (có file lỗi), thất bại, đã hủy |
| stats | JSONB | Số liệu của lần quét (mọi số đều đếm/đo thật): số file theo loại (tổng / thêm / sửa / xóa / không đổi / đọc lại / lấy từ cache / lỗi), số node / edge / endpoint của Workspace sau lần quét, số chẩn đoán lỗi / cảnh báo kèm danh sách chẩn đoán (tối đa 100), thời gian từng giai đoạn đã chạy (danh sách có thứ tự), kết quả từng Source; `noChanges` khi quét lại không có file đổi. Rỗng khi lần quét bị hủy / thất bại |
| error_summary | TEXT (nullable) | Nguyên nhân lỗi chung khi `FAILED`. Lỗi của từng file nằm ở SourceFile |
| started_at | TIMESTAMPTZ | Thời điểm bắt đầu |
| finished_at | TIMESTAMPTZ (nullable) | Thời điểm kết thúc. NULL khi đang chạy |

## 4. SourceFile

Mỗi bản ghi là **một file mã nguồn** đã được tool đọc, kèm **mã băm nội dung**. Đây là cơ sở của quét lại tăng dần: mã băm không đổi nghĩa là file không đổi, nên không cần phân tích lại.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| source_id | UUID (FK) | Source chứa file, tham chiếu Source.id |
| rel_path | TEXT | Đường dẫn file tính từ thư mục của Source. Duy nhất trong mỗi Source |
| content_hash | CHAR(64) | Mã băm SHA-256 của nội dung file, dùng để phát hiện thay đổi |
| language | VARCHAR(20) | Loại file: `java`, `ts`, `html`, `php`…; riêng `config` là file cấu hình ảnh hưởng kết quả (vd `application.yml`) — chỉ lưu mã băm để phát hiện thay đổi, không được phân tích và không tính vào số file |
| size_bytes | INTEGER | Kích thước file (byte) |
| parse_status | ENUM('OK', 'ERROR', 'SKIPPED') | Kết quả phân tích: thành công, lỗi (ví dụ sai cú pháp), hoặc bỏ qua (quá lớn, không hỗ trợ) |
| parse_error | TEXT (nullable) | Lý do lỗi hoặc lý do bỏ qua, hiển thị cho người dùng |
| last_scan_run_id | UUID (FK) | Lần quét gần nhất đã cập nhật file này, tham chiếu ScanRun.id |

## 5. IrNode

Mỗi bản ghi là **một thành phần** trên bản đồ của dự án: một nút bấm, một màn hình, một hàm, một endpoint API, một câu truy vấn hay một bảng dữ liệu. Cây Backend, cây Frontend, kết quả tìm kiếm và các ô trên sequence diagram đều lấy từ bảng này.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính (có thể đổi sau mỗi lần quét lại) |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id |
| key | TEXT | **Định danh ổn định** qua các lần quét, duy nhất trong Workspace. Dữ liệu người dùng trỏ tới node bằng trường này |
| kind | VARCHAR(30) | Loại thành phần, ví dụ: `UI_SCREEN` (màn hình), `UI_ELEMENT` (phần tử tương tác), `API_ENDPOINT` (API), `BE_METHOD` (hàm phía máy chủ), `DB_TABLE` (bảng dữ liệu). Danh sách đầy đủ ở §2.2 của overview |
| source_id | UUID (FK) | Source chứa thành phần, tham chiếu Source.id |
| file_id | UUID (FK, nullable) | File chứa thành phần, tham chiếu SourceFile.id. NULL với thành phần suy ra, không nằm trong file cụ thể (ví dụ bảng dữ liệu) |
| name | TEXT | Tên kỹ thuật trong code, ví dụ `CategoryServiceImpl.create` |
| display_name | TEXT (nullable) | Tên thân thiện tiếng Việt để hiển thị, ví dụ "Dịch vụ Danh mục – Tạo mới", hoặc nhãn nút lấy từ file ngôn ngữ |
| start_line | INTEGER (nullable) | Dòng bắt đầu trong file, để mở đúng vị trí code |
| end_line | INTEGER (nullable) | Dòng kết thúc trong file |
| attrs | JSONB | Thông tin riêng theo loại. Ví dụ endpoint có method, đường dẫn, tham số; phần tử giao diện có loại (nút, ô nhập…) và sự kiện |
| search_text | TSVECTOR | Chỉ mục tìm kiếm toàn văn (tên, tên hiển thị, đường dẫn), phục vụ ô tìm kiếm nhanh |

## 6. IrEdge

Mỗi bản ghi là **một quan hệ có hướng** giữa hai thành phần, ví dụ "nút Lưu kích hoạt hàm onSave", "hàm create gọi hàm save", "lời gọi HTTP ở Frontend đi tới endpoint ở Backend". Mỗi mũi tên trên sequence diagram tương ứng một edge. Edge còn cho biết **tool chắc chắn đến đâu** về quan hệ đó.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id |
| from_node_id | UUID (FK) | Thành phần xuất phát, tham chiếu IrNode.id |
| to_node_id | UUID (FK, nullable) | Thành phần đích, tham chiếu IrNode.id. NULL khi chưa xác định được đích |
| kind | VARCHAR(30) | Loại quan hệ, ví dụ: `TRIGGERS` (kích hoạt), `CALLS` (gọi hàm), `HTTP_REQUEST` (gửi yêu cầu API), `READS`/`WRITES` (đọc/ghi dữ liệu), `CONTAINS` (chứa trong) |
| confidence | ENUM('CERTAIN', 'INFERRED', 'UNRESOLVED', 'RUNTIME_VERIFIED') | Mức tin cậy: chắc chắn; suy luận theo luật; chưa xác định; đã xác minh lúc chạy (mở rộng). AI không bao giờ đổi giá trị này; liên kết chốt tay nằm ở bảng LinkOverride |
| reason | TEXT (nullable) | Giải thích vì sao có mức tin cậy này, hiển thị khi người dùng di chuột vào mũi tên |
| call_order | INTEGER (nullable) | Thứ tự của lời gọi bên trong hàm nguồn, dùng để xếp các bước trên diagram đúng trình tự |
| guard | JSONB (nullable) | Điều kiện bao quanh lời gọi, ví dụ `{"type":"if","text":"code đã tồn tại","branch":"then"}`. Dùng để vẽ nhánh lỗi và sinh mục "Ràng buộc" |
| candidates | JSONB (nullable) | Danh sách thành phần có thể là đích (kèm điểm khớp) khi có nhiều khả năng. Người dùng chốt tay một ứng viên; AI chỉ giải thích |
| file_id | UUID (FK, nullable) | File chứa điểm gọi, tham chiếu SourceFile.id |
| line | INTEGER (nullable) | Dòng của điểm gọi trong file |

## 7. LinkOverride

Mỗi bản ghi là **một quyết định chốt tay** của người dùng về một liên kết: **ghim** (khẳng định A đi tới B) hoặc **loại bỏ** (A không đi tới B). Quyết định này luôn thắng kết quả tự động và được giữ nguyên khi quét lại, vì nó trỏ tới thành phần bằng `key`.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id |
| from_node_key | TEXT | Key của thành phần xuất phát |
| to_node_key | TEXT (nullable) | Key của thành phần đích được ghim. NULL khi chỉ loại bỏ liên kết |
| edge_kind | VARCHAR(30) | Loại quan hệ được chốt, ví dụ `HTTP_REQUEST` |
| decision | ENUM('PIN', 'REJECT') | `PIN` = ghim liên kết này; `REJECT` = liên kết này sai, bỏ đi |
| note | TEXT (nullable) | Ghi chú lý do, ví dụ "Môi trường prod dùng Impl B" |
| created_at | TIMESTAMPTZ | Thời điểm chốt |

## 8. AiArtifact

**Bộ nhớ đệm** cho mọi nội dung do AI sinh ra: mục đích của API, lời kịch bản, lời phân tích liên kết… Mỗi bản ghi gắn với một **mã băm đầu vào**. Đoạn code liên quan không đổi thì dùng lại kết quả cũ, không gọi AI lần nữa.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id |
| target_key | TEXT | Đối tượng được sinh nội dung: key của thành phần, key của luồng, cặp key (so sánh cũ/mới) hoặc id nhóm chức năng |
| purpose | ENUM('PURPOSE_TEXT', 'SCENARIO_POLISH', 'MOCK_DATA', 'EDGE_RESOLUTION', 'LEGACY_MATCH', 'FEATURE_SUMMARY') | Mục đích dùng AI: viết mục đích và ràng buộc; viết lời kịch bản; sinh dữ liệu mẫu (một lần, từ cấu trúc DTO); phân tích liên kết (chỉ giải thích, không đổi liên kết); ghép luồng cũ/mới; tóm tắt nhóm chức năng |
| input_hash | CHAR(64) | Mã băm của toàn bộ đầu vào (đoạn code liên quan, phiên bản prompt, model). Mỗi bộ (workspace, mục đích, đối tượng, mã băm) chỉ có một bản ghi |
| prompt_version | VARCHAR(20) | Phiên bản câu lệnh gửi AI. Đổi prompt thì cache cũ tự hết hiệu lực |
| provider | VARCHAR(50) | Nhà cung cấp AI đã dùng, ví dụ `gemini` |
| model | VARCHAR(50) | Tên model đã dùng |
| content | JSONB | Kết quả AI **đã được kiểm tra hợp lệ** |
| created_at | TIMESTAMPTZ | Thời điểm sinh |
| hit_count | INTEGER | Số lần nội dung này được dùng lại thay vì gọi AI (mặc định 0). Dùng tính tỷ lệ trúng cache trên trang thống kê |
| last_hit_at | TIMESTAMPTZ (nullable) | Lần gần nhất được dùng lại |

## 9. LlmCallLog

**Nhật ký** mỗi lần tool gọi AI, dùng để theo dõi chi phí, hạn mức và lỗi. Bảng này **không lưu nội dung gửi đi**, chỉ lưu mã băm, để tránh lộ mã nguồn hay thông tin nhạy cảm qua nhật ký.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK, nullable) | Workspace phát sinh lời gọi, tham chiếu Workspace.id. NULL với lời gọi hệ thống (ví dụ kiểm tra kết nối) hoặc khi Workspace đã bị xóa |
| purpose | VARCHAR(30) | Mục đích lời gọi (cùng danh sách với AiArtifact.purpose, thêm `CONNECTION_TEST`) |
| provider | VARCHAR(50) | Nhà cung cấp AI |
| model | VARCHAR(50) | Tên model |
| prompt_hash | CHAR(64) | Mã băm của nội dung gửi đi (không lưu nội dung gốc) |
| input_tokens | INTEGER (nullable) | Số token gửi đi |
| output_tokens | INTEGER (nullable) | Số token nhận về |
| latency_ms | INTEGER (nullable) | Thời gian phản hồi (mili giây) |
| status | ENUM('OK', 'ERROR', 'REJECTED_BY_VALIDATION', 'QUOTA_EXCEEDED') | Kết quả: thành công; lỗi từ provider; kết quả AI không qua bước kiểm tra nên bị loại; vượt hạn mức |
| created_at | TIMESTAMPTZ | Thời điểm gọi |

## 10. AppSetting

Lưu **cấu hình chung của tool** mà người dùng chỉnh được trên giao diện, ví dụ bật/tắt từng mục đích dùng AI. Thông tin bí mật (API key) và lựa chọn provider/model **không** nằm ở đây mà chỉ nằm trong biến môi trường.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| key | VARCHAR(100) | Khóa chính, tên cấu hình, ví dụ `ai.purposes` |
| value | JSONB | Giá trị cấu hình, ví dụ `{"PURPOSE_TEXT":true,"SCENARIO_POLISH":false}` |
| updated_at | TIMESTAMPTZ | Thời điểm cập nhật gần nhất |

## 11. Feature *(Mở rộng)*

Mỗi bản ghi là **một nhóm chức năng** do người dùng lưu lại, ví dụ "Quản lý danh mục" gồm các thao tác tải danh sách, thêm, sửa, xóa. Dùng để xem diagram và diễn giải ở cấp cả chức năng. Bảng được tạo ngay từ MVP để khi làm tính năng này không phải thay đổi cấu trúc CSDL.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| id | UUID | Khóa chính |
| workspace_id | UUID (FK) | Workspace sở hữu, tham chiếu Workspace.id |
| name | VARCHAR(200) | Tên nhóm chức năng, không trùng trong cùng Workspace |
| description | TEXT (nullable) | Mô tả nhóm chức năng |
| origin | ENUM('USER', 'SUGGESTED') | `USER` = người dùng tự tạo; `SUGGESTED` = tạo từ gợi ý của hệ thống và đã được người dùng xác nhận |
| created_at | TIMESTAMPTZ | Thời điểm tạo |
| updated_at | TIMESTAMPTZ | Thời điểm cập nhật gần nhất |

## 12. FeatureMember *(Mở rộng)*

Danh sách **thành phần thuộc một nhóm chức năng** và thứ tự của chúng trong quy trình. Thành phần được trỏ tới bằng `key`, nên nhóm chức năng vẫn còn nguyên sau khi quét lại.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| feature_id | UUID (PK, FK) | Nhóm chức năng, tham chiếu Feature.id. Cùng với `node_key` tạo thành khóa chính |
| node_key | TEXT (PK) | Key của thành phần thuộc nhóm (endpoint, nút bấm…) |
| order_index | INTEGER | Thứ tự của thành phần trong quy trình, ví dụ 1 = Tìm kiếm, 2 = Thêm vào giỏ, 3 = Đặt hàng |

---

## Quan hệ giữa các thực thể

| Thực thể A | Thực thể B | Loại quan hệ | Khóa ngoại | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| Workspace | Source | 1 – N | source.workspace_id | Xóa Workspace thì xóa Source |
| Source | Source | 1 – N | source.parent_source_id | Source lồng nhau |
| Workspace | ScanRun | 1 – N | scan_run.workspace_id | |
| ScanRun | Workspace | 1 – 1 (lần quét gần nhất) | workspace.last_scan_run_id | |
| Source | SourceFile | 1 – N | source_file.source_id | |
| ScanRun | SourceFile | 1 – N | source_file.last_scan_run_id | |
| Workspace | IrNode | 1 – N | ir_node.workspace_id | |
| Source | IrNode | 1 – N | ir_node.source_id | |
| SourceFile | IrNode | 1 – N | ir_node.file_id | Xóa hoặc sửa file thì node của file bị xóa và tạo lại |
| IrNode | IrEdge | 1 – N (chiều đi) | ir_edge.from_node_id | |
| IrNode | IrEdge | 1 – N (chiều đến) | ir_edge.to_node_id | |
| SourceFile | IrEdge | 1 – N | ir_edge.file_id | |
| Workspace | LinkOverride, AiArtifact, LlmCallLog, Feature | 1 – N | *.workspace_id | |
| Feature | FeatureMember | 1 – N | feature_member.feature_id | |
| IrNode | LinkOverride, AiArtifact, FeatureMember | Liên kết mềm qua `key` | *(không dùng khóa ngoại)* | Cố ý không ràng buộc khóa ngoại để dữ liệu người dùng không bị mất khi quét lại |
