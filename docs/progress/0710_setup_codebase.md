# Báo cáo 07/10 — Dựng khung mã nguồn (setup codebase)

> **Mục tiêu:** tạo khung mã nguồn theo `.context/project_structure.md` và `.context/overview.md`, sẵn sàng cho bước chuẩn bị môi trường trước khi code chức năng.
> **Kết quả:** khung đã dựng xong cho cả 3 khối (Engine, Sidecar, Web) cùng phần hạ tầng. **Mọi phần đều build được và test đều đạt.** Chưa có chức năng nghiệp vụ nào được viết.
> **Chưa commit.** Toàn bộ thay đổi đang ở trạng thái chưa track trong Git (249 file).

---

## 1. Tóm tắt nhanh

| Khối | Đã làm | Kiểm chứng |
| :--- | :--- | :--- |
| `engine/` (Java) | Maven multi-module 4 module, Maven Wrapper; model IR đầy đủ; 12 entity khớp CSDL; migration Flyway V1; khung các package chức năng | `./mvnw verify` → **BUILD SUCCESS**, 14 test đạt (gồm test trên PostgreSQL thật) |
| `analyzer-node/` (sidecar) | CLI, đọc file an toàn, cache dữ kiện, khung adapter Angular, sinh kiểu dữ liệu từ `contracts/` | `build`, `test` (3 test), `lint` đều đạt |
| `web/` (Angular 21) | Khung app, 4 route với trang giữ chỗ, proxy `/api`, lint, script sinh kiểu dữ liệu API | `build`, `test` (1 test), `lint` đều đạt |
| `contracts/` | 3 JSON Schema + mô tả giao thức + 1 file ví dụ | Cả Java lẫn TypeScript đều đọc được file ví dụ |
| Hạ tầng | `.gitignore`, `.env.example`, `.editorconfig`, `.dockerignore`, Docker, CI, README | Docker **chưa build thử** (xem mục 4) |

---

## 2. Đã làm gì (giải thích dễ hiểu)

### 2.1. `engine/` — Analysis Engine

Chia thành **4 module** như tài liệu:

1. **`flowtracer-ir`** — "ngôn ngữ chung" của cả hệ thống. Đây là phần **model** được định nghĩa đầy đủ:
   - Các loại thành phần (`NodeKind`), loại quan hệ (`EdgeKind`), mức tin cậy (`Confidence`), vai trò Source.
   - `IrNode`, `IrEdge`, `IrFragment` (kết quả phân tích một Source), `Trace` (kết quả dò vết), `ScenarioStep` (một bước kịch bản).
   - `NodeKeys`: quy tắc tạo khóa ổn định cho từng thành phần.
   - `StackAdapter`: hợp đồng chung mà mọi bộ phân tích phải tuân theo.
   - Model **tự kiểm tra quy tắc cốt lõi**. Ví dụ: một quan hệ phải có vị trí trong code hoặc lý do, nếu không thì báo lỗi ngay khi tạo.
2. **`flowtracer-core`** — thuật toán lõi (Linker, Tracer, diagram, kịch bản, dữ liệu mẫu). Hiện **chỉ có chữ ký hàm**, thân hàm là `TODO`.
3. **`flowtracer-adapter-spring`** — bộ phân tích Java/Spring.
   - Model dữ kiện `JavaFileFacts` được định nghĩa đầy đủ.
   - Các bộ trích xuất và bộ phân giải chỉ có chữ ký hàm, thân hàm là `TODO`.
4. **`flowtracer-app`** — ứng dụng Spring Boot:
   - **12 entity** khớp đúng 12 bảng trong `database.md`, kèm enum và repository.
   - **Migration `V1__init_schema.sql`** tạo toàn bộ bảng, enum, khóa ngoại và chỉ mục.
   - Cấu hình (`application.yml` đọc từ biến môi trường), xử lý lỗi chung, khung SSE, `SourceReader` (điểm duy nhất được đọc mã nguồn người dùng), interface `LlmProvider` (hiện chỉ có chế độ "tắt AI").
   - Các package chưa có class (tracing, explanation, comparison…) có file `package-info.java` ghi rõ nhiệm vụ và các class sẽ bổ sung.

> **Cập nhật cùng ngày (cấu trúc package):** ban đầu các file Controller/Service/Repository để lẻ ngay trong thư mục chức năng. Đã đổi sang bố cục chuẩn doanh nghiệp: mỗi chức năng có `controller/` · `service/` · `repository/` · `entity/` · `dto/` (+ `pipeline/`, `adapter/`, `provider/`, `model/` khi đặc thù). Repository và `AiPurpose` đã được chuyển vào đúng thư mục; các thư mục còn trống có `package-info.java` mô tả class sẽ nằm ở đó (không tạo class rỗng). `ArchitectureTest` có thêm 3 luật theo tầng (9 luật tổng cộng): Controller không gọi thẳng Repository, Repository không gọi Service/Controller, Entity không phụ thuộc Service/Controller/DTO.

### 2.2. `analyzer-node/` — sidecar phân tích Angular

- `src/cli.ts`: nhận yêu cầu qua stdin, trả kết quả qua stdout, trả mã thoát đúng theo `contracts/sidecar-protocol.md`.
- `src/runtime/`: đọc file an toàn (chỉ file được cấp, chặn đường dẫn ra ngoài), cache dữ kiện theo mã băm, khung pipeline 2 pha.
- `src/adapters/angular/`: model dữ kiện `AngularFileFacts` đầy đủ. Pha 1 và pha 2 là `TODO`.
- Kiểu TypeScript được **sinh tự động** từ `contracts/` mỗi khi build hoặc test, không viết tay.

### 2.3. `web/` — giao diện Angular 21

- Khung do Angular CLI tạo, sau đó thay trang chào mặc định bằng thanh tiêu đề và 4 route: Danh sách Workspace · Màn hình chính 3 cột · Cài đặt Workspace · Cấu hình AI. **Mỗi trang hiện chỉ ghi "Đang xây dựng".**
- Thêm proxy `/api` → Engine khi dev, lint (angular-eslint), và script `gen:api` sinh kiểu dữ liệu từ OpenAPI của Engine.

### 2.4. `contracts/` — hợp đồng dữ liệu Engine ↔ sidecar

- JSON Schema cho kết quả phân tích, yêu cầu gửi sidecar và sự kiện tiến độ.
- `sidecar-protocol.md`: cách gọi CLI, mã thoát, ràng buộc an toàn.
- Một file ví dụ, dùng để kiểm tra rằng Java và TypeScript hiểu dữ liệu giống nhau.

### 2.5. Hạ tầng

- **`.gitignore`:** bỏ qua `node_modules/`, `target/`, `dist/`, `.angular/`, `.env`, `.data/`, kiểu dữ liệu sinh tự động, `docs/AI_Viettel/`.
- **`.env.example`:** mẫu biến môi trường. Mặc định `LLM_PROVIDER=none`, nên tool chạy được mà không cần API key.
- **`docker/`:** `Dockerfile` (build web, sidecar, engine thành một image) và `docker-compose.yml` (app + PostgreSQL, chỉ mở cổng cho máy cục bộ, thư mục mã nguồn gắn chế độ chỉ đọc).
- **`.github/workflows/ci.yml`:** build và test cả 3 khối cho mỗi lần push hoặc PR.
- **`fixtures/`, `scripts/`:** mới có README ghi những gì sẽ bổ sung. Chưa có code.

### 2.6. Test đi kèm khung

Các test này chỉ nhằm chứng minh **khung đúng**, không test chức năng:

| Test | Kiểm tra điều gì |
| :--- | :--- |
| `IrModelTest`, `DiscoveryTest`, `SpringStackAdapterTest` | Model IR tự kiểm tra quy tắc; lọc file và mã băm chạy đúng; adapter Spring khai báo đúng |
| `ArchitectureTest` (6 luật ArchUnit) | Tự động chặn vi phạm ranh giới kiến trúc. Ví dụ: lõi phân tích không được dùng Spring; chỉ package `ai` được gọi AI; chỉ `SourceReader` được đọc file |
| `SchemaMappingIT` | Chạy migration trên **PostgreSQL thật** (Docker), kiểm tra entity khớp bảng, đọc/ghi được cột JSON và enum |
| `ContractExampleTest` (Java) + `contract.spec.ts` (TS) | File ví dụ trong `contracts/` hợp lệ với cả hai phía |

---

## 3. Những thay đổi so với tài liệu (đã cập nhật tài liệu)

| Thay đổi | Lý do | Tài liệu đã sửa |
| :--- | :--- | :--- |
| **Spring Boot 3.x → 4.1.1**, Spring AI 2.0.1, springdoc 3.1.1 | Spring Boot 3.x đã hết hỗ trợ miễn phí (06/2026). Dự án mới nên dùng bản ổn định hiện hành | `overview.md` §2.1.3, `CLAUDE.md`, `project_structure.md` |
| Thêm interface `SourceContentLoader` trong `flowtracer-ir` | Adapter cần đọc file cấu hình (pom.xml, proxy.conf.json…) nhưng **không được tự đọc file**. App cung cấp nội dung qua interface này | `project_structure.md` §4.1 |
| Cột `ir_nodes.search_text` là **cột tự tính** trong PostgreSQL | Không cần ứng dụng tự cập nhật chỉ mục tìm kiếm | (chi tiết hiện thực, chỉ ghi chú trong file migration) |

Phần **đối tượng được phân tích** vẫn là Spring Boot 3.x như cũ. Chỉ Engine của tool là dùng 4.x.

---

## 4. Những chỗ cần lưu ý

1. **Phần đã viết sẵn logic (vượt mức khung).** Trước khi bạn nhắc, mình đã viết sẵn một số đoạn ngắn. Mình **giữ lại** vì chúng đã có test và đúng thiết kế:
   - lọc file theo glob và tính mã băm (`FileFilter`, `ContentHash`);
   - chỉ mục đồ thị (`GraphIndex`), chọn adapter (`StackDetector`), chặn vòng lặp (`CycleGuard`), sinh `trace_id`;
   - `LocalSourceReader` (chặn đường dẫn ra ngoài), xử lý lỗi chung, `SseHub`;
   - phía sidecar: đọc file an toàn, cache dữ kiện, pipeline 2 pha.
   Nếu bạn muốn khung "trắng" hoàn toàn, có thể xóa thân các hàm này.
2. **npm 10.9.2 trên máy bị lỗi** khi cài thư viện cho `web/` (lỗi nội bộ `Cannot read properties of null (reading 'edgesOut')`). Mình đã cài bằng `npx npm@11 install` mà không đổi npm toàn cục. Khuyến nghị cập nhật npm lên 11.
3. **CoDev CLI cần Node ≥ 22.21**, máy đang chạy 22.14 (không ảnh hưởng tool, chỉ lưu ý nếu dùng CoDev).
4. **Docker:**
   - Test `SchemaMappingIT` cần Docker Desktop đang chạy. Nếu Docker tắt, test **tự bỏ qua** chứ không báo lỗi.
   - Mình đã bật Docker Desktop để chạy test này. Lần sau mình sẽ hỏi trước.
   - **Image Docker của tool chưa được build thử**, vì việc tải image khá lâu và chưa cần ở giai đoạn khung. Nên build thử ở phase đóng gói.
5. **AI đang ở chế độ tắt:** `LlmProviderConfig` luôn trả về provider "none" và in cảnh báo nếu cấu hình provider khác. Phần gọi Gemini/Claude làm ở phase AI.
6. **Máy dev dùng JDK 22**, project biên dịch ở chuẩn Java 21 (`release 21`), nên vẫn chạy đúng trên JDK 21 trong Docker và CI.
7. **Các lệnh trong `CLAUDE.md` §2.4** (`scripts/*.mjs`) **chưa tồn tại**: `scripts/` hiện chỉ có README. Sẽ tạo khi tới phase cần.
8. **Lưu ý bảo mật:** `fixtures/` hiện trống. Khi viết fixture phải **tự viết**, không sao chép code dự án công ty.

---

## 5. Cách tự kiểm tra lại

```bash
# Engine (bật Docker Desktop trước nếu muốn chạy test CSDL)
cd engine && ./mvnw verify            # Windows: mvnw.cmd verify

# Sidecar
cd analyzer-node && npm ci && npm run lint && npm test && npm run build

# Web
cd web && npx npm@11 ci && npm run lint && npm test && npm run build
```

## 6. Bước tiếp theo đề xuất

1. Bạn review khung, rồi commit (mình chưa commit).
2. Viết `roadmap.md` để chia phase. Từ đó, mỗi phase có một nhật ký trong `docs/progress/`.
3. Bắt đầu phase đầu tiên theo roadmap. Gợi ý: khai báo Workspace/Source và quét Backend Spring.

---

## 7. Bổ sung: bản vẽ giao diện offline (cùng ngày)

- **Đã làm:** tạo `docs/UI/UI_offline.html` — một file HTML tự chứa (không tải gì từ mạng), mở trực tiếp bằng trình duyệt, dùng dữ liệu mẫu miền "Shop". Gồm: danh sách Workspace, import 3 bước (tự nhận diện vai trò, tự tách monorepo), trình duyệt thư mục, tiến độ quét, màn chính 3 cột (cây · sequence diagram · Diễn giải/Kịch bản), liên kết chốt tay / nhờ AI chọn, xuất Mermaid/SVG/PNG, Ctrl+K, Cài đặt Workspace (7 tab), Cấu hình AI, nhật ký API giả lập (kèm mã API A1…I7) và công cụ review (chú thích sự kiện, giả lập AI bật/tắt/lỗi, Backend chậm, Workspace rỗng). Phạm vi: MVP + Nên có.
- **Bản dữ liệu demo cục bộ:** `docs/UI/UI_offline.local.html` (đã thêm `docs/UI/*.local.html` vào `.gitignore`, **không commit**). Cùng khung giao diện, chỉ thay tên theo cấu trúc mã nguồn demo.
- **Kiểm tra:** cú pháp script đạt `node --check`; chạy thử bằng Chrome không giao diện qua kịch bản tự động (import, quét, 4 luồng mẫu, chốt tay, AI chọn, các tab cài đặt, trang AI): không có lỗi JavaScript.
- **Còn dở:** chưa có bản review bằng mắt của người dùng; nếu đổi bố cục thì sửa tại file này rồi tạo lại bản cục bộ.

---

> Các điều chỉnh từ ngày 08/10 và 09/10 nằm ở [0810_ui_review_va_chot_thiet_ke.md](0810_ui_review_va_chot_thiet_ke.md) và [0910_roadmap_va_so_sanh_cu_moi.md](0910_roadmap_va_so_sanh_cu_moi.md).
