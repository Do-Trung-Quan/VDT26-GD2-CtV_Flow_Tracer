# Prompt AI

Mỗi mục đích một file `<purpose>.v<n>.st` gồm 3 phần `### SYSTEM`, `### USER`, `### SCHEMA`; chỗ trống `{{tên}}` được điền bằng dữ kiện
(`PromptTemplates`). Đổi nội dung prompt thì **tăng version** (version nằm trong khóa cache). Danh sách mục đích được phép: overview §2.7
và enum `AiPurpose`.

| File | Mục đích |
| :--- | :--- |
| `purpose-text.v1.st` | Mục đích 80–100 từ + diễn đạt lại ràng buộc và "Lỗi có thể gặp" (giữ id của mục thô) |
| `scenario-polish.v1.st` | Lời kể từng bước, giữ nguyên số bước / thứ tự / thành phần |
| `mock-data.v1.st` | Dữ liệu mẫu thỏa JSON Schema của DTO |
| `edge-resolution.v2.st` | Giải thích từng ứng viên của một liên kết chưa chắc chắn dựa trên chuỗi gọi từ thao tác của người dùng (không đổi liên kết); v2: thêm chuỗi gọi, từ khớp tên và bằng chứng |
| `element-naming.v1.st` | Đặt tên tiếng Việt chuẩn cho phần tử giao diện của một màn hình / hộp thoại |

Lưu ý: không dùng tên như `apiKey`, `token`, `password` làm nhãn trong prompt — `SecretRedactor` sẽ che giá trị đi sau chúng. Dùng mã ngắn (`a1`, `c1`, `e1`).
