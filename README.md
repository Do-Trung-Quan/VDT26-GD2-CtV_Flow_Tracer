# CtV Flow Tracer

Công cụ web chạy cục bộ: phân tích **tĩnh** mã nguồn dự án (Spring Boot + Angular), dựng cây API Backend, cây thành phần Frontend và **sequence diagram End-to-End**, kèm diễn giải bằng lời và dữ liệu mẫu.

- Tài liệu đặc tả: [`.context/overview.md`](.context/overview.md)
- Quy định cho AI agent: [`CLAUDE.md`](CLAUDE.md)

## Cấu trúc

| Thư mục | Nội dung |
| :--- | :--- |
| `engine/` | Analysis Engine — Java 21, Spring Boot 4, Maven multi-module |
| `analyzer-node/` | Sidecar Node.js phân tích Angular |
| `web/` | Web UI — Angular 21 |
| `contracts/` | JSON Schema dùng chung Engine ↔ sidecar |
| `docker/` | Dockerfile + docker-compose |

## Chạy nhanh

Yêu cầu: Docker Desktop (hoặc Docker Engine + Compose v2). Không cần cài Java / Node trên máy.

```bash
cp .env.example .env          # Windows: copy .env.example .env
# mở .env, đặt SOURCE_ROOT là thư mục CHA chứa các dự án cần phân tích, vd D:/projects hoặc /home/me/projects
docker compose -f docker/docker-compose.yml --env-file .env up -d --build
docker compose -f docker/docker-compose.yml --env-file .env ps      # flowtracer-app: healthy
# mở http://127.0.0.1:8600
```

- Lần đầu build mất vài phút (tải Maven / npm). Dừng: `docker compose -f docker/docker-compose.yml --env-file .env down` (thêm `-v` để xóa cả CSDL của tool).
- **Cổng 8600 chỉ mở cho máy cục bộ** (`127.0.0.1`); máy khác trong mạng không truy cập được. Tool một người dùng, không đăng nhập.
- **Mã nguồn được gắn chỉ đọc** (`SOURCE_ROOT` → `/sources:ro`): tool không sửa, không build, không chạy dự án được phân tích. Khi tạo Workspace, nhập đường dẫn **tương đối** so với `SOURCE_ROOT` (hoặc dùng nút duyệt thư mục).
- Dữ liệu của tool nằm trong volume Docker (`pgdata`, `flowtracer-data`); xóa Workspace chỉ xóa dữ liệu trong CSDL, không đụng tới mã nguồn.
- Cổng PostgreSQL mặc định `5432` (đổi bằng `DB_PORT` trong `.env` nếu máy đã có PostgreSQL); cổng Engine đổi bằng `APP_PORT`.
- **Trên Windows, đọc mã nguồn qua Docker chậm hơn nhiều so với chạy trực tiếp** (bind-mount ổ đĩa: khoảng 0,6 ms mỗi file). Dự án có hàng chục nghìn file không phân tích (thư mục mẫu giao diện, ảnh, mã cũ…) có thể mất cả phút mỗi lần quét. Cách xử lý: ở **Cài đặt Workspace → Nguồn mã** khai báo thư mục đó vào **Loại trừ**; kết quả phân tích không đổi nhưng nhanh hơn nhiều lần. Dự án rất lớn: đặt mã nguồn trong hệ tệp WSL2 hoặc chạy Engine trực tiếp (xem `docs/evaluation.md` §4).

## Cấu hình AI (tùy chọn)

Mặc định `LLM_PROVIDER=none`: mọi tính năng chạy bằng nội dung tự sinh từ mã nguồn. Muốn AI viết mục đích, lời kịch bản, dữ liệu mẫu và đặt tên phần tử, đặt trong `.env`:

| Biến | Ý nghĩa |
| :--- | :--- |
| `LLM_PROVIDER` | `gemini` / `anthropic` / `openai-compatible` / `none` |
| `LLM_MODEL` | tên model (bỏ trống thì dùng mặc định của provider; bắt buộc với `openai-compatible`) |
| `GEMINI_API_KEY` / `ANTHROPIC_API_KEY` / `LLM_API_KEY` | khóa API — chỉ nằm trong biến môi trường, không lưu CSDL, không trả về giao diện, không ghi log |
| `LLM_BASE_URL` | địa chỉ API cho `openai-compatible` |
| `LLM_DAILY_TOKEN_LIMIT` | giới hạn token mỗi ngày do bạn tự đặt (mặc định 0 = không đặt; Gemini không công bố hạn mức token theo ngày cho gói miễn phí, chỉ hạn mức lượt gọi / phút / ngày) |
| `LLM_LOG_CONTENT` | mặc định `true`: ghi prompt (đã che secret) và kết quả của mọi lời gọi AI ra log của Engine (logger `flowtracer.ai`); `false` = chỉ ghi dòng tóm tắt |

**Hạn mức của nhà cung cấp:** gói **miễn phí** của Gemini hiện chỉ cho khoảng **20 lượt gọi mỗi ngày cho mỗi model** (số liệu đo ngày 14/10/2026, có thể đổi), mà một lượt tương ứng một lần bấm "Sinh bằng AI" / "Sinh lại" / "Đặt tên bằng AI" / "Nhờ AI phân tích" — **kể cả mỗi lần bấm "Kiểm tra kết nối"**. Hết hạn mức, Engine báo giờ được dùng lại và mọi tính năng chuyển sang nội dung tự sinh. Muốn demo trọn vẹn nên bật thanh toán cho khóa API (gói trả phí có cam kết không dùng dữ liệu để huấn luyện, đúng yêu cầu NFR-14) hoặc sinh trước nội dung cho các thành phần sẽ trình bày: nội dung AI đã sinh được lưu trong CSDL và mở lại không gọi AI nữa, miễn là mã nguồn / cấu hình liên quan không đổi.

Mã nguồn gửi cho AI luôn được che giá trị bí mật (mật khẩu, token, chuỗi kết nối). Dùng API chính thức có cam kết không huấn luyện bằng dữ liệu. Bật / tắt từng mục đích và kiểm tra kết nối ở trang **Cấu hình AI**. AI hết hạn mức (429) hoặc quá tải (503) thì tool tự nghỉ, hiện banner và dùng nội dung tự sinh — nội dung AI đã lưu vẫn dùng bình thường.

## Hướng dẫn sử dụng

1. **Import dự án** (nút trên header): chọn thư mục mã nguồn; tool tự nhận diện Backend (Spring Boot) / Frontend (Angular), xác nhận rồi **Tạo & quét**.
2. **Cột trái**: tab Backend (cây module → API) và Frontend (ứng dụng → màn hình → phần tử). `Ctrl+K` tìm nhanh.
3. **Chọn một API hoặc phần tử** → giữa là sequence diagram End-to-End; bấm mũi tên "Suy luận" / "Chưa xác định" để xem ứng viên, **Chốt tay**, hoặc **Nhờ AI phân tích** (AI chỉ giải thích, không tự đổi liên kết). Nút **Xuất** tải PlantUML / SVG / PNG.
4. **Cột phải**: tab **Diễn giải** (mục đích, đầu vào / đầu ra, ràng buộc, dữ liệu mẫu, được gọi từ / gọi tới), tab **Kịch bản** (từng bước khớp mũi tên), nút **Xem code**.
5. **Cài đặt Workspace**: nguồn mã, luật liên kết, bộ lọc nhiễu, liên kết chốt tay, đối chiếu OpenAPI, lịch sử quét, xuất / nhập.

## Đo lường

`node scripts/benchmark.mjs` và `node scripts/evaluate-accuracy.mjs` — xem [`scripts/README.md`](scripts/README.md); kết quả tổng hợp ở [`docs/evaluation.md`](docs/evaluation.md).

Lệnh build/test từng phần: xem `CLAUDE.md` §2.
