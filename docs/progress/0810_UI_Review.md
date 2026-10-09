# Báo cáo 08/10 — Điều chỉnh theo review giao diện

> **Mục tiêu:** chốt các quyết định thiết kế sau khi review bản vẽ giao diện, rồi cập nhật tài liệu, code khung và giao diện cho khớp.
> **Kết quả:** tài liệu, code khung và `UI_offline.html` đã đồng bộ; Engine `verify` xanh, sidecar test xanh, kiểm thử giao diện tự động đạt.
> **Chưa commit.**
> Phần trước đó (dựng khung và bản vẽ giao diện đầu tiên): [0710_setup_codebase.md](0710_setup_codebase.md).

---

## 1. Việc đã làm

- **Quyết định đã chốt:**
  - Diagram một mức hiển thị, kiểu PlantUML. Cột theo vai trò: Frontend gộp 1 cột, mỗi Controller / Service / Repository một cột, Database 1 cột. Nhãn mũi tên ngắn, mang tính kỹ thuật.
  - Kịch bản gồm "Các actor tham gia" và "Bước n" khớp mũi tên số n. Bỏ công tắc chi tiết kỹ thuật.
  - Bỏ "AI chọn liên kết". Thay bằng nút "Nhờ AI phân tích": AI chỉ giải thích, có thể đề xuất thêm ứng viên sau khi đã kiểm tra; người dùng chốt tay.
  - Mục đích 80–100 từ: dùng cache, rồi đến AI, cuối cùng mới Swagger/Javadoc.
  - Vai trò Source do tool tự nhận diện, kèm 4 luật xử lý thư mục.
  - Bỏ Xuất/Nhập Workspace và bỏ tải file OpenAPI.
  - Giữ trang thống kê AI (số liệu thật) và thêm bộ đếm lượt trúng cache.
  - Đưa lời gọi microservice qua HTTP và API bên ngoài vào MVP.
- **Tài liệu đã sửa:** `overview.md` (§1.1.4, §2.1, §2.2.4, §2.4, §2.6, §2.7, Phụ lục A), `feature_list.md` (còn 73 chức năng: 46 MVP / 4 Nên có / 23 Mở rộng), `API_list.md`, `database.md`, `database.dbml`, `project_structure.md`, `CLAUDE.md`.
- **Code đã sửa:**
  - Bỏ `Confidence.AI_RESOLVED`, `DetailLevel`, `DetailLevelCollapser`.
  - `MermaidExporter` → `PlantUmlExporter`. Thêm `Participant.Role`, `TraceStep.pinned`, khung `ParticipantBuilder` và `SelfStepBuilder`.
  - `TraceIds` tính cả liên kết chốt tay.
  - Migration `V2__drop_ai_resolved_add_cache_hits.sql`; thêm `hit_count` / `last_hit_at` vào `AiArtifact`.
  - `contracts/ir-fragment.schema.json` và sinh lại kiểu cho sidecar.
- **Giao diện:** dựng lại `docs/UI/UI_offline.html` theo các quyết định trên, có 4 luồng mẫu (Lưu, Tìm kiếm, Thêm mới, Xóa có 2 cột Service và bước chưa xác định). Đã tạo lại bản cục bộ `UI_offline.local.html` (không commit).
- **Kiểm tra:**
  - `mvnw verify` xanh: 18 test, gồm test CSDL thật có chạy migration V2.
  - Sidecar test xanh.
  - Chạy thử giao diện tự động bằng Chrome không giao diện: 60 mục đều đạt, không có lỗi JavaScript.
- **Vấn đề gặp phải:** chạy `spotless:apply` cho toàn bộ engine làm thay đổi khoảng 170 file không liên quan; đã hoàn tác, chỉ giữ 14 file Java dự định sửa.

---

## 2. Việc còn dở

- Chưa có bản review bằng mắt của người dùng cho lần dựng lại này; các nhận xét tiếp theo xử lý ở [0910_roadmap_va_so_sanh_cu_moi.md](0910_roadmap_va_so_sanh_cu_moi.md).
- Roadmap chưa được viết (làm ở ngày 09/10).
