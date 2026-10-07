# DANH SÁCH HỆ THỐNG API CHI TIẾT — CtV FLOW TRACER

> **Quy ước chung:**
> - Đây là API **nội bộ** giữa Web UI (Angular) và Analysis Engine (Java Spring Boot). Mọi đường dẫn có tiền tố `/api`, dữ liệu dạng JSON.
> - Tool dùng **cá nhân**: không có đăng nhập, không phân quyền. Engine mặc định chỉ lắng nghe `127.0.0.1`.
> - Lỗi trả về theo dạng `{ "error": { "code", "message", "details" } }`, kèm HTTP status phù hợp (`400`, `404`, `409`, `422`, `500`, `503`).
> - API có đánh dấu **SSE** trả dữ liệu dạng luồng (Server-Sent Events) để hiển thị tiến độ hoặc nội dung đang sinh.
> - `node_key` là định danh ổn định của một thành phần qua các lần quét (được URL-encode khi đặt trên đường dẫn).
> - `trace_id` được tính từ (điểm bắt đầu, mức chi tiết, độ sâu, lần quét). Cùng đầu vào luôn cho cùng `trace_id`, nên kết quả dò vết tính lại được bất cứ lúc nào.
> - API ghi *(Mở rộng)* chỉ triển khai sau MVP. API ghi *(Nên có)* làm trong MVP nếu kịp.
> - Mỗi nhóm A–I ứng với nhóm chức năng 1–9 trong [feature_list.md](feature_list.md).

---

## Nhóm A: Quản lý Workspace & nguồn mã

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách Workspace** | `GET /api/workspaces` | *(Không cần input)* | Danh sách Workspace: id, name, số Source, thời điểm và trạng thái quét gần nhất | Sắp xếp theo thời điểm cập nhật mới nhất lên đầu. |
| 2 | **Tạo Workspace** | `POST /api/workspaces` | name, description, sources[] (tùy chọn: name, role, root_path, include_globs, exclude_globs) | Đối tượng Workspace vừa tạo kèm danh sách Source | Kiểm tra `name` không trùng (`409` nếu trùng). Nếu gửi kèm Source thì tự chạy nhận diện công nghệ. |
| 3 | **Lấy chi tiết Workspace** | `GET /api/workspaces/:id` | workspace_id (param) | Thông tin Workspace, danh sách Source, lần quét gần nhất | Dữ liệu cho trang tổng quan và trang cài đặt Workspace. |
| 4 | **Cập nhật Workspace** | `PATCH /api/workspaces/:id` | param: id; body: name, description | Workspace đã cập nhật | Chỉ sửa thông tin mô tả. |
| 5 | **Xóa Workspace** | `DELETE /api/workspaces/:id` | workspace_id (param) | `204 No Content` | Xóa toàn bộ kết quả phân tích, cache AI và dữ liệu người dùng của Workspace. Không đụng tới mã nguồn. Trả `409` nếu đang có lần quét chạy. |
| 6 | **Duyệt thư mục** | `GET /api/fs/browse` | path (query, tương đối trong `SOURCE_ROOT`) | Danh sách thư mục con (tên, đường dẫn, có phải repo Git hay không) | Chỉ trả thư mục, không trả nội dung file. Chặn truy cập ra ngoài `SOURCE_ROOT` (`400`). |
| 7 | **Thêm Source** | `POST /api/workspaces/:id/sources` | param: id; body: name, role, root_path, include_globs, exclude_globs, parent_source_id | Source vừa tạo | Kiểm tra thư mục tồn tại. Nếu Source nằm trong một Source khác thì tự thêm luật loại trừ vào Source cha. |
| 8 | **Cập nhật Source** | `PATCH /api/workspaces/:id/sources/:sourceId` | param: id, sourceId; body: name, role, root_path, include_globs, exclude_globs | Source đã cập nhật | Đổi đường dẫn hoặc luật lọc thì lần quét tiếp theo sẽ phân tích lại Source này. |
| 9 | **Xóa Source** | `DELETE /api/workspaces/:id/sources/:sourceId` | param: id, sourceId | `204 No Content` | Xóa Source cùng kết quả phân tích của nó; gỡ luật loại trừ đã thêm vào Source cha (nếu có). |
| 10 | **Nhận diện công nghệ** | `POST /api/workspaces/:id/detect` | workspace_id (param) | Với mỗi Source: danh sách công nghệ (adapter, phiên bản, độ phù hợp) | Chạy lại nhận diện theo các file đặc trưng. Người dùng sửa tay kết quả qua API số 8. |
| 11 | **Gợi ý tách Source hệ thống cũ** *(Mở rộng)* | `GET /api/workspaces/:id/legacy-suggestions` | workspace_id (param) | Danh sách thư mục nên tách thành Source `LEGACY` kèm lý do | Phát hiện cụm lớn file của công nghệ khác nằm trong Source Backend. |
| 12 | **Xuất Workspace** *(Nên có)* | `GET /api/workspaces/:id/export` | workspace_id (param) | File JSON (tải về) | Gồm cấu hình Source (đường dẫn tương đối), luật liên kết, liên kết đã chốt tay, nhóm chức năng. Không chứa mã nguồn hay kết quả phân tích. |
| 13 | **Nhập Workspace** *(Nên có)* | `POST /api/workspaces/import` | file JSON (multipart) | Workspace vừa tạo | Kiểm tra phiên bản định dạng file; đổi tên tự động nếu trùng. |
| 14 | **Nạp Source từ file zip** *(Mở rộng)* | `POST /api/workspaces/:id/sources/upload` | param: id; body: file zip (multipart), name, role | Source vừa tạo | Giải nén vào thư mục riêng của tool rồi khai báo như Source thường. |
| 15 | **Nạp Source từ Git** *(Mở rộng)* | `POST /api/workspaces/:id/sources/git` | param: id; body: git_url, branch, access_token (chỉ đọc), name, role | Source vừa tạo | Clone repo về thư mục riêng của tool. Token không được lưu sau khi clone xong. |

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
| 1 | **Lấy cây API Backend** | `GET /api/workspaces/:id/trees/backend` | workspace_id (param) | Cây `Module → Endpoint`: node_key, method, path, mô tả ngắn, cờ cảnh báo | Module theo `@Tag`, sau đó theo Controller, cuối cùng theo package. |
| 2 | **Lấy cây thành phần Frontend** | `GET /api/workspaces/:id/trees/frontend` | workspace_id (param) | Cây `Ứng dụng → Màn hình → Tab → Hộp thoại/Form → Phần tử`: node_key, loại, nhãn tiếng Việt, cờ "gọi API"/"chỉ giao diện" | Dựng từ quan hệ chứa-trong giữa các thành phần. |
| 3 | **Tìm kiếm** *(Nên có)* | `GET /api/workspaces/:id/search` | param: id; query: q, kinds (tùy chọn), limit | Danh sách kết quả nhóm theo loại: node_key, tên hiển thị, loại, vị trí | Tìm theo đường dẫn API, nhãn nút, tên màn hình, tên class, tên bảng (tìm kiếm toàn văn trên PostgreSQL). |
| 4 | **Lấy chi tiết thành phần** | `GET /api/workspaces/:id/nodes/:nodeKey` | workspace_id, node_key (param) | Thông tin thành phần, danh sách "Được gọi từ", "Gọi tới" | Dữ liệu cho panel phải khi chọn một thành phần trên cây hoặc diagram. |
| 5 | **Lấy đoạn mã nguồn** | `GET /api/workspaces/:id/nodes/:nodeKey/source` | param: id, nodeKey; query: context_lines (mặc định 5) | Đường dẫn file, số dòng bắt đầu/kết thúc, nội dung đoạn code | Chỉ đọc. Không trả file nằm ngoài Source. |
| 6 | **Đối chiếu với OpenAPI** *(Nên có)* | `POST /api/workspaces/:id/openapi-check` | param: id; body: openapi_url hoặc file `openapi.json` (multipart) | Báo cáo: endpoint khớp, chỉ có trong code, chỉ có trong Swagger; số mô tả được bổ sung | Lấy thêm `summary`/`description` từ Swagger cho các endpoint thiếu mô tả. |

---

## Nhóm D: Dò vết & trực quan hóa luồng

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Dò vết luồng** | `POST /api/workspaces/:id/traces` | param: id; body: start_key, detail_level (`LAYER` / `COMPONENT` / `DETAIL`), max_depth (mặc định 12) | Trace: trace_id, participants[], steps[] (thứ tự, từ, tới, nhãn, mức tin cậy, lý do), blocks[] (`alt`/`opt`), warnings[] | Dùng chung cho 3 kiểu xuất phát: từ phần tử giao diện, từ endpoint Backend, luồng chỉ có giao diện. Tự áp liên kết chốt tay và bộ lọc nhiễu. Nếu luồng đi qua cạnh có nhiều ứng viên chưa được xử lý thì tự gọi AI chọn (xem Nhóm E, API 2). |
| 2 | **Xuất diagram** *(Nên có)* | `GET /api/workspaces/:id/traces/:traceId/export` | param: id, traceId; query: format (`mermaid` / `svg`) | File Mermaid hoặc SVG | Ảnh PNG được giao diện chuyển từ SVG ngay trên trình duyệt. |
| 3 | **Lấy flowchart** *(Mở rộng)* | `POST /api/workspaces/:id/flowcharts` | param: id; body: start_key, max_depth | Flowchart: danh sách khối (bắt đầu, xử lý, điều kiện, lỗi, kết thúc) và đường nối | Dựng từ logic rẽ nhánh bên trong các hàm trên đường đi. |

---

## Nhóm E: Liên kết Frontend – Backend & độ tin cậy

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy chi tiết liên kết** | `GET /api/workspaces/:id/edges/:edgeId` | workspace_id, edge_id (param) | Loại liên kết, mức tin cậy, lý do, danh sách ứng viên (kèm điểm), vị trí code của điểm gọi | Hiển thị khi người dùng di chuột hoặc bấm vào mũi tên. |
| 2 | **Nhờ AI chọn liên kết** | `POST /api/workspaces/:id/edges/:edgeId/resolve` | param: id, edgeId; body: force (bỏ qua cache, tùy chọn) | Liên kết cập nhật: `AI_RESOLVED` (kèm lý do) hoặc giữ `UNRESOLVED` | AI chỉ được chọn trong danh sách ứng viên; kết quả được kiểm tra trước khi lưu. Trả `422` nếu liên kết không có ứng viên. |
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
| 1 | **Lấy nội dung tab Diễn giải** (hỗ trợ SSE) | `GET /api/workspaces/:id/nodes/:nodeKey/explanation` | param: id, nodeKey; query: stream (boolean) | Mục đích (kèm nguồn: annotation / Javadoc / tài liệu / AI), thuộc chức năng, đầu vào (JSON), đầu ra (JSON), các trường hợp lỗi, ràng buộc | Phần tất định trả ngay. Mục đích chỉ gọi AI khi không có nguồn nào khác; khi `stream=true` thì nội dung AI hiện dần. |
| 2 | **Lấy dữ liệu mẫu** | `GET /api/workspaces/:id/nodes/:nodeKey/mock-data` | workspace_id, node_key (param) | JSON: request mẫu, response mẫu, nguồn sinh (`AI` / `RULE`) | Có cache thì trả ngay. Chưa có thì AI sinh **một lần** từ cấu trúc DTO (không có dữ liệu thật), kiểm tra khớp cấu trúc rồi lưu. AI tắt hoặc lỗi thì dùng bộ sinh tất định. |
| 3 | **Lấy nội dung tab Kịch bản** | `GET /api/workspaces/:id/traces/:traceId/scenario` | param: id, traceId; query: polish (boolean, mặc định theo cấu hình) | Danh sách bước: số thứ tự (kèm bước con 3a, 3b), câu mô tả, step tương ứng trên diagram, chi tiết kỹ thuật (tên hàm, `METHOD /path`, `file:dòng`) | Khung câu sinh tất định từ trace. Khi `polish=true` thì AI làm mượt câu; nếu số bước hoặc thành phần bị lệch thì dùng lại câu gốc. |
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
| 7 | **Dò vết cấp chức năng** | `POST /api/workspaces/:id/features/:featureId/trace` | param: id, featureId; body: detail_level, merge_duplicates (boolean) | Trace ghép: mỗi luồng thành viên là một khối có tiêu đề | Cùng cấu trúc với API dò vết (Nhóm D) nên dùng chung bộ vẽ diagram. |
| 8 | **Lấy diễn giải cấp chức năng** | `GET /api/workspaces/:id/features/:featureId/summary` | workspace_id, feature_id (param) | Mục đích chung, danh sách bước lớn, bảng dữ liệu bị tác động | Mục đích chung do AI tóm tắt từ diễn giải của các thành viên; có cache. |
| 9 | **Lấy nhóm chức năng chứa thành phần** | `GET /api/workspaces/:id/nodes/:nodeKey/features` | workspace_id, node_key (param) | Danh sách Feature chứa thành phần và Feature được gợi ý | Hiện ở panel phải khi mở một thành phần đơn lẻ. |

---

## Nhóm H: So sánh & đối chiếu hệ thống cũ – mới *(Mở rộng)*

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy gợi ý thành phần để so sánh** | `GET /api/workspaces/:id/nodes/:nodeKey/compare-suggestions` | param: id, nodeKey; query: limit | Danh sách gợi ý có xếp hạng kèm lý do (gọi tới, được gọi bởi, cùng nhóm, cùng bảng, luồng tương ứng ở hệ thống cũ) | Ưu tiên cách tất định; AI chỉ xếp hạng lại khi có quá nhiều ứng viên. |
| 2 | **So sánh hai luồng** | `POST /api/workspaces/:id/compare/diff` | body: left_trace_id, right_trace_id | Danh sách thành phần và bảng dữ liệu chỉ có ở một bên, các bước tương ứng | Giao diện dùng để tô màu chỗ khác nhau trên hai diagram. |
| 3 | **Tìm luồng tương ứng ở hệ thống cũ** | `GET /api/workspaces/:id/nodes/:nodeKey/legacy-matches` | param: id, nodeKey (thành phần hệ thống mới) | Danh sách ứng viên ở hệ thống cũ: node_key, độ phù hợp, lý do, nguồn ghép (tài liệu / tương đồng / AI) | Thứ tự ưu tiên: tài liệu đặc tả ghi file PHP nguồn → độ giống tên, trường, bảng → AI xác nhận. |
| 4 | **Tạo checklist đối soát** | `POST /api/workspaces/:id/compare/checklist` | body: legacy_node_key, new_node_key | Checklist: loại khác biệt (validation, nhánh lỗi, quyền, dữ liệu), mô tả, vị trí code ở hai phía | AI tổng hợp từ hai luồng và hai bộ ràng buộc; có cache theo cặp thành phần. |

---

## Nhóm I: Cấu hình AI & hệ thống

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy cấu hình AI** | `GET /api/settings/ai` | *(Không cần input)* | provider, model, trạng thái bật/tắt từng mục đích, hạn mức token/ngày, trạng thái sẵn sàng | **Không bao giờ trả API key.** Provider, model và key đọc từ biến môi trường. |
| 2 | **Cập nhật mục đích dùng AI** | `PATCH /api/settings/ai` | purposes (danh sách mục đích và trạng thái bật/tắt) | Cấu hình đã lưu | Không cho sửa provider hay key qua API. |
| 3 | **Kiểm tra kết nối AI** | `POST /api/settings/ai/test` | *(Không cần input)* | Thành công/thất bại, độ trễ, thông điệp lỗi (đã loại bỏ thông tin bí mật) | Gửi một yêu cầu ngắn tới provider đang cấu hình. |
| 4 | **Lấy bộ lọc nhiễu** | `GET /api/workspaces/:id/noise-filters` | workspace_id (param) | Danh sách mẫu class/package bị ẩn (mặc định và thủ công) | — |
| 5 | **Cập nhật bộ lọc nhiễu** | `PUT /api/workspaces/:id/noise-filters` | param: id; body: danh sách mẫu thủ công | Danh sách đã lưu | Áp dụng cho các lần dò vết tiếp theo. |
| 6 | **Lấy thống kê sử dụng AI** *(Nên có)* | `GET /api/ai/usage` | from_date, to_date, workspace_id (tùy chọn) | Số lượt gọi, số token vào/ra, tỷ lệ lỗi, độ trễ trung bình theo từng mục đích | Tổng hợp từ nhật ký gọi AI (không chứa nội dung prompt). |
| 7 | **Kiểm tra tình trạng hệ thống** | `GET /api/health` | *(Không cần input)* | Trạng thái Engine, CSDL, provider AI, phiên bản | Dùng cho Docker healthcheck và chỉ báo trạng thái trên giao diện. |
