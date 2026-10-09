# Fixtures

Dự án mẫu **tự viết 100%** để test và demo công khai. **TUYỆT ĐỐI không sao chép code công ty** (CLAUDE.md §3.5).

Sẽ bổ sung khi làm tới phase tương ứng (xem `.context/project_structure.md` §7):
- `spring-shop-api/` — Spring Boot nhỏ chứa các "ca khó" cho adapter Spring.
- `angular-shop-web/` — Angular nhỏ chứa các "ca khó" cho adapter Angular.
- `golden/` — snapshot IR để so sánh trong golden test.

Đã có:
- `detection/` — cây thư mục giả cho test nhận diện thư mục (Giai đoạn 1), xem `detection/README.md`.
- `spring-mini/` — dự án Spring Boot mini cho bộ phân tích endpoint/module (Giai đoạn 2), xem `spring-mini/README.md`.
- `golden/spring-mini.ir.json` — snapshot IR của `spring-mini`; sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update -pl flowtracer-adapter-spring test` (chỉ khi thay đổi IR có chủ đích).
