# Giai đoạn 8 (Task 8.1) + Giai đoạn 11 (Task 11.1) — Đo lường và đóng gói

> Ngày 14/10. Theo thống nhất với người dùng: làm **8.1 (script đo)** → **11.1 (Docker)** → rồi mới **8.2 (tối ưu / sửa ca sai)**. 8.2 chưa làm; danh sách việc nằm ở `docs/evaluation.md`.

---

## 1. Task 8.1 — Script đo và ground truth

### 1.1. Việc đã làm

| Hạng mục | Nội dung |
| :--- | :--- |
| `scripts/benchmark.mjs` | Đo NFR-01 → NFR-04 và NFR-06: quét lần đầu, quét lại (không đổi / **20 file đổi**), tải cây Backend / Frontend (5 lần, p95), tìm kiếm, dò vết (30 API + 15 phần tử giao diện, tách luồng ≤ 50 / > 50 bước), RAM đỉnh của Engine, thời gian từng giai đoạn quét. In bảng Markdown chỉ gồm số liệu tổng hợp. |
| Quét lại với 20 file đổi | Script **không sửa mã nguồn gốc**: sao chép Source vào thư mục tạm `<SOURCE_ROOT>/ft-bench-<mã>/` (bỏ `node_modules`, `.git`, `target`, `dist`…), sửa 20 file trong bản sao, đo, rồi xóa bản sao. `--no-copy` để tắt. |
| `scripts/evaluate-accuracy.mjs` | NFR-07 (API C6 so với OpenAPI), NFR-08 (precision / recall liên kết FE → BE theo cặp phần tử–API, qua F1), NFR-09 (luồng đúng tới bảng và không bước `UNRESOLVED`). Chỉ in số liệu tổng hợp; `--verbose` chỉ in số thứ tự mục sai. |
| `scripts/lib/engine-client.mjs` | Phần dùng chung: gọi API, chờ quét, thống kê, đọc RAM (Windows / Linux), chuẩn hóa tên để so khớp. |
| Ground truth ví dụ | `fixtures/ground-truth/spring-angular-mini.json` (tự viết) — để thử script; ground truth của demo đặt ngoài repo. Định dạng: `scripts/README.md`. |
| `docs/evaluation.md` | Số liệu đo (hiệu năng trên demo và fixture), nguyên nhân chưa đạt và hướng xử lý. |

### 1.2. Kết quả (chi tiết trong `docs/evaluation.md`)

- Demo (1.404 file): quét lần đầu **35–43 s** ✅, RAM đỉnh **690–810 MB** ✅, tải cây / tìm kiếm / dò vết **đều < 0,4 s** ✅, quét lại không đổi 8,7–10,5 s ✅ (sát ngưỡng).
- **Chưa đạt:** quét lại **20 file đổi = 27–42 s** (mục tiêu ≤ 15 s). Nguyên nhân: RESOLVE + PERSIST + LINK chạy lại trên toàn Workspace (~14 s), cộng DISCOVER (~3,5 s) và EXTRACT (~9 s, có chi phí khởi động sidecar). Đây là việc đầu tiên của Task 8.2.
- Quét lại không file nào đổi vẫn chạy LINK (~4,7 s) — nên bỏ qua.
- Fixture: mọi chỉ tiêu hiệu năng đạt; script độ chính xác cho NFR-08 = 100% / 100%, NFR-09 = 4/7 (3 luồng có cạnh `UNRESOLVED` là cố ý của fixture), NFR-07 = 75% (cố ý).

### 1.3. Việc còn dở của 8.1

- **Độ chính xác trên demo (NFR-07 → NFR-09) chưa đo**: cần file ground truth 30 luồng (đặt ngoài repo) do người dùng lập theo định dạng ở `scripts/README.md`.
- **Kiểm tra không gắn cứng vào demo** (chạy trên 1–2 dự án Spring Boot + Angular mã nguồn mở): chưa làm — cần tải mã nguồn công khai về máy, chờ người dùng đồng ý.

---

## 2. Task 11.1 — Image Docker và Compose

### 2.1. Việc đã làm

| Hạng mục | Nội dung |
| :--- | :--- |
| `docker/Dockerfile` | 4 stage: web (Angular build) → sidecar (npm ci + build + prune) → engine (JDK 21, web build đưa vào `static/`, `mvnw package`) → image chạy JRE 21 + Node 22, người dùng không phải root. Có `HEALTHCHECK` gọi `/api/health` bằng chính Node (JRE không có curl). `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`. |
| `docker/docker-compose.yml` | `postgres` (volume `pgdata`, healthcheck), `flowtracer-app` (`depends_on` postgres healthy, `restart: unless-stopped`, chỉ publish `127.0.0.1:${APP_PORT:-8600}`, `SOURCE_ROOT` gắn `:ro`, volume `/data`). Flyway tự chạy khi Engine khởi động. |
| `.dockerignore` | Thêm `.env`, `.env.*` (trừ `.env.example`) để khóa API không vào ngữ cảnh build. |
| SPA fallback (BE) | `config/SpaFallbackResolver` + `WebConfig.addResourceHandlers`: tải lại trang hoặc mở thẳng `/w/<id>`, `/settings/ai` trả `index.html` (trước đó 404 vì Engine chưa có fallback). `api/`, `v3/` và đường dẫn có đuôi file mà thiếu thì vẫn 404. Test: `SpaFallbackResolverTest`. |

### 2.2. Task 11.2 (phần làm được không cần máy khác)

- `README.md`: cài đặt bằng Docker, ý nghĩa `SOURCE_ROOT` (chỉ đọc), cấu hình AI (bảng biến môi trường, hành vi khi 429 / 503), hướng dẫn sử dụng, đo lường.
- `.github/workflows/ci.yml` thêm 3 job: `scripts` (biên dịch thử script đo), `forbidden-terms` (đọc danh sách từ khóa từ secret `FORBIDDEN_TERMS`; `check-forbidden-terms.mjs` nay nhận biến môi trường này; không có secret thì chỉ cảnh báo), `docker-image` (build image + khởi động cùng PostgreSQL và kiểm tra `/api/health`). **Chưa chạy được ở CI thật** (cần đẩy lên GitHub) — người dùng cần tạo secret `FORBIDDEN_TERMS` nếu muốn bật.

### 2.3. Kiểm chứng Task 11.1 (project compose riêng `flowtracer-verify`, PostgreSQL cổng 5433, mount `fixtures/` chỉ đọc; đã `down -v --rmi local` sau khi xong, không đụng CSDL dev)

| Kiểm tra | Kết quả |
| :--- | :--- |
| `docker compose up -d --build` từ đầu | Image 997 MB; cả hai container **healthy** (Engine sau ~1 phút) |
| Cổng | `127.0.0.1:8600` và `127.0.0.1:5433`; truy cập qua IP mạng của máy → từ chối kết nối |
| Web | `/` → 200 HTML; `/w/<id>`, `/settings/ai` (mở thẳng / tải lại) → 200 `index.html`; `/chunk-xxx.js` thiếu, `/api/xxx` → 404; `/v3/api-docs` → 200 |
| Mã nguồn chỉ đọc | `touch /sources/x` → "Read-only file system"; `/data` ghi được; tiến trình chạy bằng user `flowtracer` (không root) |
| Quét end-to-end trong container | import `spring-flow` + `angular-mini` → `SUCCEEDED`, 64 file, 6 API, 3 màn hình, 26 phần tử, 0 lỗi chẩn đoán (**sidecar Node Angular chạy được trong image**) |
| Script đo trên container | `evaluate-accuracy.mjs` ra đúng số như chạy trên máy (NFR-08 100% / 100%); `benchmark.mjs --workspace` chạy được |
| AI mặc định | `provider = none`, trạng thái OFF — mọi tính năng chạy được |

Engine `clean install`: **249 test** qua (thêm `SpaFallbackResolverTest` 4).

## 3. Kiểm tra trên dự án mã nguồn mở và Task 8.2

### 3.1. `spring-petclinic-rest` + `spring-petclinic-angular` (tải về `D:\oss-check\`, ngoài repo; 198 file)

Kết quả ban đầu rất thấp (tool "gắn cứng" theo kiểu dự án demo): 1 API (đặc tả có 37 → NFR-07 = 0%), 3 màn hình, 0 phần tử. Hai nguyên nhân:
- **Angular NgModule nạp sớm** — `AppModule` import `OwnersModule` → `RouterModule.forChild`. **Đã sửa** (8.2): sidecar gom route `forChild` của NgModule được import trực tiếp ở gốc (module nạp lười qua `loadChildren` vẫn nằm dưới tiền tố, không bị thêm trùng). Sau sửa: **23 màn hình, 96 phần tử, 26 phần tử gọi API**. Có fixture tự viết `fixtures/angular-ngmodule/` và test `ngmodule.spec.ts` (đã xác nhận test **fail khi bỏ bản sửa**).
- **Backend OpenAPI-first** — Controller `implements XxxApi`, các interface `XxxApi` mang `@RequestMapping` được **sinh lúc build** từ `openapi.yml` nên không có trong mã nguồn. **Chưa xử lý**: cần dựng endpoint từ file `openapi.yml` mà `pom.xml` trỏ tới — tính năng mới ngoài tài liệu đã chốt, chờ người dùng quyết định.

### 3.2. Task 8.2 — tối ưu quét lại

Xem `docs/evaluation.md` §1.1: NFR-02 (quét lại 20 file đổi) từ **27–42 s xuống 10,4 s** (đạt ≤ 15 s). Đã đo bằng log thời gian từng giai đoạn trước khi sửa; thử `reWriteBatchedInserts` của PostgreSQL JDBC không cải thiện nên đã gỡ.

### 3.3. Prompt lập ground truth

`scripts/ground-truth-prompt.md`: prompt tự chứa cho agent ở dự án demo (định dạng, cơ cấu 30 luồng, quy tắc đặt tên màn hình / phần tử / API / bảng, tự kiểm tra, đầu ra chỉ số liệu tổng hợp).

## 3b. Đo thật trên dự án demo (ground truth 30 luồng) và trong Docker

Chi tiết số liệu: `docs/evaluation.md` §2.1 và §4. Tóm tắt:

- **Độ chính xác** (mẫu 30 luồng, ground truth ngoài repo): NFR-08 precision **100%**, recall **96,6%** trên phần tử xác định được (84,8% nếu tính nghiêm ngặt, sát ngưỡng 85%); NFR-09 **96,7%** (29/30). NFR-07 chưa đo được (demo không có file OpenAPI; cần file `/v3/api-docs` xuất từ Backend đang chạy).
- **Sửa lỗi thật của tool tìm ra nhờ ground truth:** `throw` trong lambda `ifPresent` bị coi là kết thúc vô điều kiện (mất các bước sau); hàm nạp chồng cùng số tham số chưa chọn theo kiểu đối số; nhãn nút dạng biểu thức điều kiện giữa hai khóa dịch. Có test cho cả ba (`GuardResolverTest`, `OverloadRulesTest`, `extract.spec.ts`). Nâng phiên bản bộ phân tích (Java 9→10, Angular 3→4) nên "Quét lại" tự phân tích lại toàn bộ — đã kiểm chứng.
- **Script đo:** ghép tên phần tử tốt hơn và báo cáo tách "xác định được / mơ hồ / không có trong cây"; thêm `--diagnose` (gỡ lỗi cục bộ), `--container` (RAM của container), `--exclude`.
- **Docker trên Windows:** quét lại chậm (~52 s) vì tốc độ bind-mount, không phải do mã Engine (đo bằng `find`). Loại thư mục ~31.000 file không phân tích ở Source: quét lại 7,7 s / 10,8 s (20 file đổi), quét đầu 23,7 s, RAM 1,08 GB — **mọi NFR-01 → 06 đạt trong container**. Đã ghi vào README.
- Dockerfile: bước `mvnw` thử lại tối đa 3 lần (mạng tải thư viện thỉnh thoảng đứt giữa chừng — gặp 2 lần trong buổi đo).

## 4. Việc còn dở / vấn đề gặp phải

- NFR-07 trên demo chưa đo (cần file OpenAPI xuất từ Backend đang chạy). NFR-08 / 09 đã đo (mục 3b).
- Backend OpenAPI-first chưa hỗ trợ (xem 3.1) — cần người dùng quyết định.
- Phần tử ô nhập không có nhãn hiển thị trong petclinic (61/96) vì nhãn nằm ở thẻ `<label for=…>` — tool hiện tên theo `name` / `formControl`; có thể liên kết `label for` → ô nhập nếu muốn (chưa làm).
- Chưa chạy `docker compose up` trên **máy sạch khác / Linux** và chưa chạy CI thật (tiêu chí DoD 1 và 6 của Task 11.2). Ảnh chụp màn hình cho README chưa làm.
- **Bind-mount ổ Windows chậm**: cùng 64 file fixture, DISCOVER của lần quét lại mất ~3,1 s trong container so với ~0,3 s khi Engine chạy trực tiếp. Số đo hiệu năng ở `docs/evaluation.md` là khi Engine chạy trực tiếp; cần đo lại trong container với demo.
- Build Docker bị ngắt một lần khi phiên làm việc kết thúc (`rpc error … EOF` của buildkit) — chạy lại là xong, không phải lỗi Dockerfile.
- `target/classes` bị IDE biên dịch lại (lỗi `Unresolved compilation problems` / `Lookup method resolution failed` khi `spring-boot:run`) xuất hiện lại nhiều lần — luôn `clean install` ngay trước khi chạy.
- PostgreSQL dev (`flowtracer-postgres-1`) đã dừng sau khi Docker khởi động lại; `docker start flowtracer-postgres-1` là đủ, dữ liệu giữ nguyên.
