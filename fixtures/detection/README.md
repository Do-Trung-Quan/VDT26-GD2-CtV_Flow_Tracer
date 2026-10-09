# Mini fixture — nhận diện thư mục (Giai đoạn 1)

Cây thư mục tự viết, dùng cho `WorkspaceApiIT` (API A6, A10, A2, A7–A9). Chỉ có file đặc trưng, không phải dự án chạy được.

| Thư mục | Ca kiểm thử | Kết quả mong đợi |
| :--- | :--- | :--- |
| `shop-monorepo/` | Chứa cả Backend và Frontend | `VALID`, 2 gốc: `backend` (Spring Boot 3.3.3), `web` (Angular 21) |
| `shop-monorepo/backend/src/main/java` | Thư mục con trong dự án Backend | `INSIDE_PROJECT`, gợi ý `shop-monorepo/backend` |
| `shop-fullstack/` | Frontend nằm trong repo Backend | `VALID`, 2 gốc lồng nhau; Source Backend tự loại `frontend/**` |
| `unrelated/` | Thư mục không liên quan | `NOT_FOUND` |
