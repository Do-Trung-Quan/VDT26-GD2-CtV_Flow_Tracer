# BẢNG TỔNG HỢP CHỨC NĂNG HỆ THỐNG — CtV FLOW TRACER

> **Ghi chú:**
> - Tool dùng **cá nhân**, không có đăng nhập hay phân quyền. Vì vậy cột "Phân quyền" được thay bằng cột **Ưu tiên**:
>   - **MVP**: bắt buộc có trong bản MVP.
>   - **Nên có**: làm trong MVP nếu kịp thời gian.
>   - **Mở rộng**: làm sau khi hoàn thành MVP.
> - Chức năng được **chia nhóm theo mục đích sử dụng**. Mỗi nhóm có bảng chức năng, kèm phần **Quy tắc & cách xử lý** để người mới hiểu hệ thống làm gì bên dưới.
> - Tổng số: 77 chức năng chia làm 9 nhóm (48 MVP, 6 Nên có, 23 Mở rộng).
> - API tương ứng với từng nhóm: xem [API_list.md](API_list.md).

---

## Nhóm 1: Quản lý Workspace & nguồn mã

> **Workspace** là một "dự án" trong tool, gồm nhiều **Source**. Mỗi Source là một thư mục mã nguồn có vai trò: `BACKEND`, `FRONTEND` hoặc `LEGACY` (hệ thống cũ).

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách Workspace** | MVP | *(Không cần input)* | Danh sách Workspace kèm số Source, thời điểm quét gần nhất, trạng thái quét | Màn hình khởi đầu; cho phép mở lại nhanh Workspace đã dùng gần nhất. |
| **Tạo Workspace** | MVP | name, description, danh sách Source (tùy chọn) | Workspace mới | Tạo một không gian phân tích mới cho một dự án. |
| **Sửa thông tin Workspace** | MVP | workspace_id, name, description | Workspace đã cập nhật | Đổi tên, mô tả Workspace. |
| **Xóa Workspace** | MVP | workspace_id | Thông báo xóa thành công | Xóa toàn bộ kết quả phân tích và dữ liệu người dùng của Workspace. **Không bao giờ đụng tới mã nguồn.** |
| **Duyệt thư mục trên máy** | MVP | path (thư mục hiện tại) | Danh sách thư mục con | Giúp chọn thư mục mã nguồn ngay trên giao diện, không cần gõ đường dẫn. Chỉ duyệt được bên trong thư mục gốc đã cấu hình (`SOURCE_ROOT`). |
| **Thêm Source** | MVP | workspace_id, name, role, root_path, include/exclude (tùy chọn), parent_source_id (tùy chọn) | Source mới | Khai báo một thư mục mã nguồn và vai trò của nó trong Workspace. |
| **Sửa Source** | MVP | source_id, name, role, root_path, include/exclude | Source đã cập nhật | Đổi vai trò, đường dẫn hoặc luật lọc file của Source. |
| **Xóa Source** | MVP | source_id | Thông báo xóa thành công | Gỡ Source khỏi Workspace cùng kết quả phân tích của nó. |
| **Tự nhận diện công nghệ** | MVP | workspace_id | Danh sách công nghệ nhận diện được cho từng Source (tên, phiên bản) | Đọc các file đặc trưng (`pom.xml`, `angular.json`…) để chọn bộ phân tích phù hợp. Người dùng sửa tay được nếu nhận diện sai. |
| **Gợi ý tách mã hệ thống cũ** | Mở rộng | workspace_id | Danh sách thư mục nên tách thành Source `LEGACY` | Phát hiện cụm lớn file của công nghệ khác nằm lẫn trong Source Backend (ví dụ hàng nghìn file `.php`) và gợi ý tách riêng. |
| **Xuất Workspace ra file** | Nên có | workspace_id | File JSON cấu hình | Sao lưu hoặc chuyển sang máy khác. File gồm cấu hình Source, luật liên kết, liên kết đã chốt tay, nhóm chức năng. **Không** chứa mã nguồn hay kết quả phân tích. |
| **Nhập Workspace từ file** | Nên có | File JSON cấu hình | Workspace mới | Khôi phục Workspace từ file đã xuất, sau đó quét lại để có kết quả phân tích. |
| **Nạp mã nguồn từ file `.zip`** | Mở rộng | workspace_id, file zip, role | Source mới | Dùng khi mã nguồn không nằm sẵn trên máy chạy tool. |
| **Nạp mã nguồn từ Git** | Mở rộng | workspace_id, git_url, branch, access token (chỉ đọc) | Source mới | Tool tự clone repo về thư mục riêng rồi phân tích. |

**Quy tắc & cách xử lý:**
- **Source lồng nhau:** khi khai báo một Source con nằm trong Source khác (ví dụ mã PHP cũ nằm trong repo Backend), hệ thống tự thêm luật loại trừ thư mục con vào Source cha, để mỗi file chỉ được phân tích một lần.
- **Bộ lọc mặc định:** luôn bỏ qua `node_modules`, `.git`, `dist`, `target`, `build`, `.angular`, `.idea`, `.vscode`, `vendor`, file nhị phân, ảnh và file vượt ngưỡng kích thước.
- **Công nghệ chưa hỗ trợ:** hệ thống báo "chưa hỗ trợ" cho Source đó nhưng vẫn cho xem cây thư mục, không báo lỗi toàn Workspace.
- **Dấu hiệu nhận diện:** `pom.xml`/`build.gradle` có `spring-boot-*` → Spring Boot; `angular.json` và `@angular/core` → Angular; mật độ file `.php` cùng cấu trúc thư mục → PHP hệ thống cũ.

---

## Nhóm 2: Quét & phân tích mã nguồn

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Quét lần đầu** | MVP | workspace_id | Mã lần quét; kết quả phân tích được lưu lại | Đọc toàn bộ file hợp lệ, dựng "bản đồ" các thành phần và quan hệ giữa chúng, nối Frontend với Backend. |
| **Theo dõi tiến độ quét** | MVP | scan_run_id | Tiến độ theo thời gian thực: giai đoạn, số file đã đọc / tổng | Hiển thị thanh tiến độ trong lúc quét. |
| **Quét lại tăng dần** | MVP | workspace_id | Mã lần quét; số file thêm / sửa / xóa | Chỉ phân tích lại những file đã thay đổi kể từ lần quét trước, nhờ so sánh mã băm nội dung. |
| **Hủy lần quét** | MVP | scan_run_id | Lần quét chuyển sang trạng thái đã hủy | Dừng lần quét đang chạy, giữ nguyên kết quả của lần quét thành công trước đó. |
| **Xem lịch sử quét** | MVP | workspace_id | Danh sách lần quét (thời gian, trạng thái, thống kê) | Theo dõi các lần quét đã chạy. |
| **Xem danh sách file lỗi** | MVP | scan_run_id | Danh sách file không phân tích được kèm lý do | File lỗi cú pháp không làm hỏng cả lần quét; lần quét đó có trạng thái `PARTIAL`. |
| **Phân tích Backend Spring Boot** | MVP | *(Tự động khi quét)* | Endpoint, lớp xử lý, truy vấn, bảng dữ liệu | Bộ phân tích cho Java/Spring Boot. |
| **Phân tích Frontend Angular** | MVP | *(Tự động khi quét)* | Ứng dụng, màn hình, phần tử tương tác, lời gọi API | Bộ phân tích cho Angular, bao gồm cả microfrontend. |
| **Phân tích mã PHP hệ thống cũ** | Mở rộng | *(Tự động khi quét Source `LEGACY`)* | Action, lớp xử lý, collection/bảng dữ liệu | Bộ phân tích riêng cho framework PHP tự phát triển của hệ thống cũ. |
| **Tự quét lại khi code thay đổi** | Mở rộng | workspace_id, bật/tắt | Lần quét tự động được kích hoạt | Theo dõi thay đổi file và tự quét lại tăng dần. |
| **Quét bằng dòng lệnh (CLI)** | Mở rộng | Đường dẫn thư mục, file kết quả | File kết quả phân tích (JSON) | Chạy được trong CI để sinh tài liệu kiến trúc tự động. |
| **Hỗ trợ công nghệ bổ sung** | Mở rộng | *(Bộ phân tích mới)* | Kết quả phân tích cho công nghệ mới | ZK, NestJS, Laravel, React, Vue… Mỗi công nghệ là một bộ phân tích độc lập, không sửa phần lõi. |

**Quy tắc & cách xử lý:**
- **Phân tích Spring Boot:**
  - Nhận diện Controller và ghép đường dẫn ở cấp class với cấp method, cộng thêm `context-path` nếu có.
  - Tìm các thành phần được tiêm vào (field `final` + `@RequiredArgsConstructor`, `@Autowired`, tham số constructor) và nối interface với lớp triển khai.
  - Nhận diện truy vấn dữ liệu: method Spring Data suy ra từ tên, `@Query`, câu truy vấn ghép động bằng chuỗi, lời gọi vào thư viện truy vấn dùng chung (theo luật cấu hình). Từ truy vấn suy ra bảng bị **đọc** hay **ghi**.
  - Ghi lại điều kiện bao quanh mỗi lời gọi và các câu lệnh `throw` kèm thông báo lỗi (tra sang tiếng Việt trong file thông báo), để vẽ nhánh lỗi và sinh mục "Ràng buộc".
- **Phân tích Angular:**
  - Đọc cấu hình ứng dụng, route và microfrontend để dựng danh sách màn hình.
  - Đọc template để lấy mọi sự kiện: `(click)`, `(ngSubmit)`, `(change)`, và sự kiện của component con.
  - Lần theo hàm xử lý → facade/service → lời gọi HTTP, kể cả tiêm phụ thuộc qua `InjectionToken`.
  - Lấy nhãn tiếng Việt của nút và màn hình từ file ngôn ngữ.
- **Nguyên tắc an toàn:** chỉ **đọc** file. Không build, không chạy, không cài dependency, không kết nối CSDL của dự án được phân tích.

---

## Nhóm 3: Khám phá cấu trúc dự án

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem cây API Backend** | MVP | workspace_id | Cây `Module → Endpoint` (method, đường dẫn, mô tả ngắn, cảnh báo) | Danh sách API tổ chức theo module giống Swagger. Module lấy theo `@Tag`, sau đó theo Controller, cuối cùng theo package. |
| **Xem cây thành phần Frontend** | MVP | workspace_id | Cây `Ứng dụng → Màn hình → Tab → Hộp thoại/Form → Phần tử` | Mọi phần tử tương tác được trên giao diện, kèm nhãn tiếng Việt và dấu hiệu "có gọi API" hay "chỉ thao tác giao diện". |
| **Tìm kiếm nhanh** | Nên có | workspace_id, keyword, loại thành phần (tùy chọn) | Danh sách kết quả nhóm theo loại | Tìm theo đường dẫn API, nhãn nút, tên màn hình, tên class, tên bảng (phím tắt `Ctrl+K`). |
| **Xem chi tiết thành phần** | MVP | workspace_id, node_key | Thông tin thành phần, danh sách "Được gọi từ" và "Gọi tới" | Biết một endpoint đang được màn hình nào dùng, hoặc một nút đang gọi những gì. |
| **Xem đoạn mã nguồn** | MVP | workspace_id, node_key | Đoạn code (chỉ đọc) kèm đường dẫn file và số dòng | Xem nhanh code gốc của thành phần mà không phải mở IDE. |
| **Đối chiếu với OpenAPI** | Nên có | workspace_id, URL `/v3/api-docs` hoặc file `openapi.json` | Báo cáo endpoint khớp / chỉ có trong code / chỉ có trong Swagger; mô tả được bổ sung | Kiểm tra kết quả phân tích với Swagger của dự án và lấy thêm mô tả API. |

---

## Nhóm 4: Dò vết & trực quan hóa luồng

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Dò vết từ phần tử giao diện** | MVP | workspace_id, node_key của phần tử, mức chi tiết | Chuỗi bước từ Người dùng tới Bảng dữ liệu và chiều trả về | Luồng đầy đủ: bấm nút → hàm xử lý → gọi API → máy chủ xử lý → đọc/ghi dữ liệu → giao diện cập nhật. |
| **Dò vết từ endpoint Backend** | MVP | workspace_id, node_key của endpoint, mức chi tiết | Chuỗi bước từ endpoint tới Bảng dữ liệu, kèm danh sách màn hình "Được gọi từ" | Dành cho dev Backend khi muốn hiểu một API. |
| **Dò vết luồng chỉ có giao diện** | MVP | workspace_id, node_key của phần tử | Chuỗi bước chỉ gồm thành phần Frontend, có nhãn "Không gọi máy chủ" | Dành cho thao tác như mở hộp thoại, đổi tab, xóa form. |
| **Hiển thị sequence diagram** | MVP | Kết quả dò vết | Sequence diagram tương tác trên canvas | Zoom, kéo, di chuột vào mũi tên để xem mức tin cậy; bấm vào thành phần để xem chi tiết ở panel phải. |
| **Đổi mức chi tiết diagram** | MVP | Mức: Tầng / Thành phần / Chi tiết | Diagram vẽ lại theo mức đã chọn | *Tầng:* gộp Người dùng / Frontend / Backend / CSDL. *Thành phần* (mặc định): mỗi class một cột. *Chi tiết:* hiện cả lời gọi nội bộ. |
| **Thu gọn / mở rộng một phần diagram** | MVP | Thành phần hoặc khối cần thu gọn | Diagram cập nhật | Giúp đọc được luồng dài. |
| **Xuất diagram** | Nên có | trace_id, định dạng (SVG / PNG / Mermaid) | File diagram | Dán vào tài liệu nội bộ hoặc wiki. |
| **Xem Flowchart** | Mở rộng | workspace_id, node_key | Flowchart logic rẽ nhánh | Thể hiện các nhánh if/else, vòng lặp, lỗi bên trong logic xử lý; chuyển qua lại với Sequence trên cùng thành phần. |

**Quy tắc & cách xử lý:**
- **Thứ tự các bước** theo đúng thứ tự lời gọi trong code.
- **Nhánh `alt`/`opt`** được vẽ từ điều kiện `if`/`try` và các câu lệnh `throw`. Ví dụ: "Nếu mã đã tồn tại → trả lỗi".
- **Giới hạn:** mặc định dò tối đa 12 bước lồng nhau (cấu hình được), tự phát hiện vòng lặp và gộp lời gọi lặp lại.
- **Bộ lọc nhiễu:** ẩn các lời gọi không mang nghĩa nghiệp vụ (ghi log, chuyển đổi đối tượng, hàm tiện ích, getter/setter).
- **Tất định:** diagram luôn sinh ra từ kết quả phân tích tĩnh. Cùng mã nguồn thì luôn cho cùng diagram. **AI không vẽ diagram.**

---

## Nhóm 5: Liên kết Frontend – Backend & độ tin cậy

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Tự động liên kết Frontend với Backend** | MVP | *(Tự động khi quét)* | Liên kết giữa lời gọi HTTP ở Frontend và endpoint ở Backend, kèm mức tin cậy | Nối được cả khi Frontend và Backend nằm ở hai repo khác nhau. |
| **Hiển thị mức tin cậy** | MVP | *(Theo từng mũi tên)* | Kiểu nét vẽ và lý do khi di chuột | Cho người dùng biết chỗ nào tool chắc chắn, chỗ nào là suy luận, để tự kiểm tra lại khi cần. |
| **Nhờ AI chọn liên kết chưa chắc chắn** | MVP | edge có nhiều ứng viên | Ứng viên được chọn kèm lý do, hoặc "không xác định" | Chỉ dùng khi phân tích tĩnh tìm ra nhiều khả năng ngang nhau. Tự chạy khi luồng đi qua cạnh đó; cũng bấm tay được. |
| **Chốt tay liên kết** | MVP | from_node_key, to_node_key, quyết định (ghim / loại bỏ), ghi chú | Liên kết đã chốt | Quyết định của người dùng luôn được ưu tiên hơn kết quả tự động và được giữ qua các lần quét sau. |
| **Xem & bỏ liên kết đã chốt tay** | MVP | workspace_id / override_id | Danh sách liên kết đã chốt; thông báo xóa | Quản lý các quyết định đã chốt. |
| **Quản lý luật liên kết** | MVP | workspace_id, danh sách luật (tiền tố, rewrite, context-path) | Luật đã lưu; liên kết được tính lại | Xem các luật tool tự phát hiện (base URL, luật proxy, context-path), sửa hoặc thêm luật thủ công. |
| **Đối chiếu với route thực tế khi Backend đang chạy** | Mở rộng | workspace_id, địa chỉ Backend đang chạy | Báo cáo khớp / lệch giữa kết quả tĩnh và route thật | Đọc `/actuator/mappings` và `/v3/api-docs` của Backend đang chạy. |
| **Xác minh luồng bằng dữ liệu theo dõi lúc chạy** | Mở rộng | Dữ liệu theo dõi từ OpenTelemetry agent | Các cạnh được nâng lên "Đã xác minh runtime"; cạnh bị bỏ sót | Người dùng thao tác trên ứng dụng thật, tool đối chiếu với bản đồ tĩnh. Chỉ ghi thông tin kỹ thuật, không lưu dữ liệu nghiệp vụ. |

**Quy tắc & cách xử lý:**
- **Cách liên kết:**
  1. Tính URL thật phía Frontend: ghép hằng số, biến môi trường, template string. Phần không biết trước giá trị được thay bằng ký tự đại diện.
  2. Áp luật tiền tố và luật proxy.
  3. Chuẩn hóa đường dẫn endpoint Backend.
  4. So khớp: method phải trùng; đoạn cố định ưu tiên hơn đoạn biến. Ví dụ `/items/export-excel` thắng `/items/{id}`.
- **Các mức tin cậy:**

| Mức | Ý nghĩa | Cách hiển thị |
| :--- | :--- | :--- |
| `CERTAIN` | Code tham chiếu rõ ràng, duy nhất | Nét liền |
| `INFERRED` | Suy ra bằng luật (URL có phần đại diện, truy vấn ghép động…) | Nét đứt, di chuột để xem lý do |
| `AI_RESOLVED` | Có nhiều ứng viên, AI đã chọn một | Nét đứt khác màu, di chuột để xem lý do AI đưa ra |
| `UNRESOLVED` | Không xác định được | Ô xám "Không xác định", có link tới đoạn code |
| `RUNTIME_VERIFIED` *(Mở rộng)* | Đã xác nhận bằng dữ liệu lúc chạy | Nét liền có dấu ✓ |

- **Giới hạn của AI:** AI **chỉ được chọn trong danh sách ứng viên** mà phân tích tĩnh đã tìm ra, và không được tự tạo thành phần hay liên kết mới. AI không chắc chắn thì giữ `UNRESOLVED`.
- **Thiếu một phía:** chỉ có Frontend thì đích là "API bên ngoài"; chỉ có Backend thì điểm bắt đầu là "Client".

---

## Nhóm 6: Diễn giải & dữ liệu mẫu (panel bên phải)

> Panel phải có 2 tab: **Diễn giải** và **Kịch bản**. Lời văn ngắn gọn, đúng trọng tâm, tránh thuật ngữ nặng. Riêng Đầu vào, Đầu ra và Dữ liệu mẫu trình bày bằng **khối JSON**.

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem mục đích** | MVP | workspace_id, node_key | 1–3 câu mô tả thành phần làm gì, phục vụ nghiệp vụ nào | Lấy lần lượt từ `@Operation` → Javadoc → tài liệu đặc tả trong repo. **AI chỉ viết khi cả ba đều không có.** |
| **Xem thuộc chức năng** | MVP | workspace_id, node_key | Đường dẫn module/màn hình (ví dụ "Quản lý khai báo › Danh mục") | Cho biết thành phần nằm ở đâu trong hệ thống. Khi có nhóm chức năng thì hiện thêm nhóm đã lưu hoặc được gợi ý. |
| **Xem đầu vào (JSON)** | MVP | workspace_id, node_key | Khối JSON mô tả tham số và body: kiểu, bắt buộc hay không, giới hạn | Sinh tất định từ DTO, annotation và chữ ký method. Với phần tử giao diện: các trường người dùng nhập hoặc chọn. |
| **Xem đầu ra (JSON)** | MVP | workspace_id, node_key | Khối JSON cấu trúc kết quả trả về, kèm danh sách ngắn các trường hợp lỗi | Tự bóc lớp bọc chung (ví dụ `ApiResponse<T>`) để hiện dữ liệu thật. Với phần tử giao diện: những gì màn hình thay đổi sau thao tác. |
| **Xem ràng buộc** | MVP | workspace_id, node_key | Gạch đầu dòng quy tắc bằng lời | Validation, quy tắc nghiệp vụ, quyền truy cập, giao dịch. Ví dụ: "Không xóa được danh mục đang được sản phẩm sử dụng". |
| **Sinh dữ liệu mẫu** | MVP | workspace_id, node_key | Khối JSON request mẫu và response mẫu | AI sinh **một lần** từ cấu trúc DTO (chỉ tên trường, kiểu, ràng buộc), kết quả được kiểm tra khớp cấu trúc rồi lưu cache. Lần sau dùng lại bản đã lưu cho tới khi DTO thay đổi. AI tắt hoặc lỗi thì dùng bộ sinh tất định. **Không đọc dữ liệu thật.** |
| **Xem kịch bản từng bước** | MVP | trace_id | Danh sách bước đánh số theo mẫu "A + hành động + tới C", nhánh lỗi là bước con | Mỗi bước khớp một mũi tên trên diagram. |
| **Làm mượt câu kịch bản bằng AI** | MVP | trace_id | Kịch bản với câu văn tự nhiên hơn | AI chỉ viết lại câu, **giữ nguyên số bước, thứ tự và thành phần**. Nếu lệch thì dùng lại câu gốc. Tắt được. |
| **Đồng bộ kịch bản với diagram** | MVP | Bước hoặc mũi tên được chọn | Bước và mũi tên tương ứng cùng được tô sáng | Di chuột hoặc bấm ở một bên thì bên kia cũng sáng lên. |
| **Hiện chi tiết kỹ thuật** | MVP | Bật/tắt | Thêm tên hàm, `METHOD /path`, `file:dòng` vào từng bước | Mặc định tắt để nội dung dễ đọc; bật lên khi dev cần. |
| **Sinh lại nội dung AI** | MVP | workspace_id, node_key hoặc trace_id, loại nội dung | Nội dung AI mới | Bỏ qua bộ nhớ đệm, gọi AI lại khi người dùng thấy nội dung chưa tốt. |

**Quy tắc & cách xử lý:**
- **Khung kịch bản sinh tất định từ kết quả dò vết:**
  - Tên thân thiện lấy từ file ngôn ngữ tiếng Việt (nhãn nút, tiêu đề màn hình).
  - Tên class được chuyển thành tên dễ đọc (ví dụ `CategoryServiceImpl` → "Dịch vụ Danh mục").
  - Hành động theo mẫu của từng loại quan hệ: "bấm", "gửi yêu cầu", "đọc dữ liệu", "ghi dữ liệu", "trả kết quả".
- **Bộ nhớ đệm:** mọi nội dung do AI sinh được lưu lại. Chỉ gọi AI lại khi đoạn code liên quan thay đổi hoặc người dùng bấm "Sinh lại".
- **Khi AI tắt hoặc lỗi:** mục Mục đích hiện nội dung từ annotation/Javadoc hoặc "Chưa có mô tả"; kịch bản dùng câu gốc; dữ liệu mẫu do bộ sinh tất định tạo (theo tên trường và ràng buộc); mọi mục khác vẫn đầy đủ.
- Ví dụ đầy đủ của hai tab: xem **Phụ lục A** trong [overview.md](overview.md).

---

## Nhóm 7: Nhóm chức năng (Feature)

> **Feature** là một nhóm các endpoint/thành phần liên quan, ví dụ "Quản lý danh mục" gồm: tải danh sách, tìm kiếm, thêm, sửa, xóa, xuất Excel. Cột trái có thêm tab **"Chức năng"** bên cạnh "Backend" và "Frontend".

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách nhóm chức năng** | Mở rộng | workspace_id | Danh sách Feature đã lưu | Truy cập nhanh các chức năng hay xem. |
| **Tạo nhóm chức năng** | Mở rộng | workspace_id, name, description, danh sách node_key theo thứ tự | Feature mới | Chọn nhiều thành phần trên cây (chọn nhiều cùng lúc), đặt tên và sắp xếp thứ tự. |
| **Sửa nhóm chức năng** | Mở rộng | feature_id, name, description, danh sách thành viên | Feature đã cập nhật | Thêm/bớt thành viên, đổi thứ tự. |
| **Xóa nhóm chức năng** | Mở rộng | feature_id | Thông báo xóa thành công | Chỉ xóa nhóm, không ảnh hưởng thành phần. |
| **Gợi ý nhóm chức năng tự động** | Mở rộng | workspace_id | Danh sách nhóm được gợi ý | Gom theo màn hình Frontend (các API mà một màn hình gọi), theo module Backend và theo bảng dữ liệu dùng chung. Người dùng xác nhận trước khi lưu. |
| **Xem diagram cấp chức năng** | Mở rộng | feature_id, mức chi tiết | Sequence diagram ghép từ các luồng thành viên | Mỗi luồng thành viên là một khối có tiêu đề; có tùy chọn gộp bước trùng. |
| **Xem diễn giải cấp chức năng** | Mở rộng | feature_id | Mục đích chung, các bước lớn, bảng dữ liệu bị tác động | Mục đích chung do AI tóm tắt từ diễn giải của các thành viên. |
| **Gợi ý "thuộc chức năng nào"** | Mở rộng | workspace_id, node_key | Danh sách Feature chứa hoặc nên chứa thành phần | Khi mở một thành phần đơn lẻ, panel phải hiện các nhóm liên quan để mở nhanh. |

---

## Nhóm 8: So sánh & đối chiếu hệ thống cũ – mới

> Phục vụ dự án chuyển đổi (migration): đặt luồng của hệ thống cũ (PHP) cạnh luồng của hệ thống mới (Angular + Spring Boot) để kiểm tra có sót logic không.

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Mở chế độ so sánh (split view)** | Mở rộng | Hai node_key | Canvas chia 2 khung, mỗi khung có diagram và panel diễn giải riêng | Đổi vị trí hai khung hoặc đóng một khung được. |
| **Gợi ý thành phần để so sánh** | Mở rộng | workspace_id, node_key | Danh sách gợi ý có xếp hạng | Ưu tiên cách tất định: thành phần gọi tới hoặc được gọi bởi thành phần đang mở, cùng nhóm chức năng, cùng bảng dữ liệu, luồng tương ứng ở hệ thống cũ. AI chỉ dùng để xếp hạng khi có quá nhiều ứng viên. |
| **Đánh dấu khác biệt giữa hai diagram** | Mở rộng | Hai trace_id | Danh sách thành phần/bảng chỉ có ở một bên, được tô màu trên diagram | Nhìn nhanh chỗ khác nhau. |
| **Tìm luồng tương ứng ở hệ thống cũ** | Mở rộng | workspace_id, node_key (hệ thống mới) | Danh sách ứng viên ở hệ thống cũ kèm độ phù hợp và lý do | Thứ tự ưu tiên: (1) tài liệu đặc tả ghi rõ file PHP nguồn; (2) độ giống nhau về tên, trường dữ liệu, bảng/collection; (3) AI xác nhận khi hai cách trên chưa đủ. |
| **Tạo checklist đối soát** | Mở rộng | node_key hệ thống cũ, node_key hệ thống mới | Checklist các điểm lệch, mỗi mục trỏ về vị trí code ở cả hai phía | AI tổng hợp từ hai luồng và hai bộ ràng buộc: validation còn thiếu, nhánh lỗi còn thiếu, khác biệt quyền truy cập, khác biệt dữ liệu bị đọc/ghi. |

---

## Nhóm 9: Cấu hình AI & hệ thống

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem cấu hình AI** | MVP | *(Không cần input)* | Provider, model, trạng thái bật/tắt từng mục đích dùng AI | API key chỉ đặt qua biến môi trường của máy chủ, **không bao giờ hiển thị hay nhập trên giao diện**. |
| **Bật/tắt từng mục đích dùng AI** | MVP | Danh sách mục đích và trạng thái bật/tắt | Cấu hình đã lưu | Ví dụ tắt "Làm mượt kịch bản" để tiết kiệm lượt gọi. |
| **Kiểm tra kết nối AI** | MVP | *(Không cần input)* | Kết quả thành công/thất bại, độ trễ | Gửi một yêu cầu thử tới provider đang cấu hình. |
| **Quản lý bộ lọc nhiễu** | MVP | workspace_id, danh sách class/package cần ẩn | Bộ lọc đã lưu | Chỉnh danh sách lời gọi bị ẩn khỏi diagram (có sẵn giá trị mặc định hợp lý). |
| **Xem thống kê sử dụng AI** | Nên có | Khoảng thời gian | Số lượt gọi, số token, tỷ lệ lỗi theo mục đích | Theo dõi chi phí và hạn mức. Nhật ký không lưu nội dung prompt. |

**Quy tắc & cách xử lý:**
- **Provider hỗ trợ:** `gemini` (mặc định), `anthropic`, `openai-compatible`, `none` (tắt AI).
- **Mục đích được phép dùng AI:** xem §2.7 trong [overview.md](overview.md). Mọi mục đích mới phải được bổ sung vào đó trước khi triển khai.
- **Trước mỗi lần gọi AI:** che các giá trị bí mật (mật khẩu, token, khóa, chuỗi kết nối CSDL) và chỉ gửi đoạn code liên quan.
