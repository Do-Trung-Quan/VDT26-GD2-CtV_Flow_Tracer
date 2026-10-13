# BẢNG TỔNG HỢP CHỨC NĂNG HỆ THỐNG — CtV FLOW TRACER

> **Ghi chú:**
> - Tool dùng **cá nhân**, không có đăng nhập hay phân quyền. Vì vậy cột "Phân quyền" được thay bằng cột **Ưu tiên**:
>   - **MVP**: bắt buộc có trong bản MVP.
>   - **Nên có**: làm trong MVP nếu kịp thời gian.
>   - **Mở rộng**: làm sau khi hoàn thành MVP.
> - Chức năng được **chia nhóm theo mục đích sử dụng**. Mỗi nhóm có bảng chức năng, kèm phần **Quy tắc & cách xử lý** để người mới hiểu hệ thống làm gì bên dưới.
> - Tổng số: 72 chức năng chia làm 9 nhóm (46 MVP, 4 Nên có, 22 Mở rộng).
> - API tương ứng với từng nhóm: xem [API_list.md](API_list.md).
> - **Lộ trình hiện tại** ([roadmap.md](roadmap.md)): trong phần Mở rộng chỉ làm **phân tích mã PHP hệ thống cũ** và **Nhóm 8 (So sánh & đối chiếu cũ – mới)**. Các chức năng Mở rộng còn lại để sau, chưa có kế hoạch.

---

## Nhóm 1: Quản lý Workspace & nguồn mã

> **Workspace** là một "dự án" trong tool, gồm nhiều **Source**. Mỗi Source là một thư mục mã nguồn có vai trò: `BACKEND`, `FRONTEND` hoặc `LEGACY` (hệ thống cũ). Vai trò do tool **tự nhận diện**, người dùng chỉ chọn thư mục.

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách Workspace** | MVP | *(Không cần input)* | Danh sách Workspace kèm số Source, thời điểm quét gần nhất, trạng thái quét | Màn hình khởi đầu; cho phép mở lại nhanh Workspace đã dùng gần nhất. |
| **Tạo Workspace** | MVP | name, description, danh sách thư mục đã chọn | Workspace mới | Tạo một không gian phân tích mới cho một dự án (giao diện: "Import dự án", 3 bước: thông tin → chọn thư mục → xác nhận). |
| **Sửa thông tin Workspace** | MVP | workspace_id, name, description | Workspace đã cập nhật | Đổi tên, mô tả Workspace. |
| **Xóa Workspace** | MVP | workspace_id | Thông báo xóa thành công | Xóa toàn bộ kết quả phân tích và dữ liệu người dùng của Workspace. **Không bao giờ đụng tới mã nguồn.** |
| **Duyệt thư mục trên máy** | MVP | path (thư mục hiện tại) | Danh sách thư mục con | Giúp chọn thư mục mã nguồn ngay trên giao diện, không cần gõ đường dẫn. Chỉ duyệt được bên trong thư mục gốc đã gắn vào tool (`SOURCE_ROOT`, chỉ đọc). Lý do: Engine chạy trong container nên chỉ thấy thư mục được gắn vào, và trình duyệt không cho web biết đường dẫn thật của thư mục chọn qua hộp thoại hệ điều hành. Nên gắn `SOURCE_ROOT` rộng (vd cả ổ chứa code) để chọn được mọi dự án. |
| **Thêm Source** | MVP | workspace_id, root_path, name (tùy chọn), thư mục loại trừ (tùy chọn) | Một hoặc nhiều Source mới | Khai báo một thư mục mã nguồn. Vai trò và công nghệ do tool tự nhận diện; thư mục có nhiều gốc dự án được tách thành nhiều Source. |
| **Sửa Source** | MVP | source_id, name, thư mục loại trừ | Source đã cập nhật | Đổi tên hoặc danh sách thư mục loại trừ (vd code sinh tự động, dữ liệu test). |
| **Xóa Source** | MVP | source_id | Thông báo xóa thành công | Gỡ Source khỏi Workspace cùng kết quả phân tích của nó. |
| **Tự nhận diện công nghệ** | MVP | workspace_id hoặc root_path | Danh sách gốc dự án tìm thấy: đường dẫn, vai trò, công nghệ, phiên bản | Đọc các file đặc trưng (`pom.xml`, `angular.json`…) để xác định vai trò và chọn bộ phân tích phù hợp. |
| **Gợi ý tách mã hệ thống cũ** | Mở rộng | workspace_id | Danh sách thư mục nên tách thành Source `LEGACY` | Phát hiện cụm lớn file của công nghệ khác nằm lẫn trong Source Backend (ví dụ hàng nghìn file `.php`) và gợi ý tách riêng. |
| **Nạp mã nguồn từ file `.zip`** | Mở rộng | workspace_id, file zip, role | Source mới | Dùng khi mã nguồn không nằm sẵn trên máy chạy tool. |
| **Nạp mã nguồn từ Git** | Mở rộng | workspace_id, git_url, branch, access token (chỉ đọc) | Source mới | Tool tự clone repo về thư mục riêng rồi phân tích. |

**Quy tắc & cách xử lý:**
- **Source lồng nhau:** khi khai báo một Source con nằm trong Source khác (ví dụ mã PHP cũ nằm trong repo Backend), hệ thống tự thêm luật loại trừ thư mục con vào Source cha, để mỗi file chỉ được phân tích một lần.
- **Bộ lọc mặc định:** luôn bỏ qua `node_modules`, `.git`, `dist`, `target`, `build`, `.angular`, `.idea`, `.vscode`, `vendor`, thư mục `docs/` ở gốc Source, file nhị phân, ảnh và file vượt ngưỡng kích thước.
- **Công nghệ chưa hỗ trợ:** hệ thống báo "chưa hỗ trợ" cho Source đó nhưng vẫn cho xem cây thư mục, không báo lỗi toàn Workspace.
- **Dấu hiệu nhận diện (gốc dự án):** `pom.xml`/`build.gradle` có `spring-boot-*` → Spring Boot (vai trò `BACKEND`; dự án Maven nhiều module lấy `pom.xml` gốc); `angular.json` và `@angular/core` → Angular (vai trò `FRONTEND`); mật độ file `.php` cùng cấu trúc thư mục → PHP hệ thống cũ (*mở rộng*).
- **Xử lý thư mục người dùng chọn:** tool quét tối đa 4 tầng thư mục con (bỏ qua thư mục trong bộ lọc mặc định) để tìm các gốc dự án.
  - *Không có gốc nào* (thư mục không liên quan): báo "Không tìm thấy dự án Spring Boot hoặc Angular", không cho thêm.
  - *Thư mục nằm bên trong một dự án* (vd `src/main/java/...`): dò ngược lên thư mục cha gần nhất có dấu hiệu và gợi ý dùng thư mục đó. Không phân tích lẻ thư mục con, vì sẽ thiếu cấu hình (context-path, proxy…).
  - *Nhiều gốc dự án* (BE và FE chung repo, vd `engine/` + `web/`): tự tách thành nhiều Source. Thư mục không thuộc công nghệ nào được bỏ qua.
  - *Mã cũ (PHP) nằm trong thư mục tài liệu của BE:* bộ phân tích Java chỉ đọc `.java` nên không bị ảnh hưởng; `docs/` ở gốc Source nằm trong bộ lọc mặc định. Ở bản mở rộng, tool báo "phát hiện mã PHP" để người dùng bật thành Source `LEGACY`.

---

## Nhóm 2: Quét & phân tích mã nguồn

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Quét lần đầu** | MVP | workspace_id | Mã lần quét; kết quả phân tích được lưu lại | Đọc toàn bộ file hợp lệ, dựng "bản đồ" các thành phần và quan hệ giữa chúng, nối Frontend với Backend. |
| **Theo dõi tiến độ quét** | MVP | scan_run_id | Tiến độ theo thời gian thực: giai đoạn, số file đã đọc / tổng | Hiển thị thanh tiến độ trong lúc quét. |
| **Quét lại tăng dần** | MVP | workspace_id | Mã lần quét; số file thêm / sửa / xóa | Chỉ phân tích lại những file đã thay đổi kể từ lần quét trước, nhờ so sánh mã băm nội dung. |
| **Hủy lần quét** | MVP | scan_run_id | Lần quét chuyển sang trạng thái đã hủy | Dừng lần quét đang chạy, giữ nguyên kết quả của lần quét thành công trước đó. |
| **Xem lịch sử quét** | MVP | workspace_id | Danh sách lần quét (thời gian, trạng thái, thống kê) | Theo dõi các lần quét đã chạy. |
| **Xem danh sách file lỗi** | MVP | scan_run_id | Danh sách file không phân tích được kèm lý do | File lỗi không làm hỏng cả lần quét; lần quét đó có trạng thái `PARTIAL`. |
| **Phân tích Backend Spring Boot** | MVP | *(Tự động khi quét)* | Endpoint, lớp xử lý, truy vấn, bảng dữ liệu, lời gọi HTTP ra ngoài | Bộ phân tích cho Java/Spring Boot, gồm cả lời gọi sang service khác hoặc hệ thống bên ngoài. |
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
  - Ghi lại các đoạn kiểm tra trong thân method (`if … throw`, lời gọi kiểm tra quyền, hàm private) để dựng **bước tự xử lý** trên diagram.
  - Nhận diện lời gọi HTTP ra ngoài: `@FeignClient`, `RestTemplate`, `WebClient`. Đích nằm trong Workspace (microservice khác) thì nối tới endpoint đó; không thì là "Hệ thống bên ngoài".
  - Hiểu code Lombok (`@RequiredArgsConstructor`, `@Getter`…) mà không cần chạy trình sinh code.
- **Phân tích Angular:**
  - Đọc cấu hình ứng dụng, route và microfrontend để dựng danh sách màn hình.
  - Đọc template để lấy mọi sự kiện: `(click)`, `(ngSubmit)`, `(change)`, và sự kiện của component con.
  - Lần theo hàm xử lý → facade/service → lời gọi HTTP, kể cả tiêm phụ thuộc qua `InjectionToken`.
  - Lấy nhãn tiếng Việt của nút và màn hình từ file ngôn ngữ.
- **File lỗi:** một file bị coi là lỗi khi tool không phân tích được nó: sai cú pháp, cú pháp mới mà bộ đọc chưa hỗ trợ, sai bảng mã ký tự, vượt kích thước hoặc thời gian cho phép, không đọc được, hoặc bộ phân tích bị lỗi ở file đó. Không giải được kiểu dữ liệu (thiếu thư viện ngoài) **không** tính là file lỗi; khi đó liên kết liên quan chỉ hạ xuống "Suy luận" hoặc "Chưa xác định".
  - Ảnh hưởng: các thành phần trong file lỗi không có trên cây; luồng đi qua file đó bị đứt và hiện bước "Chưa xác định" với lý do "file lỗi phân tích". Các phần khác không bị ảnh hưởng.
- **Nguyên tắc an toàn:** chỉ **đọc** file. Không build, không chạy, không cài dependency, không kết nối CSDL của dự án được phân tích.

---

## Nhóm 3: Khám phá cấu trúc dự án

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem cây API Backend** | MVP | workspace_id | Cây `Module → Endpoint` (method, đường dẫn, mô tả ngắn, cảnh báo) | Danh sách API tổ chức theo module giống Swagger. Module lấy theo `@Tag`, sau đó theo Controller, cuối cùng theo package. |
| **Xem cây thành phần Frontend** | MVP | workspace_id | Cây `Ứng dụng → Màn hình → Tab → Hộp thoại/Form → Phần tử` | Phần tử tương tác kèm nhãn tiếng Việt và dấu hiệu "có gọi API" hay "chỉ thao tác giao diện". **Mặc định chỉ hiện phần tử gọi API** và nút mở hộp thoại / chuyển màn hình có gọi API (cùng ứng dụng, mọi màn hình, và hộp thoại / form / tab có phần tử gọi API hoặc tự gọi API khi mở); công tắc "Hiện cả phần tử chỉ thao tác giao diện" hiện đủ, và khi gõ lọc hoặc dùng Ctrl+K thì tìm trong mọi phần tử. Phần tử chỉ giao diện vẫn có trong dữ liệu để dò vết, tìm kiếm và diễn giải. |
| **Tìm kiếm nhanh** | Nên có | workspace_id, keyword, loại thành phần (tùy chọn) | Danh sách kết quả nhóm theo loại | Tìm theo đường dẫn API, nhãn nút, tên màn hình, tên class, tên bảng (phím tắt `Ctrl+K`). |
| **Xem chi tiết thành phần** | MVP | workspace_id, node_key | Thông tin thành phần, danh sách "Được gọi từ" và "Gọi tới" | Với API: thành phần UI hoặc API khác gọi tới nó, và những gì nó gọi (service ngoài, bảng dữ liệu). Với thành phần UI: chuỗi thành phần chứa nó (từ cao nhất xuống), và API/hộp thoại nó gọi tới. |
| **Xem đoạn mã nguồn** | MVP | workspace_id, node_key | Các đoạn code (chỉ đọc) theo từng lớp trong luồng, kèm đường dẫn file và số dòng | Popup có tab: API gồm Controller / Service / Repository / Entity; thành phần UI gồm template / component / facade / kết nối API. Xem nhanh mà không phải mở IDE. |
| **Đối chiếu với OpenAPI** | Nên có | workspace_id, URL `/v3/api-docs` (tùy chọn) | Báo cáo endpoint khớp / chỉ có trong code / chỉ có trong Swagger | Tool tự tìm file đặc tả có sẵn trong repo (`openapi.yaml/json`, `swagger.*`); URL chỉ dùng được khi Backend đang chạy ở đâu đó. Nội dung không phải OpenAPI 3 / Swagger 2 thì bị từ chối kèm thông báo. Không có chức năng tải file lên. |

---

## Nhóm 4: Dò vết & trực quan hóa luồng

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Dò vết từ phần tử giao diện** | MVP | workspace_id, node_key của phần tử | Chuỗi bước từ Người dùng tới Bảng dữ liệu và chiều trả về | Luồng đầy đủ: bấm nút → hàm xử lý → gọi API → máy chủ xử lý → đọc/ghi dữ liệu → giao diện cập nhật. |
| **Dò vết từ endpoint Backend** | MVP | workspace_id, node_key của endpoint | Chuỗi bước từ endpoint tới Bảng dữ liệu, kèm danh sách màn hình "Được gọi từ" | Dành cho dev Backend khi muốn hiểu một API. |
| **Dò vết luồng chỉ có giao diện** | MVP | workspace_id, node_key của phần tử | Chuỗi bước chỉ gồm User và Frontend, có nhãn "Không gọi máy chủ" | Dành cho thao tác như mở hộp thoại, đổi tab, xóa form. |
| **Hiển thị sequence diagram** | MVP | Kết quả dò vết | Sequence diagram tương tác trên canvas, phong cách PlantUML | Một mức hiển thị duy nhất. Zoom, kéo, di chuột vào mũi tên để xem mức tin cậy; bấm mũi tên để tô sáng bước tương ứng ở tab Kịch bản. |
| **Thu gọn / mở rộng một phần diagram** | MVP | Thành phần hoặc khối cần thu gọn | Diagram cập nhật | Giúp đọc được luồng dài. |
| **Xuất diagram** | Nên có | trace_id, định dạng (PlantUML / SVG / PNG) | Mã PlantUML hoặc file ảnh | Dán vào tài liệu nội bộ hoặc wiki. |
| **Xem Flowchart** | Mở rộng | workspace_id, node_key | Flowchart logic rẽ nhánh | Thể hiện các nhánh if/else, vòng lặp, lỗi bên trong logic xử lý; chuyển qua lại với Sequence trên cùng thành phần. |

**Quy tắc & cách xử lý:**
- **Cột (participant):** User · Frontend (gộp toàn bộ FE) · một cột cho **mỗi** Controller · một cột cho **mỗi** Service · một cột cho mỗi Repository · Database · hệ thống/service bên ngoài. Nhãn cột = vai trò + tên nghiệp vụ (vd "Service Danh mục"); `ServiceImpl` gộp chung cột với interface của nó. Chi tiết: §2.2.4 trong [overview.md](overview.md).
- **Nhãn mũi tên** ngắn, mang tính kỹ thuật, sinh tất định: tên API, tên hàm, thao tác dữ liệu (vd `POST /api/v1/categories`, `existsByCode(code)`, `INSERT category`). Câu mô tả đầy đủ nằm ở tab Kịch bản.
- **Bước tự xử lý:** các đoạn kiểm tra trong một method được gom thành một mũi tên tự quay về chính cột đó.
- **Cách vẽ:** đánh số từng bước, thanh kích hoạt, chiều trả về nét đứt, User vẽ hình người, Database vẽ hình trụ.
- **Thứ tự các bước** theo đúng thứ tự lời gọi trong code.
- **Nhánh `alt`/`opt`** được vẽ từ điều kiện `if`/`try` và các câu lệnh `throw`. Ví dụ: "Nếu mã đã tồn tại → trả lỗi".
- **Giới hạn:** mặc định dò tối đa 12 bước lồng nhau (cấu hình được), tự phát hiện vòng lặp và gộp lời gọi lặp lại.
- **Bộ lọc nhiễu:** ẩn các lời gọi không mang nghĩa nghiệp vụ (ghi log, chuyển đổi đối tượng, hàm tiện ích, getter/setter).
- **Tất định:** diagram luôn sinh ra từ kết quả phân tích tĩnh. Cùng mã nguồn thì luôn cho cùng diagram. **AI không vẽ diagram.**

---

## Nhóm 5: Liên kết Frontend – Backend & độ tin cậy

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Tự động liên kết Frontend với Backend** | MVP | *(Tự động khi quét)* | Liên kết giữa lời gọi HTTP và endpoint, kèm mức tin cậy | Nối FE → BE, kể cả khi hai bên nằm ở hai repo khác nhau; nối BE → BE khi Workspace có nhiều microservice; lời gọi tới địa chỉ không thuộc Workspace thành "Hệ thống bên ngoài". |
| **Hiển thị mức tin cậy** | MVP | *(Theo từng mũi tên)* | Kiểu nét vẽ và lý do khi di chuột | Cho người dùng biết chỗ nào tool chắc chắn, chỗ nào là suy luận, để tự kiểm tra lại khi cần. |
| **Nhờ AI phân tích liên kết chưa chắc chắn** | MVP | edge chưa chắc chắn | Lời giải thích cho từng ứng viên; có thể kèm **một** ứng viên AI đề xuất thêm | Chỉ chạy khi người dùng bấm "Nhờ AI phân tích". AI **không đổi liên kết**; người dùng đọc giải thích rồi tự chốt tay. |
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

| Mức | Khi nào | Cách hiển thị |
| :--- | :--- | :--- |
| `CERTAIN` (Chắc chắn) | Đích xác định duy nhất bằng kiểu dữ liệu: lời gọi được giải ra đúng một method; interface chỉ có một lớp hiện thực; URL là chuỗi hằng khớp đúng một endpoint (đường dẫn và phương thức) | Nét liền |
| `INFERRED` (Suy luận) | Phải dùng luật hoặc heuristic: URL ghép từ biến/hằng số, khớp tham số `{id}`, áp luật proxy/tiền tố, interface nhiều lớp hiện thực nhưng chọn được nhờ `@Primary` / `@Qualifier` | Nét đứt, di chuột để xem lý do |
| `UNRESOLVED` (Chưa xác định) | Không có ứng viên nào, hoặc nhiều ứng viên có mức khớp gần nhau | Nét chấm đỏ, huy hiệu "?"; bấm để xem ứng viên |
| Chốt tay | Người dùng đã chọn đích (lưu ở `link_override`, không phải một giá trị của `confidence`) | Nét liền màu xanh lá |
| `RUNTIME_VERIFIED` *(Mở rộng)* | Đã xác nhận bằng dữ liệu lúc chạy | Nét liền có dấu ✓ |

- **Mức khớp của ứng viên:** điểm tất định cộng từ các đặc trưng có trọng số: khớp từng đoạn đường dẫn, khớp phương thức HTTP, số tham số, luật đã dùng, `@Primary` / `@Qualifier`, khoảng cách package. Điểm này **không phải xác suất đúng**, nên giao diện hiện 3 mức **Cao / Trung bình / Thấp** thay vì con số %. Trọng số được hiệu chỉnh bằng script đo độ chính xác.
- **Nhờ AI phân tích:** AI giải thích vì sao nên hoặc không nên chọn từng ứng viên, dẫn chứng từ code. AI được đề xuất thêm **một** ứng viên ngoài danh sách; ứng viên này chỉ hiển thị (nhãn "AI đề xuất") khi là thành phần **có thật** trong kết quả quét và **hợp lệ về kiểu** (lớp đúng là hiện thực của interface, hoặc endpoint khớp phương thức HTTP). AI **không bao giờ** tự đổi liên kết hay tạo thành phần mới; chỉ người dùng chốt tay mới đổi diagram.
- **Thiếu một phía:** chỉ có Frontend thì đích là "API bên ngoài"; chỉ có Backend thì điểm bắt đầu là "Client".
- **Luật liên kết:** `PREFIX` (thay tiền tố URL phía Frontend, kể cả phần không tính được như `{apiBase}`), `REWRITE` (biểu thức chính quy, như `pathRewrite` của proxy — tự phát hiện từ `proxy.conf.json`), `CONTEXT_PATH` (tiền tố của Backend — tự phát hiện từ cấu hình Spring, đã cộng sẵn vào đường dẫn API; luật thủ công áp cho API chưa có context-path). Luật không áp nối tiếp mà thử từng phương án (gốc, một luật, luật thủ công + luật tự phát hiện); phương án ít luật hơn được ưu tiên. Chi tiết thuật toán và điểm: §2.2.5 của overview.
- **Gọi giữa các Backend:** `RestTemplate`, `WebClient` / `RestClient`, `@FeignClient` thành lời gọi HTTP phía Backend; khớp API trong Workspace thì nối Backend → Backend, không khớp thì thành hệ thống ngoài theo host / tên service.

---

## Nhóm 6: Diễn giải & dữ liệu mẫu (panel bên phải)

> Panel phải có 2 tab: **Diễn giải** và **Kịch bản**. Lời văn ngắn gọn, đúng trọng tâm, tránh thuật ngữ nặng. Riêng Đầu vào, Đầu ra và Dữ liệu mẫu trình bày bằng **khối JSON**.

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem mô tả thành phần chứa** | MVP | workspace_id, node_key (module, ứng dụng, màn hình, hộp thoại, form) | Tên, loại và một đoạn giải thích ngắn | Thành phần chứa chỉ hiện chừng ấy; không hiện đầu vào/đầu ra, ràng buộc hay dữ liệu mẫu. |
| **Xem mục đích** | MVP | workspace_id, node_key (API, nút, ô nhập…) | Đoạn 80–100 từ, dễ hiểu: thành phần này dùng để làm gì, người dùng nhận được gì, khi nào lỗi | AI viết (có cache), đầu vào gồm `@Operation`, Javadoc, tài liệu đặc tả và code. AI lỗi/tắt và chưa có cache thì hiện `@Operation` → Javadoc → tài liệu, cuối cùng là câu khung tất định. |
| **Xem đầu vào (JSON)** | MVP | workspace_id, node_key | Khối JSON mô tả tham số và body: kiểu, bắt buộc hay không, giới hạn | Sinh tất định từ DTO, annotation và chữ ký method. Với thành phần UI: lấy theo API mà nó gọi (gọi nhiều API thì mỗi API một tab). Luồng chỉ trên giao diện: "Không có". |
| **Xem đầu ra (JSON)** | MVP | workspace_id, node_key | Khối JSON cấu trúc kết quả trả về, kèm danh sách ngắn các trường hợp lỗi | Tự bóc lớp bọc chung (vd `ApiResponse<T>`) để hiện dữ liệu thật. Thành phần UI lấy theo API mà nó gọi; luồng chỉ trên giao diện: "Không có". |
| **Xem ràng buộc** | MVP | workspace_id, node_key | Gạch đầu dòng ngắn gọn, cụ thể | Chỉ những điều quan trọng, ghi rõ ràng: bắt buộc đăng nhập/quyền nào; cấm xóa bản ghi đang được tham chiếu; trường bắt buộc, độ dài, định dạng; không được trùng. Thành phần UI lấy theo API mà nó gọi. |
| **Sinh dữ liệu mẫu** | MVP | workspace_id, node_key | Khối JSON request mẫu và response mẫu, khớp đúng Đầu vào / Đầu ra | AI sinh **một lần** từ cấu trúc DTO (chỉ tên trường, kiểu, ràng buộc), kết quả được kiểm tra khớp cấu trúc rồi lưu cache. Lần sau dùng lại bản đã lưu cho tới khi DTO thay đổi. AI tắt hoặc lỗi thì dùng bộ sinh tất định. **Không đọc dữ liệu thật.** |
| **Xem kịch bản từng bước** | MVP | trace_id | Mục "Các actor tham gia" và danh sách "Bước n: …" | Bước n khớp đúng mũi tên số n trên diagram. Nhánh `alt` ghi rõ điều kiện ("Nếu mã đã tồn tại:") và các bước của nhánh. |
| **Viết lời kịch bản bằng AI** | MVP | trace_id | Lời kể tự nhiên cho từng bước | AI viết từ khung câu tất định, **giữ nguyên số bước, thứ tự và thành phần**. Nếu lệch thì dùng khung câu. Tắt được. |
| **Đồng bộ kịch bản với diagram** | MVP | Bước hoặc mũi tên được chọn | Bước và mũi tên tương ứng cùng được tô sáng | Di chuột hoặc bấm ở một bên thì bên kia cũng sáng lên. |
| **Sinh lại nội dung AI** | MVP | workspace_id, node_key hoặc trace_id, loại nội dung | Nội dung AI mới | Bỏ qua bộ nhớ đệm, gọi AI lại khi người dùng thấy nội dung chưa tốt. |

**Quy tắc & cách xử lý:**
- **Ràng buộc** gồm 2 lớp:
  - *Ràng buộc thô (tất định):* Bean Validation (`@NotNull`, `@Size`, `@Pattern`…); yêu cầu đăng nhập/quyền (`@PreAuthorize`, cấu hình Security); các nhánh `if … throw` kèm thông báo lỗi; unique / khóa ngoại trong Entity hoặc migration; kiểm tra "đang được tham chiếu" trước khi xóa.
  - *Câu hiển thị:* AI viết gọn danh sách thô (cùng lần gọi với Mục đích). Mỗi câu phải trỏ về một ràng buộc thô có thật, không đạt thì bỏ. AI tắt thì hiện danh sách thô.
- **Khung kịch bản sinh tất định từ kết quả dò vết:**
  - Tên actor lấy theo nhãn cột trên diagram (vd "Controller Danh mục", "Service Danh mục").
  - Mỗi bước theo mẫu "Bước n: [actor nguồn] [hành động] [tới actor đích]"; hành động theo loại quan hệ: "bấm", "gửi yêu cầu … đến", "chuyển tiếp … đến", "truy vấn … trên", "trả về … cho", "tự kiểm tra …".
- **Bộ nhớ đệm:** mọi nội dung do AI sinh được lưu lại. Chỉ gọi AI lại khi đoạn code liên quan thay đổi hoặc người dùng bấm "Sinh lại".
- **Khi AI tắt hoặc lỗi:** Mục đích hiện mô tả từ annotation/Javadoc hoặc câu khung; ràng buộc hiện danh sách thô; kịch bản dùng khung câu; dữ liệu mẫu do bộ sinh tất định tạo; mọi mục khác vẫn đầy đủ.
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
| **Xem diagram cấp chức năng** | Mở rộng | feature_id | Sequence diagram ghép từ các luồng thành viên | Mỗi luồng thành viên là một khối có tiêu đề; có tùy chọn gộp bước trùng. |
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

---

## Nhóm 9: Cấu hình AI & hệ thống

| Tên chức năng | Ưu tiên | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem cấu hình AI** | MVP | *(Không cần input)* | Provider, model, trạng thái bật/tắt từng mục đích dùng AI | API key chỉ đặt qua biến môi trường của máy chủ, **không bao giờ hiển thị hay nhập trên giao diện**. |
| **Bật/tắt từng mục đích dùng AI** | MVP | Danh sách mục đích và trạng thái bật/tắt | Cấu hình đã lưu | Ví dụ tắt "Làm mượt kịch bản" để tiết kiệm lượt gọi. |
| **Kiểm tra kết nối AI** | MVP | *(Không cần input)* | Kết quả thành công/thất bại, độ trễ | Gửi một yêu cầu thử tới provider đang cấu hình. |
| **Quản lý bộ lọc nhiễu** | MVP | workspace_id, danh sách class/package cần ẩn | Bộ lọc đã lưu | Chỉnh danh sách lời gọi bị ẩn khỏi diagram (có sẵn giá trị mặc định hợp lý). |
| **Xem thống kê sử dụng AI** | Nên có | Khoảng thời gian | Số lượt gọi, số token, tỷ lệ trúng cache, số lỗi, thời gian phản hồi; chia theo mục đích | Số liệu thật: lượt gọi, token, lỗi lấy từ `llm_call_log` (token do nhà cung cấp trả về); lượt trúng cache lấy từ bộ đếm của `ai_artifact`. Kèm thanh "token hôm nay / hạn mức ngày". Nhật ký không lưu nội dung prompt. |

**Quy tắc & cách xử lý:**
- **Provider hỗ trợ:** `gemini` (mặc định), `anthropic`, `openai-compatible`, `none` (tắt AI).
- **Mục đích được phép dùng AI:** xem §2.7 trong [overview.md](overview.md). Mọi mục đích mới phải được bổ sung vào đó trước khi triển khai.
- **Trước mỗi lần gọi AI:** che các giá trị bí mật (mật khẩu, token, khóa, chuỗi kết nối CSDL) và chỉ gửi đoạn code liên quan.
