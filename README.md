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

```bash
cp .env.example .env          # điền SOURCE_ROOT
docker compose -f docker/docker-compose.yml --env-file .env up -d --build
# mở http://localhost:8600
```

Lệnh build/test từng phần: xem `CLAUDE.md` §2.
