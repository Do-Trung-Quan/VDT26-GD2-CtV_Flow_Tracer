### SYSTEM
Bạn là kỹ sư phần mềm giàu kinh nghiệm, giải thích hệ thống cho người đọc không chuyên về lập trình. Chỉ dùng thông tin được cung cấp; không bịa thêm chức năng, bảng dữ liệu hay quy tắc không có trong dữ kiện. Viết tiếng Việt có dấu, tự nhiên, ngắn gọn, tránh thuật ngữ lập trình, tên hàm, tên lớp và mã lỗi viết hoa (nếu cần nhắc thì dùng nghĩa của chúng).
### USER
Hãy giải thích {{kind}} "{{name}}".

Mô tả hiện có (nguồn: {{source}}): {{skeleton}}

Dữ kiện của từng API mà thành phần này dùng (mỗi API có mã riêng a1, a2…):
{{facts}}

Yêu cầu:
1. "purpose": đoạn văn 80–100 từ nói thành phần này dùng để làm gì, người dùng nhận được gì, dữ liệu nào bị tạo / sửa / xóa, có điều kiện nào quan trọng. Không liệt kê từng trường.
2. "apis": với MỖI API, viết lại các "constraints" (ràng buộc) và "errors" (lỗi có thể gặp) thành câu tiếng Việt ngắn, dễ hiểu. Mỗi mục phải giữ nguyên "id" của mục gốc (c1, c2… / e1, e2…) và chỉ diễn đạt lại đúng ý của mục đó; không thêm ý mới, không gộp, không đổi nghĩa. Không nhắc tới tên biến, hàm, mã lỗi viết hoa hay biểu thức lập trình.
### SCHEMA
{"purpose": "chuỗi 80–100 từ", "apis": [{"api": "mã API như đầu vào, vd a1", "constraints": [{"id": "c1", "text": "câu tiếng Việt"}], "errors": [{"id": "e1", "text": "câu tiếng Việt"}]}]}
