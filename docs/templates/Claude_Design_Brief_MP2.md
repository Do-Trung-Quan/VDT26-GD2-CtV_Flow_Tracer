# BRIEF THIẾT KẾ UI — HỆ THỐNG MP2 (Intelligent Meeting Transcription System)

## 1. Tổng quan dự án

Đây là phân hệ MP2 trong hệ thống vSM (Virtual Session Manager) — hệ thống chuyển đổi nội dung biên bản họp realtime. Người dùng ghi âm cuộc họp trực tiếp (qua microphone) hoặc tải lên file audio có sẵn; hệ thống tự động chuyển đổi giọng nói thành văn bản (kèm nhãn người nói), cho phép xem lại, tìm kiếm, tóm tắt bằng AI và xuất PDF.

**Đối tượng dùng:** nhân viên nội bộ doanh nghiệp (vai trò User) và quản trị viên hệ thống (vai trò Admin, kế thừa toàn bộ quyền User).

## 2. Phong cách thiết kế

- **Tinh thần chung:** chuyên nghiệp, chuẩn doanh nghiệp, nghiêm túc — không phải ứng dụng tiêu dùng vui nhộn. Đây là công cụ làm việc dùng hàng ngày trong giờ hành chính.
- **Thương hiệu:** lấy cảm hứng từ màu nhận diện Viettel — đỏ làm accent color chủ đạo (CTA chính, trạng thái LIVE/đang ghi âm), không dùng đỏ làm màu nền diện rộng.

**Bảng màu:**
| Vai trò | Mã màu |
|---|---|
| Primary/Accent (CTA, badge LIVE) | #EE0033 |
| Primary Dark (hover) | #C40029 |
| Text chính / Sidebar | #1A2332 |
| Nền chính | #F5F6F8 |
| Card/Surface | #FFFFFF |
| Border/Divider | #E2E5EA |
| Success (COMPLETED) | #2E9E5B |
| Warning (PROCESSING) | #E8A23D |

- **Typography:** sans-serif rõ ràng (Inter hoặc tương đương).
- **Border-radius:** nhỏ-vừa (6-8px), tránh bo tròn quá mức.
- **Shadow:** rất nhẹ, tối giản.
- **Icon:** bộ line-icon nhất quán (không filled).
- **Density:** vừa phải — ưu tiên dễ scan thông tin nhanh hơn là tối giản tuyệt đối.

## 3. Cấu trúc điều hướng tổng thể

- Các màn hình: **Login, Reset Password, Meeting List, Personal, Quản lý User, Quản lý Department, Dashboard** dùng layout chung có **Sidebar + Header** cố định.
- Hai màn hình **Live Workspace** và **Meeting Detail** là **full-screen riêng biệt, KHÔNG có sidebar/header chung** — chiếm toàn bộ viewport.
  - *Meeting Detail* cần 1 nút "Back" rõ ràng ở góc trên trái để quay lại Meeting List.
  - *Live Workspace* **không có đường thoát nào trên UI** (không sidebar, không nút back) — đây là chủ đích, vì Host không được phép rời khỏi phòng họp khi đang ghi.

## 4. Danh sách màn hình chi tiết

### 4.1 Login
- Chỉ có 2 trường: Email, Password. Nút đăng nhập. Link "Quên mật khẩu?".

### 4.2 Reset Password (3 bước nối tiếp, dạng wizard/stepper)
1. Nhập email để tìm tài khoản.
2. Nhập mã OTP đã gửi về email.
3. Đặt mật khẩu mới + xác nhận.

### 4.3 Meeting List (màn hình mặc định sau khi login)
- **Thanh công cụ trên cùng:** ô tìm kiếm theo tên cuộc họp; filter Status (LIVE/PROCESSING/COMPLETED); filter khoảng ngày; filter Department (chỉ Admin); filter trạng thái xóa — 3 lựa chọn: **Tất cả / Đang hoạt động / Đã xóa** (chỉ Admin).
- **2 nút CTA nổi bật:** "Tạo cuộc họp trực tuyến" và "Tải lên file audio".
- **Bảng/danh sách**, mỗi dòng gồm: Title, badge Status (màu theo bảng màu: đỏ=LIVE, vàng=PROCESSING, xanh=COMPLETED), badge Type (LIVE/UPLOAD), tên Host, Department, thời gian tạo, icon nhỏ khóa 🔒 hoặc bút ✏️ tương ứng `is_locked`.
- **Sắp xếp cố định** (không có UI chọn sắp xếp): LIVE → PROCESSING → COMPLETED, trong nhóm sắp theo thời gian tạo giảm dần.
- **Click vào dòng:** nếu `status = COMPLETED` → mở Meeting Detail; nếu LIVE/PROCESSING → không cho vào (hiển thị tooltip/toast giải thích).
- **Menu 3 chấm mỗi dòng:** Xóa mềm (User: chỉ Meeting do mình host; Admin: mọi Meeting); Khôi phục, Sửa thông tin, Khóa/Mở khóa biên bản (chỉ Admin).
- Phân trang ở cuối danh sách.
- Icon chuông thông báo ở Header, có badge số lượng chưa đọc.

### 4.4 Live Workspace (full-screen riêng)
- **Header:** tên cuộc họp; đồng hồ đếm tổng thời gian phòng họp (từ lúc Meeting bắt đầu, chạy liên tục không dừng); banner trạng thái kết nối — 3 trạng thái: "Ổn định" / "Mất kết nối, đang thử kết nối lại…" / "Đang đồng bộ dữ liệu…".
- **Vùng giữa (chiếm phần lớn màn hình) — Transcript realtime:** danh sách block hiện dần, tự cuộn xuống mới nhất. Mỗi block: nhãn người nói (tô màu khác nhau theo từng speaker), nội dung text, mốc thời gian nhỏ dạng mm:ss. Click vào nhãn người nói → sửa tên inline.
- **Vùng dưới cùng (đè nổi — floating bar):** waveform động hiển thị realtime theo âm lượng micro; nút Start/Pause/Resume ghi âm; nút "Kết thúc cuộc họp" (tách riêng, nổi bật, màu đỏ); đồng hồ đếm thời lượng ghi âm thực tế (dừng khi Pause, không phải đồng hồ ở header).

### 4.5 Meeting Detail (full-screen riêng, layout 3 vùng + audio player nổi)
- **Cột phải:** thông tin cuộc họp (tên, badge status, badge type, host, department, created_at, ended_at); nút Xuất PDF; nút Khóa/Mở khóa biên bản và Sửa thông tin (chỉ Admin).
- **Cột giữa (chính):** danh sách TranscriptBlock tĩnh theo trình tự thời gian, giống giao diện Live Workspace nhưng không realtime. Click vào 1 block → audio player nhảy tới đúng `start_time`.
- **Cột trái:** vùng AI tóm tắt — hiện skeleton loading "Đang tóm tắt..." nếu đang xử lý, hoặc đoạn tóm tắt nếu đã xong.
- **Audio player (đè nổi, dưới cùng):** waveform, đồng hồ thời lượng, nút play/pause, tua nhanh/lùi, dropdown chọn tốc độ phát (0.5x/1x/1.5x/2x).

### 4.6 Personal
- Section "Thông tin cá nhân": avatar (có nút đổi ảnh), họ tên/email/employee ID/department/role — toàn bộ read-only trừ avatar.
- Section "Đổi mật khẩu" (tách riêng): mật khẩu cũ, mật khẩu mới, xác nhận, nút Đổi mật khẩu.

### 4.7 Quản lý User (Admin)
- Thanh công cụ: tìm kiếm theo tên/email/employee ID; filter Department, filter is_active; nút "Thêm tài khoản mới" mở popup.
- Bảng: Avatar, Họ tên, Email, Employee ID, Department, badge Role (USER/ADMIN), badge Trạng thái (Active/Inactive), ngày tạo.
- Action mỗi dòng: sửa inline (role, department), toggle switch Active/Inactive.
- Form "Thêm tài khoản mới": Email, Họ tên, Employee ID, dropdown Department, radio Role. **Không có trường password** (hệ thống tự sinh ngẫu nhiên và gửi qua email, Admin không nhìn thấy).

### 4.8 Quản lý Department (Admin)
- Thanh công cụ: nút "Thêm phòng ban mới"; thanh tìm kiếm theo tên; **filter trạng thái 3 lựa chọn: Tất cả / Đang hoạt động / Đã xóa**.
- Bảng: Tên, Địa chỉ, Mô tả, số lượng User thuộc phòng ban.
- Action: Sửa, Xóa mềm (disable + tooltip nếu còn User thuộc phòng ban). **Nút Khôi phục chỉ hiển thị với phòng ban đang ở trạng thái Đã xóa**, thay thế vị trí nút Sửa/Xóa.
- Form thêm/sửa: Tên, Địa chỉ (bắt buộc), Mô tả (tùy chọn).

### 4.9 Dashboard (Admin)
- Filter trên cùng (áp dụng toàn trang): dropdown Tháng, dropdown Năm.
- Hàng KPI Cards (4 thẻ ngang hàng): Tổng số cuộc họp, Tổng giờ audio, Số User active, Trung bình giờ/cuộc họp.
- Biểu đồ Phân tích xu hướng (full-width, ngay dưới KPI): Line/Bar chart theo 12 tháng, có dropdown filter riêng theo Department.
- 2 biểu đồ song song bên dưới:
  - Trái: biểu đồ cột ngang Top 5 phòng ban có nhiều cuộc họp nhất.
  - Phải: Donut chart phân bổ nhân sự theo Department, kèm cột chú thích (số lượng + %) bên cạnh.
- (Đã bỏ widget "Xếp hạng cuộc họp dài nhất" khỏi bản thiết kế UI hiện tại.)

## 5. Lưu ý kỹ thuật để giữ thiết kế khả thi khi code

- Mọi field hiển thị phải khớp đúng tên/kiểu dữ liệu trong tài liệu Database & API đính kèm (không tự thêm field không tồn tại).
- 3 trạng thái Meeting (LIVE/PROCESSING/COMPLETED) và type (LIVE/UPLOAD) cần dùng đúng 1 hệ màu/icon nhất quán ở MỌI nơi xuất hiện (Meeting List, Meeting Detail).
- Không thiết kế chức năng cộng tác chỉnh sửa transcript nhiều người cùng lúc — việc này thuộc phân hệ khác (MP1), ngoài phạm vi.

---
**Tài liệu đính kèm cần đọc cùng:** Danh sách API (MP2_Danh_sach_API.docx), Bảng chức năng (MP2_Bang_chuc_nang.docx), Thiết kế CSDL (MP2_Thiet_ke_CSDL_Entities.docx + mp2_dbdiagram.dbml).