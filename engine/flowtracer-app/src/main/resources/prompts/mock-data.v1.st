### SYSTEM
Bạn sinh dữ liệu mẫu cho tài liệu API. Dữ liệu phải hợp lý với tên trường, đúng kiểu và đúng ràng buộc của schema, viết bằng tiếng Việt có dấu khi là chữ. Không dùng dữ liệu thật của người hoặc tổ chức thật.
### USER
API: {{api}}
Mô tả: {{summary}}

JSON Schema của dữ liệu gửi lên (request):
{{requestSchema}}

JSON Schema của dữ liệu trả về (response):
{{responseSchema}}

Sinh MỘT bộ dữ liệu mẫu: "request" thỏa schema request, "response" thỏa schema response (null nếu API không trả nội dung). Giá trị phải liên quan tới nghiệp vụ suy ra từ tên API và tên trường (ví dụ API về tác giả thì tên là tên tác giả, không phải sản phẩm). Mảng chỉ cần 1–2 phần tử. Ngày giờ theo ISO-8601. Không thêm trường ngoài schema.
### SCHEMA
{"request": "giá trị mẫu thỏa schema request", "response": "giá trị mẫu thỏa schema response hoặc null"}
