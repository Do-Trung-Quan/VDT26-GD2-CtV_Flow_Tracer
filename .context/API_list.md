# DANH SÁCH HỆ THỐNG API CHI TIẾT — CtV FLOW TRACER

> **Quy ước chung:**
> - Đây là API **nội bộ** giữa Web UI (Angular) và Analysis Engine (Java Spring Boot). Mọi đường dẫn có tiền tố `/api`, dữ liệu dạng JSON.
> - Tool dùng **cá nhân**: không có đăng nhập, không phân quyền. Engine mặc định chỉ lắng nghe `127.0.0.1`.
> - Lỗi trả về theo dạng `{ "error": { "code", "message", "details" } }`, kèm HTTP status phù hợp (`400`, `404`, `409`, `422`, `500`, `503`).
> - API có đánh dấu **SSE** trả dữ liệu dạng luồng (Server-Sent Events) để hiển thị tiến độ hoặc nội dung đang sinh.
> - `node_key` là định danh ổn định của một thành phần qua các lần quét (được URL-encode khi đặt trên đường dẫn).
> - `trace_id` được tính từ (điểm bắt đầu, độ sâu, lần quét, liên kết chốt tay). Cùng đầu vào luôn cho cùng `trace_id`, nên kết quả dò vết tính lại được bất cứ lúc nào.
> - API ghi *(Mở rộng)* chỉ triển khai sau MVP. API ghi *(Nên có)* làm trong MVP nếu kịp.
> - Mỗi nhóm A–I ứng với nhóm chức năng 1–9 trong [feature_list.md](feature_list.md).

---

## Nhóm A: Quản lý Workspace & nguồn mã

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách Workspace** | `GET /api/workspaces` | *(Không cần input)* | Danh sách Workspace: id, name, số Source, thời điểm và trạng thái quét gần nhất | Sắp xếp theo thời điểm cập nhật mới nhất lên đầu. |
| 2 | **Tạo Workspace** | `POST /api/workspaces` | name, description, sources[] (tùy chọn: root_path, exclude_globs) | Đối tượng Workspace vừa tạo kèm danh sách Source | Kiểm tra `name` không trùng (`409` nếu trùng). Vai trò và công nghệ của từng Source lấy theo kết quả nhận diện (API số 10); thư mục có nhiều gốc dự án được tách thành nhiều Source. |
| 3 | **Lấy chi tiết Workspace** | `GET /api/workspaces/:id` | workspace_id (param) | Thông tin Workspace, danh sách Source, lần quét gần nhất | Dữ liệu cho trang tổng quan và trang cài đặt Workspace. |
| 4 | **Cập nhật Workspace** | `PATCH /api/workspaces/:id` | param: id; body: name, description | Workspace đã cập nhật | Chỉ sửa thông tin mô tả. |
| 5 | **Xóa Workspace** | `DELETE /api/workspaces/:id` | workspace_id (param) | `204 No Content` | Xóa toàn bộ kết quả phân tích, cache AI và dữ liệu người dùng của Workspace. Không đụng tới mã nguồn. Trả `409` nếu đang có lần quét chạy. |
| 6 | **Duyệt thư mục** | `GET /api/fs/browse` | path (query, tương đối trong `SOURCE_ROOT`) | Danh sách thư mục con (tên, đường dẫn, có phải repo Git hay không) | Chỉ trả thư mục, không trả nội dung file. Chặn truy cập ra ngoài `SOURCE_ROOT` (`400`). |
| 7 | **Thêm Source** | `POST /api/workspaces/:id/sources` | param: id; body: root_path, name (tùy chọn), exclude_globs (tùy chọn) | Danh sách Source vừa tạo | Kiểm tra thư mục tồn tại và có gốc dự án (`422` nếu không có). Vai trò tự nhận diện. Nếu Source nằm trong một Source khác thì tự thêm luật loại trừ vào Source cha. |
| 8 | **Cập nhật Source** | `PATCH /api/workspaces/:id/sources/:sourceId` | param: id, sourceId; body: name, exclude_globs | Source đã cập nhật | Đổi danh sách loại trừ thì lần quét tiếp theo sẽ phân tích lại Source này. |
| 9 | **Xóa Source** | `DELETE /api/workspaces/:id/sources/:sourceId` | param: id, sourceId | `204 No Content` | Xóa Source cùng kết quả phân tích của nó; gỡ luật loại trừ đã thêm vào Source cha (nếu có). |
| 10 | **Nhận diện thư mục** | `POST /api/fs/detect` | root_path (tương đối trong `SOURCE_ROOT`) | `VALID` + danh sách gốc dự án (đường dẫn, vai trò, công nghệ, phiên bản); `INSIDE_PROJECT` + gốc dự án gợi ý; hoặc `NOT_FOUND` | Dùng khi Import dự án và khi thêm Source, trước khi lưu. Quét tối đa 4 tầng thư mục con theo dấu hiệu dự án (Nhóm 1 trong [feature_list.md](feature_list.md)). |
| 11 | **Gợi ý tách Source hệ thống cũ** *(Mở rộng)* | `GET /api/workspaces/:id/legacy-suggestions` | workspace_id (param) | Danh sách thư mục nên tách thành Source `LEGACY` kèm lý do | Phát hiện cụm lớn file của công nghệ khác nằm trong Source Backend. |
| 12 | **Nạp Source từ file zip** *(Mở rộng)* | `POST /api/workspaces/:id/sources/upload` | param: id; body: file zip (multipart), name, role | Source vừa tạo | Giải nén vào thư mục riêng của tool rồi khai báo như Source thường. |
| 13 | **Nạp Source từ Git** *(Mở rộng)* | `POST /api/workspaces/:id/sources/git` | param: id; body: git_url, branch, access_token (chỉ đọc), name, role | Source vừa tạo | Clone repo về thư mục riêng của tool. Token không được lưu sau khi clone xong. |

---

## Nhóm B: Quét & phân tích mã nguồn

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Bắt đầu quét** | `POST /api/workspaces/:id/scans` | param: id; body: trigger (`IMPORT` / `RESCAN`) | `202 Accepted` + scan_run_id | `IMPORT` phân tích toàn bộ; `RESCAN` chỉ phân tích file thay đổi (so mã băm). Trả `409` nếu Workspace đang có lần quét chạy. |
| 2 | **Theo dõi tiến độ quét** (SSE) | `GET /api/scans/:scanRunId/events` | scan_run_id (param) | Luồng sự kiện: giai đoạn, số file đã xử lý / tổng, thông điệp; sự kiện kết thúc | Giao diện dùng để hiển thị thanh tiến độ theo thời gian thực. |
| 3 | **Lấy trạng thái lần quét** | `GET /api/scans/:scanRunId` | scan_run_id (param) | status, thống kê (số file, số thành phần, số liên kết, số lỗi, thời gian từng giai đoạn) | Dùng khi tải lại trang hoặc mất kết nối SSE. |
| 4 | **Hủy lần quét** | `POST /api/scans/:scanRunId/cancel` | scan_run_id (param) | Lần quét với trạng thái `CANCELLED` | Dừng lần quét; kết quả của lần quét thành công trước đó vẫn được giữ. |
| 5 | **Lấy lịch sử quét** | `GET /api/workspaces/:id/scans` | param: id; query: page, limit | Danh sách lần quét (phân trang) | Mới nhất lên đầu. |
| 6 | **Lấy danh sách file lỗi** | `GET /api/scans/:scanRunId/errors` | param: scanRunId; query: page, limit | Danh sách file không phân tích được: đường dẫn, lý do | Phục vụ trạng thái `PARTIAL`. |
| 7 | **Bật/tắt tự quét khi code đổi** *(Mở rộng)* | `PATCH /api/workspaces/:id/watch` | param: id; body: enabled (boolean) | Trạng thái theo dõi hiện tại | Theo dõi thay đổi file và tự chạy `RESCAN` (gộp các thay đổi liên tiếp trước khi quét). |

> Quét bằng dòng lệnh *(Mở rộng)* dùng trực tiếp thư viện phân tích qua lệnh `flowtracer scan <path> --out ir.json`, không đi qua API.

---

## Nhóm C: Khám phá cấu trúc dự án

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy cây API Backend** | `GET /api/workspaces/:id/trees/backend` | workspace_id (param); query: role (`BACKEND` mặc định / `LEGACY` *(mở rộng)* — cây Module → Lớp xử lý → Action của hệ thống cũ) | Cây `Module → Endpoint`: node_key, method, path, mô tả ngắn, cờ cảnh báo | Module theo `@Tag`, sau đó theo Controller, cuối cùng theo package. |
| 2 | **Lấy cây thành phần Frontend** | `GET /api/workspaces/:id/trees/frontend` | workspace_id (param) | Cây `Ứng dụng → Màn hình → Tab → Hộp thoại/Form → Phần tử`: node_key, loại, nhãn tiếng Việt, cờ "gọi API"/"chỉ giao diện" | Dựng từ quan hệ chứa-trong giữa các thành phần. |
| 3 | **Tìm kiếm** *(Nên có)* | `GET /api/workspaces/:id/search` | param: id; query: q, kinds (tùy chọn), limit | Danh sách kết quả nhóm theo loại: node_key, tên hiển thị, loại, vị trí | Tìm theo đường dẫn API, nhãn nút, tên màn hình, tên class, tên bảng (tìm kiếm toàn văn trên PostgreSQL). |
| 4 | **Lấy chi tiết thành phần** | `GET /api/workspaces/:id/nodes/:nodeKey` | workspace_id, node_key (param) | Thông tin thành phần, danh sách "Được gọi từ", "Gọi tới" | Với API: "Được gọi từ" là thành phần UI / API khác gọi tới nó. Với thành phần UI: chuỗi thành phần chứa nó, từ cao nhất xuống. |
| 5 | **Lấy đoạn mã nguồn** | `GET /api/workspaces/:id/nodes/:nodeKey/source` | param: id, nodeKey; query: context_lines (mặc định 5) | Danh sách đoạn code theo từng lớp trong luồng (vai trò lớp, đường dẫn file, số dòng, nội dung) | Mỗi đoạn là một tab trong popup "Xem code". Chỉ đọc. Không trả file nằm ngoài Source. |
| 6 | **Đối chiếu với OpenAPI** *(Nên có)* | `POST /api/workspaces/:id/openapi-check` | param: id; body: openapi_url (tùy chọn) | Báo cáo: nguồn đặc tả đã dùng, endpoint khớp, chỉ có trong code, chỉ có trong Swagger | Không có URL thì tự tìm file đặc tả trong repo (`openapi.yaml/json`, `swagger.*`). Nội dung không phải OpenAPI 3 / Swagger 2 thì trả `422`. Không nhận file tải lên. |

---

## Nhóm D: Dò vết & trực quan hóa luồng

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Dò vết luồng** | `POST /api/workspaces/:id/traces` | param: id; body: start_key, max_depth (mặc định 12) | Trace: trace_id, participants[] (id, nhãn, vai trò), steps[] (thứ tự, từ, tới, nhãn kỹ thuật ngắn, mức tin cậy, lý do, cờ chốt tay), blocks[] (`alt`/`opt`/`loop`), warnings[] | Dùng chung cho 3 kiểu xuất phát: từ phần tử giao diện, từ endpoint Backend, luồng chỉ có giao diện. Tự áp liên kết chốt tay và bộ lọc nhiễu. Cạnh chưa chắc chắn giữ `UNRESOLVED` kèm ứng viên; **không gọi AI**. Cột dựng theo §2.2.4 của overview. |
| 2 | **Xuất diagram** *(Nên có)* | `GET /api/workspaces/:id/traces/:traceId/export` | param: id, traceId; query: format (`plantuml` / `svg`) | Mã PlantUML hoặc file SVG | Ảnh PNG được giao diện chuyển từ SVG ngay trên trình duyệt. |
| 3 | **Lấy flowchart** *(Mở rộng)* | `POST /api/workspaces/:id/flowcharts` | param: id; body: start_key, max_depth | Flowchart: danh sách khối (bắt đầu, xử lý, điều kiện, lỗi, kết thúc) và đường nối | Dựng từ logic rẽ nhánh bên trong các hàm trên đường đi. |

---

## Nhóm E: Liên kết Frontend – Backend & độ tin cậy

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy chi tiết liên kết** | `GET /api/workspaces/:id/edges/:edgeId` | workspace_id, edge_id (param) | Loại liên kết, mức tin cậy, lý do, danh sách ứng viên (kèm mức khớp Cao / Trung bình / Thấp), vị trí code của điểm gọi, liên kết đã chốt (nếu có) | Hiển thị khi người dùng di chuột hoặc bấm vào mũi tên. |
| 2 | **Nhờ AI phân tích liên kết** | `POST /api/workspaces/:id/edges/:edgeId/advice` | param: id, edgeId; body: force (bỏ qua cache, tùy chọn) | Lời giải thích cho từng ứng viên (nên/không nên chọn, lý do dẫn chứng từ code); có thể kèm **một** ứng viên AI đề xuất thêm | Chỉ gọi khi người dùng bấm nút. **Không đổi liên kết.** Ứng viên đề xuất thêm chỉ được trả về khi là node có thật trong IR và hợp lệ về kiểu. Trả `503` khi AI tắt hoặc lỗi. |
| 3 | **Lấy danh sách liên kết đã chốt tay** | `GET /api/workspaces/:id/link-overrides` | workspace_id (param) | Danh sách: from_node_key, to_node_key, quyết định, ghi chú, thời điểm | Quản lý các quyết định đã chốt. |
| 4 | **Chốt tay liên kết** | `POST /api/workspaces/:id/link-overrides` | param: id; body: from_node_key, to_node_key, edge_kind, decision (`PIN` / `REJECT`), note | Liên kết chốt tay vừa tạo | Ghi đè kết quả tự động; áp dụng ngay cho các lần dò vết và giữ qua các lần quét sau. |
| 5 | **Bỏ liên kết đã chốt tay** | `DELETE /api/workspaces/:id/link-overrides/:overrideId` | workspace_id, override_id (param) | `204 No Content` | Quay lại dùng kết quả tự động. |
| 6 | **Lấy luật liên kết** | `GET /api/workspaces/:id/link-rules` | workspace_id (param) | Danh sách luật: loại (tiền tố, rewrite, context-path, điểm thực thi truy vấn), giá trị, nguồn (tự phát hiện / thủ công) | Xem các luật tool đã tự tìm thấy trong cấu hình dự án. |
| 7 | **Cập nhật luật liên kết** | `PUT /api/workspaces/:id/link-rules` | param: id; body: danh sách luật thủ công | Danh sách luật đã lưu | Tính lại các liên kết Frontend – Backend ngay sau khi lưu, không cần quét lại toàn bộ. |
| 8 | **Đối chiếu route thực tế** *(Mở rộng)* | `POST /api/workspaces/:id/runtime/mappings-check` | param: id; body: backend_base_url | Báo cáo khớp / lệch giữa endpoint tĩnh và route thật | Đọc `/actuator/mappings` và `/v3/api-docs` của Backend đang chạy. |
| 9 | **Nhận dữ liệu theo dõi lúc chạy** *(Mở rộng)* | `POST /api/workspaces/:id/runtime/otlp/v1/traces` | Dữ liệu trace theo chuẩn OTLP (từ OpenTelemetry agent) | `200 OK`; các liên kết khớp được nâng lên `RUNTIME_VERIFIED` | Chỉ giữ thông tin kỹ thuật (route, bảng, tên hàm); bỏ toàn bộ giá trị dữ liệu nghiệp vụ. |

---

## Nhóm F: Diễn giải & dữ liệu mẫu

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy nội dung tab Diễn giải** (hỗ trợ SSE) | `GET /api/workspaces/:id/nodes/:nodeKey/explanation` | param: id, nodeKey; query: stream (boolean) | Thành phần chứa: tên, loại, mô tả ngắn. Thành phần con: mục đích (kèm nguồn: AI / annotation / Javadoc / tài liệu / khung), đầu vào (JSON, theo từng API), đầu ra (JSON), các trường hợp lỗi, ràng buộc (kèm ràng buộc thô), được gọi từ, gọi tới | Phần tất định trả ngay. Mục đích và ràng buộc gọn do AI viết (có cache); AI tắt/lỗi thì dùng annotation/Javadoc và ràng buộc thô. Khi `stream=true` thì nội dung AI hiện dần. |
| 2 | **Lấy dữ liệu mẫu** | `GET /api/workspaces/:id/nodes/:nodeKey/mock-data` | workspace_id, node_key (param) | JSON: request mẫu, response mẫu, nguồn sinh (`AI` / `RULE`) | Có cache thì trả ngay. Chưa có thì AI sinh **một lần** từ cấu trúc DTO (không có dữ liệu thật), kiểm tra khớp cấu trúc rồi lưu. AI tắt hoặc lỗi thì dùng bộ sinh tất định. |
| 3 | **Lấy nội dung tab Kịch bản** | `GET /api/workspaces/:id/traces/:traceId/scenario` | param: id, traceId; query: polish (boolean, mặc định theo cấu hình) | actors[] (tên cột, vai trò, mô tả ngắn); steps[] (số bước = số mũi tên, câu mô tả, nhánh `alt` kèm điều kiện) | Khung câu sinh tất định từ trace. Khi `polish=true` thì AI viết thành lời kể tự nhiên; nếu số bước, thứ tự hoặc thành phần bị lệch thì dùng lại khung câu. |
| 4 | **Sinh lại nội dung AI** | `POST /api/workspaces/:id/ai-artifacts/regenerate` | param: id; body: purpose (`PURPOSE_TEXT` / `SCENARIO_POLISH` / `MOCK_DATA` / …), target_key | Nội dung AI mới | Bỏ qua bộ nhớ đệm và gọi AI lại cho đúng mục đích và đối tượng được chỉ định. |

---

## Nhóm G: Nhóm chức năng (Feature) *(Mở rộng)*

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách nhóm chức năng** | `GET /api/workspaces/:id/features` | workspace_id (param) | Danh sách Feature: id, name, số thành viên, nguồn gốc (tự tạo / từ gợi ý) | Dữ liệu cho tab "Chức năng" ở cột trái. |
| 2 | **Tạo nhóm chức năng** | `POST /api/workspaces/:id/features` | param: id; body: name, description, members[] (node_key, thứ tự) | Feature vừa tạo | Kiểm tra `name` không trùng trong Workspace (`409`). |
| 3 | **Lấy chi tiết nhóm chức năng** | `GET /api/workspaces/:id/features/:featureId` | workspace_id, feature_id (param) | Thông tin Feature và danh sách thành viên (kèm cờ "không còn tồn tại" nếu thành phần đã bị xóa khỏi code) | — |
| 4 | **Cập nhật nhóm chức năng** | `PATCH /api/workspaces/:id/features/:featureId` | param: id, featureId; body: name, description, members[] | Feature đã cập nhật | Thay toàn bộ danh sách thành viên theo thứ tự mới. |
| 5 | **Xóa nhóm chức năng** | `DELETE /api/workspaces/:id/features/:featureId` | workspace_id, feature_id (param) | `204 No Content` | Chỉ xóa nhóm, không ảnh hưởng thành phần. |
| 6 | **Lấy gợi ý nhóm chức năng** | `GET /api/workspaces/:id/features/suggestions` | workspace_id (param) | Danh sách nhóm gợi ý: tên đề xuất, thành viên, lý do gom nhóm | Gom tất định theo màn hình Frontend, module Backend và bảng dữ liệu dùng chung. |
| 7 | **Dò vết cấp chức năng** | `POST /api/workspaces/:id/features/:featureId/trace` | param: id, featureId; body: merge_duplicates (boolean) | Trace ghép: mỗi luồng thành viên là một khối có tiêu đề | Cùng cấu trúc với API dò vết (Nhóm D) nên dùng chung bộ vẽ diagram. |
| 8 | **Lấy diễn giải cấp chức năng** | `GET /api/workspaces/:id/features/:featureId/summary` | workspace_id, feature_id (param) | Mục đích chung, danh sách bước lớn, bảng dữ liệu bị tác động | Mục đích chung do AI tóm tắt từ diễn giải của các thành viên; có cache. |
| 9 | **Lấy nhóm chức năng chứa thành phần** | `GET /api/workspaces/:id/nodes/:nodeKey/features` | workspace_id, node_key (param) | Danh sách Feature chứa thành phần và Feature được gợi ý | Hiện ở panel phải khi mở một thành phần đơn lẻ. |

---

## Nhóm H: So sánh & đối chiếu hệ thống cũ – mới *(Mở rộng)*

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy gợi ý thành phần để so sánh** | `GET /api/workspaces/:id/nodes/:nodeKey/compare-suggestions` | param: id, nodeKey; query: limit | Danh sách gợi ý có xếp hạng kèm lý do (gọi tới, được gọi bởi, cùng nhóm, cùng bảng, luồng tương ứng ở hệ thống cũ) | Ưu tiên cách tất định; AI chỉ xếp hạng lại khi có quá nhiều ứng viên. |
| 2 | **So sánh hai luồng** | `POST /api/workspaces/:id/compare/diff` | body: left_trace_id, right_trace_id | Danh sách thành phần và bảng dữ liệu chỉ có ở một bên, các bước tương ứng | Giao diện dùng để tô màu chỗ khác nhau trên hai diagram. |
| 3 | **Tìm luồng tương ứng ở hệ thống cũ** | `GET /api/workspaces/:id/nodes/:nodeKey/legacy-matches` | param: id, nodeKey (thành phần hệ thống mới) | Danh sách ứng viên ở hệ thống cũ: node_key, độ phù hợp, lý do, nguồn ghép (tài liệu / tương đồng / AI) | Thứ tự ưu tiên: tài liệu đặc tả ghi file PHP nguồn → độ giống tên, trường, bảng → AI xác nhận. |

---

## Nhóm I: Cấu hình AI & hệ thống

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy cấu hình AI** | `GET /api/settings/ai` | *(Không cần input)* | provider, model, trạng thái bật/tắt từng mục đích, hạn mức token/ngày, trạng thái sẵn sàng | **Không bao giờ trả API key.** Provider, model và key đọc từ biến môi trường. |
| 2 | **Cập nhật mục đích dùng AI** | `PATCH /api/settings/ai` | purposes (danh sách mục đích và trạng thái bật/tắt) | Cấu hình đã lưu | Không cho sửa provider hay key qua API. |
| 3 | **Kiểm tra kết nối AI** | `POST /api/settings/ai/test` | *(Không cần input)* | Thành công/thất bại, độ trễ, thông điệp lỗi (đã loại bỏ thông tin bí mật) | Gửi một yêu cầu ngắn tới provider đang cấu hình. |
| 4 | **Lấy bộ lọc nhiễu** | `GET /api/workspaces/:id/noise-filters` | workspace_id (param) | Danh sách mẫu class/package bị ẩn (mặc định và thủ công) | — |
| 5 | **Cập nhật bộ lọc nhiễu** | `PUT /api/workspaces/:id/noise-filters` | param: id; body: danh sách mẫu thủ công | Danh sách đã lưu | Áp dụng cho các lần dò vết tiếp theo. |
| 6 | **Lấy thống kê sử dụng AI** *(Nên có)* | `GET /api/ai/usage` | from_date, to_date, workspace_id (tùy chọn) | Số lượt gọi, số token vào/ra, số lượt trúng cache, tỷ lệ lỗi, độ trễ trung bình theo từng mục đích; token hôm nay và hạn mức ngày | Lượt gọi, token, lỗi, độ trễ tổng hợp từ `llm_call_log` (không chứa nội dung prompt); lượt trúng cache từ `ai_artifact.hit_count`. |
| 7 | **Kiểm tra tình trạng hệ thống** | `GET /api/health` | *(Không cần input)* | Trạng thái Engine, CSDL, provider AI, phiên bản | Dùng cho Docker healthcheck và chỉ báo trạng thái trên giao diện. |
