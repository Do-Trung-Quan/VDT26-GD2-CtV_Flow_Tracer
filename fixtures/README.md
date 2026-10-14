# Fixtures

Dự án mẫu **tự viết 100%** để test và demo công khai. **TUYỆT ĐỐI không sao chép code công ty** (CLAUDE.md §3.5).

Sẽ bổ sung khi làm tới phase tương ứng (xem `.context/project_structure.md` §7):
- `spring-shop-api/` — Spring Boot nhỏ chứa các "ca khó" cho adapter Spring.
- `angular-mini/` — Angular nhỏ chứa các "ca khó" cho adapter Angular (Giai đoạn 4), xem `angular-mini/README.md`.
- `golden/` — snapshot IR để so sánh trong golden test.

Đã có:
- `detection/` — cây thư mục giả cho test nhận diện thư mục (Giai đoạn 1), xem `detection/README.md`.
- `spring-mini/` — dự án Spring Boot mini cho bộ phân tích endpoint/module (Giai đoạn 2), xem `spring-mini/README.md`.
- `spring-flow/` — dự án Spring Boot mini cho luồng xử lý Backend và dò vết (Giai đoạn 3), xem `spring-flow/README.md`.
- `golden/spring-flow.ir.json`, `golden/spring-flow.trace.*.json|puml` — snapshot IR và kết quả dò vết / mã PlantUML của `spring-flow`; sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update test`.
- `golden/angular-mini.ir.json` — snapshot IR của `angular-mini`; sinh lại bằng `UPDATE_GOLDEN=1 npm --prefix analyzer-node run test` (chỉ khi thay đổi IR có chủ đích).
- `golden/spring-mini.ir.json` — snapshot IR của `spring-mini`; sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update -pl flowtracer-adapter-spring test` (chỉ khi thay đổi IR có chủ đích).
- `ground-truth/spring-angular-mini.json` — ground truth **ví dụ** (tự viết) cho `spring-flow` + `angular-mini`, dùng thử `scripts/evaluate-accuracy.mjs` (định dạng: `scripts/README.md`). Ground truth của dự án thật đặt ngoài repo.
- `angular-ngmodule/` — Angular dùng NgModule: module con nạp sớm có `RouterModule.forChild` (route ở gốc) và module nạp lười (Giai đoạn 8), xem `angular-ngmodule/README.md`.
