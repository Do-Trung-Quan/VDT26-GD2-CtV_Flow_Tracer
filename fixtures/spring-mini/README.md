# spring-mini — mini fixture cho Giai đoạn 2

Dự án Spring Boot **tự viết** (miền "Shop"), không biên dịch, chỉ dùng để kiểm thử bộ phân tích. Golden snapshot:
`fixtures/golden/spring-mini.ir.json` (sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update -pl flowtracer-adapter-spring test`).

| File | Ca kiểm thử |
| :--- | :--- |
| `application.yml` | `server.servlet.context-path` khai báo lồng nhau → mọi endpoint có tiền tố `/shop-api` |
| `CategoryController` | `@Tag` → module "Danh mục"; đường dẫn cấp class là hằng số ghép (`ApiPaths.CATEGORIES`); `@Operation`; Javadoc; biến đường dẫn có regex; `ResponseEntity<…>`; `@RequestMapping` nhiều phương thức |
| `OrderController` | Không `@Tag` → module theo tên Controller ("Order"); `@RequestMapping` không khai phương thức → `ANY`; hằng số không giải được |
| `ProductController` | Sai cú pháp → file lỗi, không có endpoint, lần quét `PARTIAL` |
| `CategoryService` | Không phải Controller → không sinh endpoint |
