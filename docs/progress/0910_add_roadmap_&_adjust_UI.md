# Báo cáo 09/10 — Roadmap và giao diện so sánh cũ – mới

> **Mục tiêu:** viết lộ trình phát triển, chỉnh giao diện theo review lần 3, thêm phần so sánh & đối chiếu hệ thống cũ – mới, và chốt cách xử lý mã hệ thống cũ.
> **Kết quả:** `.context/roadmap.md` (11 giai đoạn); `UI_offline.html` có tab Hệ thống cũ và màn hình so sánh; `checklist đối soát` đã bị bỏ; mã PHP chỉ được gợi ý, người dùng xác nhận.
> **Chưa commit.**
> Phần trước đó: [0810_ui_review_va_chot_thiet_ke.md](0810_ui_review_va_chot_thiet_ke.md).

> **Lưu ý đọc theo thứ tự:** các mục dưới đây ghi theo diễn biến trong ngày. Mục sau có thể thay đổi mục trước (ví dụ mục 1 nói roadmap 14 giai đoạn, mục 2 viết lại còn 11 giai đoạn; mục 3 bỏ checklist mà mục 2 vừa thêm). **Kết quả cuối cùng** là mục 3 và `.context/roadmap.md`.

---

## 1. Chỉnh giao diện lần 3 và viết roadmap bản đầu

- **Giao diện:**
  - Ô tìm kiếm dài, nằm giữa thanh trên cùng, placeholder ghi tên Workspace.
  - Bỏ thẻ tên luồng trên canvas. Thẻ "n bước chưa xác định" nằm cùng hàng với cụm nút nổi, ở góc phải.
  - Chú giải thu gọn được.
  - Nhãn chiều trả về dạng "trả về …".
  - Kịch bản: danh sách thẳng lề, tiêu đề "Scenario:".
  - Popover liên kết luôn nằm trọn trong canvas; popover menu giới hạn theo màn hình.
  - Chạy thử tự động: tất cả mục đạt, không có lỗi JavaScript. Đã tạo lại bản cục bộ.
- **Roadmap:** viết `.context/roadmap.md` gồm 14 giai đoạn MVP + Nên có (tới đóng gói và nghiệm thu) và 5 giai đoạn mở rộng. Mỗi task có Mục tiêu, Việc sẽ làm, Cách kiểm thử.

---

## 2. Roadmap theo lát cắt dọc và giao diện so sánh cũ – mới

- **Roadmap:** viết lại `.context/roadmap.md` còn 11 giai đoạn, gồm cả phần mở rộng. Mỗi giai đoạn làm cả BE lẫn FE để kiểm chứng ngay trên giao diện bằng dự án demo. Mini fixture tự viết chỉ dùng cho test tự động / CI, viết dần trong từng giai đoạn. Phần Mở rộng chỉ còn: phân tích PHP hệ thống cũ (GĐ 9) và so sánh & đối chiếu cũ – mới (GĐ 10).
- **Tài liệu:**
  - `feature_list.md`: thêm ghi chú phạm vi Mở rộng theo roadmap.
  - `API_list.md`: API C1 thêm tham số `role=LEGACY` để lấy cây Hệ thống cũ.
- **Giao diện (`UI_offline.html`):**
  - Import tự nhận ra mã PHP nằm trong `docs/` của Backend và thêm làm nguồn "Hệ thống cũ".
  - Cột trái có tab "Hệ thống cũ" (Module → Lớp PHP → Action / Template).
  - Luồng PHP: Giao diện cũ → Bộ định tuyến → Lớp nghiệp vụ → BaseCrud → MongoDB.
  - Nút "So sánh cũ – mới": chọn luồng tương ứng có bằng chứng (tài liệu đặc tả / độ tương đồng); chia đôi canvas; tô màu bước khác biệt.
  - Tab Đối chiếu: danh sách khác biệt kèm vị trí code hai phía, checklist đối soát do AI viết; AI tắt thì danh sách khác biệt vẫn đủ.
  - Code PHP mẫu tự viết, đổi tên so với dự án demo (`BaseCrud`, `PERMISSIONS`, `$collection`).
- **Lỗi đã sửa:** đoạn code `.tpl` mẫu chứa chuỗi `</script>` làm trình duyệt cắt ngang khối script của trang. Đã đổi thành `<\/script>` và kiểm tra trang chỉ còn đúng một thẻ đóng.
- **Kiểm tra:** chạy thử tự động bằng Chrome không giao diện, tất cả mục đạt (thêm 15 mục cho hệ thống cũ và so sánh), không có lỗi JavaScript.

---

## 3. Bỏ checklist đối soát; mã PHP chỉ được gợi ý

- **Bỏ chức năng "Tạo checklist đối soát"** (API H4, mục đích AI `MIGRATION_CHECKLIST`): chỉ là danh sách tích tĩnh, không thêm thông tin so với danh sách khác biệt. Đã sửa `feature_list.md` (còn 72 chức năng: 46 / 4 / 22), `API_list.md`, `overview.md`, `database.md`, `.dbml`, `project_structure.md`, `CLAUDE.md`, roadmap, enum `AiPurpose`, migration V2 (tạo lại kiểu `ai_purpose`) và giao diện.
- **Nhận diện hệ thống cũ chỉ là gợi ý:** "cũ / mới" là khái niệm nghiệp vụ, không suy ra được từ code. Tool chỉ báo "có thể là mã hệ thống cũ" khi thấy mật độ file PHP cao; nguồn `LEGACY` chỉ được tạo khi người dùng bấm xác nhận (ở cả Import và Cài đặt › Nguồn mã). Mã cũ nằm lẫn trong codebase mới được phân tích như một phần của nguồn chứa nó.
- **Roadmap:** thêm vào Giai đoạn 8 việc chạy thử trên 1–2 dự án mã nguồn mở công khai (Spring Boot + Angular, không phải demo) để kiểm tra tool không bị gắn cứng vào demo.
- **Kiểm tra:** chạy thử giao diện tự động đạt toàn bộ; Engine `verify` xanh.

---

## 4. Việc còn dở

- Chưa bắt đầu Giai đoạn 1 của roadmap (Workspace & Import dự án).
- Chưa chạy thử tool trên dự án mã nguồn mở công khai nào (dự kiến ở Giai đoạn 8).
- Giao diện so sánh cũ – mới mới là bản vẽ với dữ liệu mẫu; cần Giai đoạn 9–10 để có dữ liệu thật.
