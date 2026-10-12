# spring-mini — mini fixture cho Giai đoạn 2

Dự án Spring Boot **tự viết** (miền "Shop"), không biên dịch, chỉ dùng để kiểm thử bộ phân tích. Golden snapshot:
`fixtures/golden/spring-mini.ir.json` (sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update -pl flowtracer-adapter-spring test`).

| File | Ca kiểm thử |
| :--- | :--- |
| `application.yml` | `server.servlet.context-path` khai báo lồng nhau → mọi endpoint có tiền tố `/shop-api` |
| `CategoryController` | `@Tag` → module "Danh mục"; đường dẫn cấp class là hằng số ghép (`ApiPaths.CATEGORIES`); `@Operation`; Javadoc; biến đường dẫn có regex; `ResponseEntity<…>`; `@RequestMapping` nhiều phương thức |
| `OrderController` | Không `@Tag` → module theo tên Controller ("Order"); `@RequestMapping` không khai phương thức → `ANY`; hằng số không giải được; thuộc tính cấu hình trong đường dẫn: `export-path` (cấu hình thắng mặc định trong code), `archive-path` (biến môi trường có mặc định), `missing-path` (vắng mặt), `env-path` (biến môi trường không mặc định), `api-token` (khóa nhạy cảm, không đọc), `token-path` (có đoạn "token" nhưng là khóa đường dẫn, giá trị bắt đầu bằng "/" nên đọc), `profile-path` (chỉ có trong `application-dev.yml`) |
| `ProductController` | Sai cú pháp → file lỗi, không có endpoint, lần quét `PARTIAL` |
| `CategoryService` | Không phải Controller → không sinh endpoint |
