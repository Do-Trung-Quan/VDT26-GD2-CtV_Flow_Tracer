# spring-flow — mini fixture cho Giai đoạn 3

Dự án Spring Boot **tự viết** (miền "Shop"), không biên dịch, chỉ dùng để kiểm thử phần dựng luồng xử lý Backend và dò vết.
Golden snapshot: `fixtures/golden/spring-flow.ir.json`; golden trace: `fixtures/golden/spring-flow.trace.*.json` và `*.puml`
(sinh lại bằng `./mvnw -f engine/pom.xml -Pgolden-update test` — chỉ khi thay đổi IR/trace có chủ đích).

| Luồng | Ca kiểm thử |
| :--- | :--- |
| `POST /api/v1/categories` | Controller (Lombok `@RequiredArgsConstructor`) → `CategoryService` (interface, 1 lớp hiện thực → `CERTAIN`) → hàm private gom thành bước tự xử lý → `if … throw` thành khối `alt` → repository Spring Data: `existsByCode` (suy ra từ tên), `save` (kế thừa) → bảng `category` (`@Table`) |
| `GET /api/v1/categories` | `@Query` JPQL; `map(this::toResponse)` (tham chiếu hàm); `ApiResponse.ok` bị lọc nhiễu |
| `DELETE /api/v1/categories/{id}` | `ProductQueryService` có 2 lớp hiện thực, không `@Primary` → cạnh `UNRESOLVED` có 2 ứng viên (một lớp có `@ConditionalOnProperty`); `markDeleted()` là hàm của entity; hai khối `alt` nối tiếp |
| `GET /api/v1/products/options` | Service có hàm `getOptions()` (tên giống getter nhưng là nghiệp vụ) — không bị bộ lọc nhiễu bỏ nhầm |
| `GET /api/v1/products` | `ProductSearchDao` ghép JPQL bằng `StringBuilder` + `EntityManager.createQuery` → truy vấn `DYNAMIC`; mảnh có điều kiện thêm bảng `product_brand` (entity không khai `@Table` → tên suy ra theo quy ước) |
| `PUT /api/v1/orders/{id}/confirm` | `NotificationSender` có `@Primary` → `INFERRED`; `@Qualifier("sms")` → `CERTAIN`; `@Table(name = Tables.ORDER)` (hằng số) → bảng `shop_order`; `CrudRepository`; `var order = repo.findById(id).orElseThrow()` rồi `order.isPending()` (kiểu giải theo kiểu trả về khai báo qua `Optional`); `auditService.record(id)` — hàm kế thừa từ lớp cơ sở trong thư viện nên không tìm thấy → `UNRESOLVED` không có ứng viên |; gọi HTTP sang service khác (Giai đoạn 5): `CatalogGateway` dùng `WebClient.get().uri("/api/v1/products/options")` (nối Backend → Backend tới API cùng Workspace), `InventoryClient` là `@FeignClient(name = "inventory-service", path = …)` (không có trong Workspace → hệ thống ngoài `inventory-service`), `PaymentGateway` dùng `RestTemplate.postForObject(PAYMENT_URL + "/charges")` với URL tuyệt đối (→ hệ thống ngoài `payment.example.com`) |

`src/main/resources/openapi.yaml` là đặc tả OpenAPI mẫu (Giai đoạn 6, đối chiếu C6): khớp 3 API của module danh mục, thêm `GET /api/v1/categories/export` mà code không có (→ "chỉ có trong đặc tả"), và thiếu mọi API sản phẩm / đơn hàng (→ "chỉ có trong code"). `messages_vi.properties` có thông báo của các lệnh `throw`; `CategoryRequest` có Bean Validation, `CategoryController.create` có `@PreAuthorize`, cột `code` của entity `Category` là `unique`.
