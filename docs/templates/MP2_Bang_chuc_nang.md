# BẢNG TỔNG HỢP CHỨC NĂNG HỆ THỐNG — PHÂN HỆ MP2

> **Ghi chú phân quyền:** > - **User:** Áp dụng khi cả User thông thường và Admin đều sử dụng được (Admin kế thừa toàn bộ quyền hạn của User).
> - **Admin:** Chỉ áp dụng đối với các hành vi hoặc giao diện đặc thù độc quyền của Admin.
> Tổng số: 38 chức năng chia làm 8 nhóm độc lập.

---

## Nhóm 1: Xác thực & Quản lý thông tin cá nhân

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Đăng nhập** | User | email, password | access_token (JWT), refresh_token, thông tin user + role | Xác thực tài khoản, cấp token kèm payload phân quyền. |
| **Quên mật khẩu (gửi OTP)** | User | email | Thông báo đã gửi OTP về email | Gửi mã OTP về email để xác thực yêu cầu đặt lại mật khẩu. |
| **Đặt lại mật khẩu (qua OTP)** | User | email, otp_code, new_password | Thông báo đặt lại mật khẩu thành công | Xác thực mã OTP, cập nhật mật khẩu mới cho tài khoản. |
| **Đổi mật khẩu (khi đã đăng nhập)** | User | old_password, new_password | Thông báo đổi mật khẩu thành công | Cho phép người dùng tự đổi mật khẩu khi đang đăng nhập. |
| **Xem hồ sơ cá nhân** | User | *(Không cần input, lấy theo token)* | Thông tin hồ sơ (tên, email, avatar, phòng ban, role) | Lấy thông tin tài khoản đang đăng nhập. |
| **Cập nhật hồ sơ cá nhân (avatar)** | User | avatar (file ảnh) | Thông tin hồ sơ đã cập nhật | Cập nhật ảnh đại diện tài khoản cá nhân; họ tên và Employee ID được định cấu hình từ đầu, không cho sửa. |

---

## Nhóm 2: Quản lý & Tra cứu cuộc họp (Theo phòng ban)

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách cuộc họp** | User | page, limit, filter (trạng thái, ngày...) | Danh sách Meeting thuộc phòng ban của user (phân trang) | Hiển thị các cuộc họp trong nội bộ phòng ban. |
| **Tìm kiếm cuộc họp theo tên** | User | keyword (tên cuộc họp), page, limit | Danh sách Meeting có title khớp từ khóa | Tìm nhanh cuộc họp theo tên, trong phạm vi phòng ban quản lý. |
| **Xem chi tiết biên bản** | User | meeting_id | Thông tin Meeting + Transcript đầy đủ + audio_url | Xem lại nội dung biên bản và nghe lại file ghi âm gốc. |
| **Xóa mềm cuộc họp** | User | meeting_id | Thông báo xóa thành công | Ẩn cuộc họp khỏi danh sách hiển thị; User chỉ xóa được cuộc họp do chính mình host, Admin xóa được mọi cuộc họp. |

---

## Nhóm 3: Quản trị hệ thống cuộc họp (POV Admin)

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách tổng thể** | Admin | page, limit, filter (phòng ban, trạng thái, ngày...) | Danh sách toàn bộ Meeting hệ thống (không giới hạn phòng ban) | Giám sát và quản lý toàn bộ các cuộc họp trên toàn nền tảng. |
| **Chỉnh sửa thông tin họp** | Admin | meeting_id, title, description, department_id | Meeting đã cập nhật | Sửa thông tin cơ bản (không đụng transcript/audio) khi có sai sót hoặc điều chuyển phòng ban; hệ thống tự gửi thông báo tới User liên quan. |
| **Khôi phục cuộc họp** | Admin | meeting_id | Meeting được khôi phục về danh sách hiển thị | Khôi phục cuộc họp đã xóa mềm nếu còn trong thời hạn cho phép. |
| **Thay đổi trạng thái biên bản** | Admin | meeting_id, is_locked (true/false) | Meeting với trạng thái khóa đã cập nhật | Khóa biên bản để bảo vệ tính pháp lý, ngăn sửa đổi text sau khi chốt; Admin mở khóa lại được khi cần. |

---

## Nhóm 4: Quản lý User và Department

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách nhân viên** | Admin | page, limit, filter (phòng ban, trạng thái), keyword | Danh sách User (phân trang) | Liệt kê tài khoản nhân sự kết hợp lọc và tìm kiếm theo định danh. |
| **Đăng ký tài khoản mới** | Admin | email, password, full_name, employee_id, department_id, role | User mới được tạo | Tạo tài khoản mới cho người dùng trong hệ thống (Cấp phát tập trung). |
| **Sửa thông tin User** | Admin | user_id, role, department_id | User đã cập nhật | Điều chỉnh chức vụ, vai trò, phòng ban của tài khoản (không can thiệp vào avatar/password). |
| **Vô hiệu hóa / Kích hoạt User** | Admin | user_id, is_active | User đã cập nhật trạng thái | Chặn hoặc khôi phục quyền truy cập hệ thống của nhân viên, không làm mất dữ liệu lịch sử. |
| **Xem danh sách phòng ban** | Admin | page, limit | Danh sách Department | Liệt kê các phòng ban hiện có trong hệ thống tổ chức. |
| **Thêm mới Department** | Admin | name | Department mới được tạo | Khởi tạo mô hình cấu trúc phòng ban mới cho công ty. |
| **Sửa Department** | Admin | department_id, name | Department đã cập nhật | Chỉnh sửa tên phòng ban; kiểm tra ràng buộc không trùng tên với các phòng ban đang hoạt động khác. |
| **Xóa mềm Department** | Admin | department_id | Thông báo xóa thành công, hoặc báo lỗi hệ thống | Soft-delete phòng ban; CHỈ cho phép khi không còn bất kỳ User nào thuộc phòng ban đó. |
| **Khôi phục Department** | Admin | department_id | Department được khôi phục về danh sách hiển thị | Khôi phục phòng ban đã xóa mềm nếu còn nằm trong thời hạn cho phép. |

---

## Nhóm 5: Thống kê & Báo cáo

> **Quy tắc bộ lọc:** Bộ lọc chung (Tháng, Năm) áp dụng đồng bộ cho cả 5 chức năng. Bộ lọc riêng theo phòng ban chỉ áp dụng thêm cho biểu đồ "Phân tích xu hướng" và "Xếp hạng cuộc họp dài nhất".

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Theo dõi thẻ chỉ số (KPIs)** | Admin | filter chung: tháng, năm | Tổng số cuộc họp, tổng giờ audio, số User active, TB giờ/cuộc họp | Cung cấp cái nhìn nhanh về tài nguyên hệ thống đã tiêu thụ. |
| **Phân tích xu hướng** | Admin | filter chung: tháng, năm; filter riêng: phòng ban | Dữ liệu biểu đồ theo 12 tháng (số cuộc họp, thời lượng) | Đánh giá tần suất sử dụng theo thời gian, có thể lọc chi tiết theo phòng ban cụ thể. |
| **Xếp hạng phòng ban** | Admin | filter chung: tháng, năm | Top 5 phòng ban có nhiều cuộc họp nhất | Liệt kê phòng ban hoạt động nhiều nhất trong khoảng thời gian lọc dữ liệu. |
| **Xếp hạng cuộc họp dài nhất** | Admin | filter chung: tháng, năm; filter riêng: phòng ban | Top 5 cuộc họp có thời lượng audio dài nhất | Liệt kê các cuộc họp dài nhất, hỗ trợ bộ lọc phân tách theo phòng ban. |
| **Phân bổ nhân sự** | Admin | filter chung: tháng, năm | Tỷ trọng số User theo từng phòng ban | Khai thác dữ liệu biểu đồ thể hiện phân bổ nhân sự giữa các đơn vị. |

---

## Nhóm 6: Live Meeting & Transcription (Nghiệp vụ cốt lõi MP2)

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Tạo cuộc họp trực tuyến** | User | title, description, department_id (chỉ Admin) | Meeting (status = LIVE) | Khởi tạo phiên họp live mới thông qua hệ thống REST API; Backend tự động ép phòng ban cho User thường. |
| **Mở phiên ghi âm (WebSocket)** | User | meeting_id, JWT token | Session ready hoặc bị từ chối kết nối | Thiết lập kết nối WebSocket, khởi tạo buffer Redis và VAD instance. Thực hiện check bộ đếm số phiên Live trên Redis nhằm bảo vệ tài nguyên. |
| **Luồng truyền phát & nhận transcript realtime** | User | audio chunk (binary, gửi liên tục) | Transcript block hoàn chỉnh (kèm speaker_id, start_time, end_time) | Client gửi byte âm thanh liên tục; VAD xử lý cắt đoạn khoảng lặng, gọi song song Speech2Text và SpeakerIdentify để trả text realtime qua WS. Giới hạn bằng bộ đếm concurrency. |
| **Kết thúc phiên họp live** | User | meeting_id (tín hiệu end_session qua WS) | Meeting (status = COMPLETED), audio_url, transcript đã lưu DB | Đóng luồng ghi âm file tạm, lưu toàn bộ transcript hoàn chỉnh, cập nhật trạng thái cuộc họp. |
| **Tự động kết nối lại (Reconnect)** | User | meeting_id, JWT token, last_received_sequence | missed_blocks (nếu có dữ liệu kẹt), tiếp tục session | Khôi phục kết nối WebSocket nhanh, đồng bộ bù dữ liệu in-flight từ bộ đệm Redis, bảo toàn nội dung biên bản. |
| **Tải lên file audio tạo cuộc họp** | User | title, description, audio_file (multipart), department_id (chỉ Admin) | Meeting (status = PROCESSING) | Khởi tạo phiên bóc băng từ file ghi âm có sẵn bên ngoài qua REST API. |
| **Xử lý bóc băng nền** | — | audio_url (nhận qua Job Queue ngầm) | Transcript đã lưu, Meeting (status = COMPLETED) | Job Worker xử lý nền bất đồng bộ file audio theo concurrency quy định, kết thúc bắn event cập nhật trạng thái. |

---

## Nhóm 7: Chức năng nâng cao (Mở rộng thêm)

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Chỉnh sửa định danh người nói** | User | meeting_id, old_speaker_label, new_speaker_label | Transcript block đã cập nhật nhãn người nói | Cho phép Host sửa nhãn người nói trực tiếp trong phiên live, áp dụng tự động cho các đoạn phát biểu tiếp theo. |
| **Tìm kiếm toàn văn (Full-text)** | User | keyword, filter (phòng ban, ngày...) | Danh sách Meeting/đoạn transcript khớp từ khóa | Tra cứu biên bản trong quá khứ theo nội dung văn bản transcript chi tiết, không chỉ quét theo tên tiêu đề. |
| **Tóm tắt biên bản họp (AI)** | User | meeting_id | Đoạn văn bản tóm tắt nội dung cuộc họp | Tự động tổng hợp nội dung chính ngắn gọn của biên bản cuộc họp ngay khi hoàn thành xử lý. |
| **Xuất PDF** | User | meeting_id | File PDF (binary stream hoặc download link) | Xuất và tải dữ liệu văn bản biên bản cuộc họp dưới định dạng tài liệu PDF về local. |

---

## Nhóm 8: Thông báo (Notification)

> Hệ thống tự động tạo thông báo khi phát sinh các sự kiện: (1) Có cuộc họp mới được tạo trong phòng ban quản lý, (2) Cuộc họp chuyển đổi trạng thái thành công sang `COMPLETED`, (3) Admin sửa đổi thông tin cuộc họp. Mỗi User thuộc phòng ban sẽ nhận được một bản ghi độc lập.

| Tên chức năng | Phân quyền | Input | Output | Mô tả chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **Xem danh sách thông báo** | User | page, limit, is_read (tùy chọn) | Danh sách Notification của User (phân trang), tổng số chưa đọc | Hiển thị danh sách thông báo trong icon chuông, ưu tiên đẩy thông báo mới nhất lên đầu. |
| **Đánh dấu thông báo đã đọc** | User | notification_id | Notification đã cập nhật is_read = true | Đánh dấu một thông báo cụ thể đã được người dùng click xem. |
| **Đánh dấu tất cả đã đọc** | User | *(Không cần input, theo session)* | Số lượng thông báo đã được cập nhật | Chuyển trạng thái toàn bộ thông báo chưa đọc của tài khoản hiện tại thành đã đọc. |