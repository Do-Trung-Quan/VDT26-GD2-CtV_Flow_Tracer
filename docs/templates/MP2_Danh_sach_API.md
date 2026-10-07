# DANH SÁCH HỆ THỐNG API CHI TIẾT — PHÂN HỆ MP2

> **Nguyên tắc phân quyền API:**
> - Các API thuộc **Nhóm C, D, E** mặc định yêu cầu kiểm tra Guard quyền **Admin**.
> - Các API thuộc nhóm còn lại áp dụng cho cả **User và Admin** (Hệ thống áp dụng cơ chế kế thừa quyền hạn).

---

## Nhóm A: Xác thực & Quản lý thông tin cá nhân

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Đăng nhập** | `POST /auth/login` | email, password | access_token (JWT), refresh_token, hồ sơ user + role | Xác thực tài khoản, cấp JWT chứa payload phân quyền. |
| 2 | **Làm mới token** | `POST /auth/refresh` | refresh_token | access_token mới (+ refresh_token xoay vòng) | Cơ chế Token Rotation, duy trì phiên đăng nhập bảo mật. |
| 3 | **Đăng xuất** | `POST /auth/logout` | Bearer access_token, refresh_token | 204 No Content | Thu hồi (Revoke) hoàn toàn refresh_token trong cơ sở dữ liệu. |
| 4 | **Gửi OTP quên mật khẩu** | `POST /auth/forgot-password` | email | 200 OK + thông báo gửi | Sinh mã OTP ngẫu nhiên, lưu kèm thời hạn TTL và gửi email. |
| 5 | **Xác thực OTP & đặt mật khẩu** | `POST /auth/reset-password` | email, otp_code, new_password | 200 OK + thông báo thành công | Kiểm tra OTP hợp lệ và còn hạn, tiến hành ghi mật khẩu băm mới. |
| 6 | **Đổi mật khẩu** | `PATCH /users/me/password` | old_password, new_password | 200 OK + thông báo thành công | Xác thực mật khẩu cũ của tài khoản, cập nhật mật khẩu mới. |
| 7 | **Lấy hồ sơ cá nhân** | `GET /users/me` | Bearer token *(Không body)* | Thông tin chi tiết user hồ sơ | Trích xuất thông tin định danh của tài khoản đang đăng nhập. |
| 8 | **Cập nhật avatar** | `PATCH /users/me/avatar` | avatar (file ảnh, multipart) | Thông tin user đã cập nhật avatar_url | Tải ảnh lên Object Storage, cập nhật đường dẫn avatar mới. |

---

## Nhóm B: Quản lý & Tra cứu cuộc họp (Theo phòng ban)

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách cuộc họp** | `GET /meetings` | page, limit, status, from_date, to_date | Danh sách Meeting thuộc phòng ban (phân trang) | Hiển thị các cuộc họp nội bộ phòng ban. Thứ tự sắp xếp mặc định: `LIVE` $\rightarrow$ `PROCESSING` $\rightarrow$ `COMPLETED`, sắp xếp giảm dần theo thời gian tạo `created_at`. |
| 2 | **Tìm kiếm cuộc họp theo tên** | `GET /meetings/search` | keyword, page, limit | Danh sách Meeting khớp từ khóa | Tìm kiếm nhanh cuộc họp theo trường tiêu đề trong đơn vị. |
| 3 | **Lấy chi tiết cuộc họp** | `GET /meetings/:id` | meeting_id (param) | Thông tin Meeting tổng quan + audio_url | Lấy metadata cuộc họp và link file ghi âm gốc; `audio_url` hỗ trợ streaming tua bài qua HTTP Range Request. |
| 4 | **Lấy transcript cuộc họp** | `GET /meetings/:id/transcript` | meeting_id (param) | Danh sách TranscriptBlock mảng | Lấy toàn bộ các khối hội thoại biên bản phục vụ việc render hiển thị đồng bộ theo mốc thời gian của audio player. |
| 5 | **Xóa mềm cuộc họp** | `DELETE /meetings/:id` | meeting_id (param) | 200 OK + thông báo thành công | Cập nhật trường dữ liệu `deleted_at` của bản ghi. User thường chỉ được xóa cuộc họp do mình tạo. |

---

## Nhóm C: Quản trị hệ thống cuộc họp (Đặc quyền Admin)

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách tổng thể** | `GET /admin/meetings` | page, limit, department_id, status, from_date, to_date | Toàn bộ danh sách Meeting của toàn hệ thống | Giám sát xuyên suốt toàn bộ hoạt động họp hành của các phòng ban. |
| 2 | **Cập nhật thông tin cuộc họp** | `PATCH /admin/meetings/:id` | param: id; body: title, description, department_id | Meeting bản ghi đã cập nhật | Điều chỉnh dữ liệu thông tin cơ bản khi có sai sót; tự động phát sự kiện Domain Event `MeetingInfoUpdatedEvent` để kích hoạt thông báo cho User thuộc phòng ban liên quan. |
| 3 | **Khôi phục cuộc họp đã xóa** | `PATCH /admin/meetings/:id/restore` | meeting_id (param) | Meeting khôi phục hiển thị | Hoàn tác trạng thái xóa mềm bằng cách đưa trường `deleted_at` về lại giá trị `NULL`. |
| 4 | **Cập nhật trạng thái khóa** | `PATCH /admin/meetings/:id/lock-status` | param: id; body: is_locked (boolean) | Meeting bản ghi đã cập nhật | Đóng hoặc mở trạng thái chỉnh sửa của biên bản cuộc họp (`is_locked`) nhằm bảo vệ tính pháp lý dữ liệu sau khi chốt. |

---

## Nhóm D: Quản lý Người dùng và Phòng ban (Đặc quyền Admin)

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy danh sách user** | `GET /admin/users` | page, limit, department_id, is_active, keyword | Danh sách User hệ thống (phân trang) | Liệt kê tài khoản nhân viên phục vụ quản trị phân quyền. |
| 2 | **Lấy chi tiết 1 user** | `GET /admin/users/:id` | user_id (param) | Thông tin chi tiết tài khoản | Xem toàn bộ trường thông tin cụ thể của một nhân viên. |
| 3 | **Tạo tài khoản mới** | `POST /admin/users` | email, password, full_name, employee_id, department_id, role | Đối tượng User vừa tạo | Khởi tạo tài khoản mới. Hệ thống áp dụng cơ chế tự sinh mật khẩu ngẫu nhiên và gửi thông tin qua Mailer. |
| 4 | **Cập nhật thông tin user** | `PATCH /admin/users/:id` | param: id; body: role, department_id | Bản ghi User đã cập nhật | Điều chỉnh vai trò hoặc điều chuyển phòng ban công tác. |
| 5 | **Cập nhật trạng thái hoạt động** | `PATCH /admin/users/:id/status` | param: id; body: is_active (boolean) | Bản ghi User đã cập nhật | Chặn hoặc khôi phục quyền truy cập. Khi `is_active = false`, hệ thống tự động thu hồi (revoke) toàn bộ refresh token. |
| 6 | **Lấy danh sách phòng ban** | `GET /admin/departments` | page, limit, deleted_status (ALL/ACTIVE/DELETED) | Danh sách các Department | Liệt kê cấu trúc phòng ban. Mặc định bộ lọc lọc trạng thái `ACTIVE`. |
| 7 | **Tạo phòng ban mới** | `POST /admin/departments` | name, address, description | Đối tượng Department vừa tạo | Thiết lập và khởi tạo một phòng ban mới trên hệ thống. |
| 8 | **Cập nhật thông tin phòng ban** | `PATCH /admin/departments/:id` | param: id; body: name, address, description | Bản ghi Department đã cập nhật | Chỉnh sửa metadata của phòng ban, tích hợp check UNIQUE trường tên với phòng ban đang hoạt động khác. |
| 9 | **Xóa mềm phòng ban** | `DELETE /admin/departments/:id` | department_id (param) | 200 OK xóa thành công | Soft-delete phòng ban. Hệ thống bắt buộc trả mã lỗi `409 Conflict` nếu số lượng nhân sự trực thuộc phòng ban lớn hơn 0. |
| 10 | **Khôi phục phòng ban đã xóa** | `PATCH /admin/departments/:id/restore` | department_id (param) | Đối tượng Department khôi phục | Hoàn tác xóa mềm phòng ban bằng cách xóa giá trị trường `deleted_at`. |

---

## Nhóm E: Thống kê & Báo cáo (Đặc quyền Admin)

> **Ràng buộc:** Bộ lọc query chung (`month`, `year`) áp dụng bắt buộc cho toàn bộ các API trong nhóm này.

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy chỉ số tổng quan** | `GET /admin/dashboard/kpis` | month, year | KPIs: Tổng cuộc họp, tổng giờ audio, số User active, TB giờ/cuộc họp | Trả về số liệu phục vụ hiển thị nhanh các thẻ số liệu trên Dashboard. |
| 2 | **Lấy dữ liệu xu hướng** | `GET /admin/dashboard/trends` | year, department_id (tùy chọn) | Mảng dữ liệu xu hướng của 12 tháng | Tổng hợp số lượng cuộc họp và thời lượng tiêu hao theo từng tháng; hỗ trợ lọc chi tiết theo phòng ban. |
| 3 | **Lấy xếp hạng phòng ban** | `GET /admin/dashboard/top-departments` | month, year | Danh sách Top 5 phòng ban | Sắp xếp và thống kê các phòng ban tạo ra nhiều phiên họp nhất. |
| 4 | **Lấy xếp hạng cuộc họp dài nhất** | `GET /admin/dashboard/top-meetings` | month, year, department_id (tùy chọn) | Danh sách Top 5 cuộc họp | Tổng hợp danh sách các cuộc họp có trường `duration_seconds` lớn nhất. |
| 5 | **Lấy phân bổ nhân sự** | `GET /admin/dashboard/staff-distribution` | month, year | Tỷ trọng số User chi tiết theo phòng ban | Lấy số liệu phục vụ render biểu đồ tròn (Donut Chart) phân bổ nhân sự doanh nghiệp. |

---

## Nhóm F: Live Meeting & Transcription (Giao thức mạng Realtime & REST)

| STT | Tên API | Giao thức & URL | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Tạo cuộc họp live** | `POST /meetings` | title, description, department_id (chỉ Admin) | Đối tượng Meeting (`status = LIVE`) | Khởi tạo tài nguyên phòng họp live mới. Khóa cứng trường chọn phòng ban nếu vai trò tài khoản kết nối là User thường. |
| 2 | **Mở kết nối WebSocket** | `WS /meetings/live` *(Handshake)* | meeting_id, JWT token (gửi qua query hoặc header) | Tín hiệu `Session ready` hoặc ném lỗi ngắt kết nối | Bắt tay thiết lập kênh realtime, tạo buffer lưu dữ liệu tạm trên Redis, bật instance VAD. Thực hiện đếm kiểm soát tải để tránh quá tải AI Service. |
| 3 | **Truyền nhận dữ liệu âm thanh** | `WS Message` | **Input:** audio_chunk (binary stream)<br>**Output:** `transcript_update` (JSON) | Phát luồng byte âm thanh liên tục lên Server; VAD Adapter ngắt đoạn, gọi song song module Speech2Text và SpeakerIdentify của Viettel AI Core, bắn trả kết quả TranscriptBlock hoàn chỉnh theo thời gian thực về Client. Quản lý tải qua cơ chế concurrency queue toàn cục. |
| 4 | **Cập nhật nhãn người nói** | `WS Message: update_speaker` | meeting_id, old_speaker_label, new_speaker_label | TranscriptBlock cập nhật nhãn | Thay đổi định danh nhãn người nói trực tiếp trong phiên live, áp dụng tự động cho các khối hội thoại phát sinh tiếp theo. |
| 5 | **Kết thúc phiên live** | `WS Message: end_session` | meeting_id | Kết quả Meeting (`status = COMPLETED`), audio_url | Kết thúc kết nối phòng họp, đóng file audio tạm vật lý local, thực hiện kéo URL đám mây lưu DB và dọn dẹp bộ đệm Redis. |
| 6 | **Resume phiên live** | `WS /meetings/live` *(Reconnect)* | meeting_id, JWT token, last_received_sequence | Mảng `missed_blocks` (nếu có dữ liệu kẹt) | Khôi phục kết nối WebSocket khi rớt mạng, đồng bộ bù các khối hội thoại bị bỏ lỡ từ bộ đệm tạm Redis dựa trên chỉ số sequence client gửi lên. |
| 7 | **Tạo cuộc họp từ file audio** | `POST /meetings/upload` | title, description, audio_file (multipart), department_id (chỉ Admin) | Đối tượng Meeting (`status = PROCESSING`) | Upload trực tiếp file ghi âm lên hệ thống, khởi chạy Background Job Worker để bóc băng xử lý nền bất đồng bộ. |
| 8 | **Kiểm tra trạng thái xử lý** | `GET /meetings/:id/status` | meeting_id (param) | status hiện tại, progress chỉ số (nếu có) | Phục vụ Client Polling đồng bộ lại trạng thái khi người dùng load lại trang hoặc quay trở lại màn hình chi tiết. |

---

## Nhóm G: Chức năng nâng cao & Mở rộng thêm

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Lấy bản tóm tắt biên bản** | `GET /meetings/:id/summary` | meeting_id (param) | 200 OK kèm text, hoặc 202 Accepted | Tự động kiểm tra dữ liệu cache tóm tắt tại bảng `meeting_summaries`: Nếu chưa có, trả mã `202 Accepted` và trigger AI chạy nền bóc tách; các lần truy cập tiếp theo trả ngay mã `200 OK` kèm kết quả cache. |
| 2 | **Tìm kiếm toàn văn transcript** | `GET /meetings/full-text-search` | keyword, department_id, from_date, to_date | Danh sách các đoạn TranscriptBlock khớp dữ liệu từ khóa | Tra cứu sâu vào nội dung hội thoại bên trong biên bản cuộc họp thông qua index PostgreSQL. |
| 3 | **Xuất biên bản ra PDF** | `GET /meetings/:id/export-pdf` | meeting_id (param) | File PDF (binary stream / download link) | Dựng cấu trúc giao diện biên bản cuộc họp và xuất bản tải về dưới định dạng file PDF cục bộ. |

---

## Nhóm H: Hệ thống API Thông báo (Notification)

> **Ràng buộc:** Tất cả các API này yêu cầu xác thực `JwtAuthGuard` và tự động bóc tách dữ liệu theo session của User đang đăng nhập[cite: 2, 10].

| STT | Tên API | URL / URI | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Xem danh sách thông báo** | `GET /notifications` | page, limit, is_read (tùy chọn) | Danh sách Notification của User (phân trang) + số lượng chưa đọc | Trả về danh sách thông báo nằm trong icon chuông của User hiện tại, ưu tiên thông báo mới nhất lên đầu[cite: 2]. |
| 2 | **Đánh dấu thông báo đã đọc** | `PATCH /notifications/:id/read` | notification_id (param) | Bản ghi Notification đã cập nhật `is_read = true` | Chuyển trạng thái của một thông báo cụ thể thành đã đọc[cite: 2]. |
| 3 | **Đánh dấu tất cả đã đọc** | `PATCH /notifications/read-all` | *(Không cần input, ăn theo token)* | Số lượng bản ghi đã cập nhật | Cập nhật đồng loạt toàn bộ thông báo đang ở trạng thái chưa đọc của User thành đã đọc[cite: 2]. |