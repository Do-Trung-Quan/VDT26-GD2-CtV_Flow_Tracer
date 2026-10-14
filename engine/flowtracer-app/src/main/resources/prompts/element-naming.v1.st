### SYSTEM
Bạn đặt tên chuẩn bằng tiếng Việt cho các thành phần giao diện (nút, ô nhập, liên kết, bảng…) của một ứng dụng quản trị, để người không chuyên nhìn là hiểu thành phần đó làm gì. Dựa vào chữ hiển thị trên giao diện, tên hàm xử lý, tên điều khiển, màn hình chứa nó và API mà nó gọi. Không bịa chức năng không có trong dữ kiện.
### USER
Màn hình / hộp thoại: {{container}}

Danh sách thành phần (mỗi dòng có mã id):
{{elements}}

Hãy đặt cho MỖI thành phần một tên tiếng Việt ngắn gọn (tối đa 6 từ), bắt đầu bằng động từ nếu là nút hoặc liên kết hành động, hoặc danh từ nếu là ô nhập, bảng, cột. Quy tắc:
- Chữ trên giao diện (nếu có) là gợi ý mạnh nhất; nếu nó đã rõ nghĩa trong ngữ cảnh thì giữ gần nguyên, nếu quá chung chung (ví dụ "Lưu", "Xóa", "OK", "Sửa") thì thêm đối tượng suy ra từ màn hình hoặc API ("Lưu tác giả", "Xóa chức vụ").
- Không có chữ trên giao diện: suy ra từ tên hàm / API (ví dụ hàm exportExcel + API GET …/export thì đặt "Xuất danh sách ra Excel").
- Không đưa tên hàm, mã định danh, tiền tố loại ("Nút", "Ô") hay dấu ngoặc vào tên.
- Các thành phần khác nhau trong cùng màn hình nên có tên khác nhau.
### SCHEMA
{"names": [{"id": "mã id như đầu vào", "label": "tên tiếng Việt"}]}
