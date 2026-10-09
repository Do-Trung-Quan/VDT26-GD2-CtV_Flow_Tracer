# Scripts

Sẽ bổ sung khi cần (xem `CLAUDE.md` §2.4):
- `benchmark.mjs` — đo NFR-01 → NFR-04.
- `evaluate-accuracy.mjs` — chạy cục bộ, so với ground truth, chỉ in số liệu tổng hợp.
- `check-forbidden-terms.mjs` — **đã có** (Giai đoạn 1): chặn commit chứa từ khóa nội bộ (danh sách trong file `.forbidden-terms`, đã gitignore). Mặc định quét file đã `git add`; `--all` quét cả repo.
