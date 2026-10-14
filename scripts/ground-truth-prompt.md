# Prompt để agent ở dự án demo lập ground truth (NFR-08, NFR-09)

> Dán nguyên khối bên dưới (từ "VAI TRÒ" tới hết) vào Claude Code đang mở **trong thư mục dự án demo**. Prompt tự chứa đủ ngữ cảnh, không cần agent biết gì về CtV Flow Tracer.
> Kết quả là **một file JSON đặt ngoài repo của tool** (đường dẫn bạn tự chọn). Sau đó chạy:
> `node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <file.json> --verbose`
> Không commit, không gửi file này đi đâu — nó chứa tên API / màn hình của dự án công ty.

---

## VAI TRÒ

Bạn là kỹ sư kiểm thử đang lập **bộ đáp án đúng ("ground truth")** để chấm điểm một công cụ phân tích tĩnh mã nguồn. Công cụ đó đọc dự án này (Backend Java/Spring Boot + Frontend Angular) và tự suy ra: (1) nút / ô nhập nào trên giao diện gọi API nào; (2) một API khi chạy sẽ đi qua những bảng CSDL nào. Nhiệm vụ của bạn là cho **đáp án thật**, đọc từ mã nguồn và tài liệu của dự án — KHÔNG phải đoán, và KHÔNG được dùng kết quả của công cụ đó.

## RÀNG BUỘC (bắt buộc)

- Chỉ **đọc**. Không chạy, không build, không cài thư viện, không kết nối CSDL, không sửa bất kỳ file nào trong dự án.
- Đáp án phải xuất phát từ **mã nguồn và tài liệu có thật**. Chỗ nào chưa chắc chắn thì **bỏ mục đó**, đừng đoán. Thà ít mục mà đúng còn hơn đủ 30 mà có mục sai.
- Ghi file kết quả **ngoài thư mục dự án**. Không sao chép đoạn code vào file kết quả; chỉ ghi tên theo định dạng dưới đây.
- Không ghi thông tin bí mật (mật khẩu, token, chuỗi kết nối) vào bất cứ đâu.

## VIỆC CẦN LÀM

Lập **30 luồng** đa dạng, chia thành hai danh sách `links` và `traces` (mỗi luồng có thể xuất hiện ở cả hai).

### Danh sách `links` — "phần tử giao diện này gọi những API nào" (khoảng 30 mục)

Mỗi mục là **một phần tử giao diện** (nút, liên kết, ô nhập, hàng bảng… có xử lý sự kiện) và **toàn bộ** API Backend mà thao tác đó gọi (kể cả API gọi nối tiếp, ví dụ lưu xong tải lại danh sách). Cách làm: từ template (`.html`/inline template) → hàm xử lý sự kiện → service/facade Angular → lời gọi HTTP → đường dẫn → Controller Backend tương ứng.

Cơ cấu mong muốn (xấp xỉ, ưu tiên đa dạng hơn đủ số lượng):
- ~12 phần tử gọi **đúng 1 API**;
- ~6 phần tử gọi **nhiều API** (lưu rồi tải lại, xóa rồi tải lại, gọi song song…);
- ~4 phần tử **chỉ thao tác giao diện, không gọi máy chủ** (mở hộp thoại, chuyển tab, đóng…) → `"apis": []`;
- ~4 phần tử gọi API **qua service/facade dùng chung** nhiều màn hình, hoặc qua hàm trung gian nhiều tầng;
- ~4 phần tử gọi API mà **đường dẫn ghép từ hằng số / biến cấu hình / tiền tố gateway**.
Rải đều trên nhiều màn hình / module khác nhau, không dồn vào một màn hình.

Mỗi mục có dạng:
```json
{ "screen": "<tên màn hình / hộp thoại / tab chứa phần tử>", "element": "<nhãn của phần tử>", "apis": ["POST /api/v1/xxx", "GET /api/v1/xxx"] }
```
- `screen`: tên đúng như người dùng thấy (tiêu đề trang, tiêu đề hộp thoại, tên tab, tên mục menu). Nếu phần tử nằm trong hộp thoại / tab con, ghi **tên hộp thoại / tab đó** (không phải tên màn hình cha). Chỉ cần một đoạn đủ để nhận ra (khớp theo "chứa chuỗi", không phân biệt hoa thường, dấu).
- `element`: **chữ hiển thị trên giao diện bằng tiếng Việt**, đúng như người dùng thấy ("Lưu", "Thêm mới", "Xuất Excel"). Nếu chữ lấy từ khóa dịch (i18n / `translate` pipe), hãy tra file dịch tiếng Việt của dự án để ghi **chữ đã dịch**, không ghi khóa. **Ưu tiên phần tử có chữ hiển thị**; chỉ chọn phần tử không có chữ (nút chỉ có icon) khi thật cần, và khi đó ghi **tên hàm xử lý** hoặc `id` / `formControlName` của nó.
- **Tránh phần tử trùng nhãn trong cùng một `screen`** (hai nút cùng tên "Lưu" trong cùng hộp thoại); nếu buộc phải chọn thì bỏ mục đó.
- `apis`: mỗi API là `"<PHƯƠNG THỨC> <đường dẫn>"`, **đường dẫn đúng như Backend phục vụ** (ghép đủ: context-path của ứng dụng + `@RequestMapping` của lớp + mapping của hàm; thay giá trị `${...}` bằng giá trị thật trong file cấu hình mặc định). Đây là dạng giống tài liệu Swagger/OpenAPI của Backend, **không phải** URL ghép tiền tố gateway ở phía Frontend. Tên biến đường dẫn (`{id}`, `{categoryId}`) tùy ý. Phương thức viết hoa.
- Mỗi API ghi trong `apis` phải **thật sự tồn tại** ở Controller; kiểm tra bằng cách tìm mapping tương ứng trong code Backend trước khi ghi.

### Danh sách `traces` — "API này đi tới những bảng nào" (30 mục, đa dạng)

Mỗi mục là một điểm xuất phát (một API, hoặc một phần tử giao diện như ở trên) và **tập đầy đủ các bảng CSDL** mà luồng xử lý chạm tới (đọc hoặc ghi). Cách làm: Controller → Service (đi theo cả nhánh `if`, lệnh gọi service khác, gọi nội bộ) → Repository/DAO/Mapper → câu SQL/JPQL/`@Table`/XML mapper → tên **bảng vật lý**.

Cơ cấu mong muốn:
- ~8 API chỉ chạm **1 bảng**;
- ~10 API chạm **nhiều bảng** (join, ghi nhiều bảng, kiểm tra trùng ở bảng khác, ghi nhật ký/audit);
- ~4 API **không chạm bảng nào** (trả dữ liệu cấu hình, tính toán thuần) → `"tables": []`;
- ~4 API đi qua **interface có nhiều lớp hiện thực** hoặc **hàm kế thừa từ lớp cơ sở**;
- ~4 API dùng **truy vấn ghép động** (chuỗi SQL/JPQL ghép theo điều kiện) hoặc **truy vấn gốc (native)**.

Mỗi mục có dạng:
```json
{ "start": { "api": "GET /api/v1/xxx/{id}" }, "tables": ["ten_bang_1", "ten_bang_2"] }
{ "start": { "screen": "<tên màn hình>", "element": "<nhãn phần tử>" }, "tables": ["ten_bang"] }
```
- `tables`: **tên bảng vật lý** viết thường (đúng như trong `@Table(name=…)`, hoặc suy ra theo quy ước đặt tên khi entity không khai báo, hoặc trong câu SQL). Ghi **mọi** bảng luồng chạm tới, không chỉ bảng chính; nhưng **không ghi** bảng chỉ có mặt ở nhánh code không thể xảy ra.
- Có thể thêm `"maxDepth": 20` nếu luồng sâu hơn mặc định (12 bước).
- Không ghi mục nào mà bạn chưa đọc hết chuỗi gọi tới tận tầng truy cập dữ liệu.

## NGUỒN THAM KHẢO

Dùng theo thứ tự ưu tiên: (1) **mã nguồn** (nguồn sự thật cuối cùng); (2) tài liệu đặc tả của dự án, đặc biệt bảng "ánh xạ giao diện ↔ API" và tài liệu API theo module nếu có — dùng để **gợi ý chọn luồng**, nhưng luôn **đối chiếu lại với mã nguồn** vì tài liệu có thể lỗi thời; (3) file Swagger/OpenAPI nếu có.

## TỰ KIỂM TRA TRƯỚC KHI NỘP

1. File là JSON hợp lệ, đúng cấu trúc `{ "links": [...], "traces": [...] }`.
2. Mọi API trong `links[].apis` và `traces[].start.api` đều tìm thấy mapping tương ứng trong code Backend (tìm lại bằng lệnh tìm kiếm, không dựa trí nhớ).
3. Không có hai mục `links` trùng `(screen, element)`.
4. Không có bảng nào trong `tables` mà bạn chưa thấy tên đó trong `@Table`, entity, XML mapper hoặc câu SQL.
5. Số mục: mục tiêu 30 ở mỗi danh sách; nếu thiếu vì phải bỏ mục không chắc chắn thì **giữ nguyên số mục đã chắc**, đừng điền cho đủ.

## ĐẦU RA

1. Ghi file JSON vào: ngay ngoài thư mục dự án.
2. Trả lời chỉ gồm **số liệu tổng hợp**, không nhắc tên API / màn hình / bảng cụ thể: số mục `links` (và bao nhiêu mục `apis` rỗng), số mục `traces` (và bao nhiêu mục `tables` rỗng), số mục đã bỏ vì không chắc chắn kèm lý do chung (ví dụ "đường dẫn ghép từ biến không tra được"), và những quy ước đặc biệt của dự án mà người đọc nên biết (ví dụ cách đặt tiền tố gateway, cách dịch nhãn) — mô tả chung, không nêu tên cụ thể.
