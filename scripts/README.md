# Scripts

Chạy bằng Node 22, không cần cài thêm thư viện. Cần Engine đang chạy (`./mvnw -f engine/pom.xml -pl flowtracer-app spring-boot:run -Dspring-boot.run.profiles=dev`).

| Script | Việc | Ghi chú |
| :--- | :--- | :--- |
| `benchmark.mjs` | Đo NFR-01 → NFR-04 và NFR-06: quét lần đầu, quét lại, tải cây, tìm kiếm, dò vết, RAM | Chỉ in số liệu tổng hợp |
| `evaluate-accuracy.mjs` | Đo NFR-07 → NFR-09 so với ground truth | **Chỉ chạy cục bộ**; chỉ in số liệu tổng hợp |
| `check-forbidden-terms.mjs` | Chặn commit chứa từ khóa nội bộ (danh sách trong `.forbidden-terms`, đã gitignore). Mặc định quét file đã `git add`; `--all` quét cả repo | |
| `lib/engine-client.mjs` | Phần dùng chung: gọi API, thống kê, đọc RAM, so khớp tên | |

## `benchmark.mjs`

```bash
# Tạo Workspace tạm từ thư mục (tương đối so với SOURCE_ROOT), đo, rồi xóa
node scripts/benchmark.mjs --source <thư-mục> [--source <thư-mục-khác>]
# Đo trên Workspace đã có (không quét lại IMPORT, không xóa)
node scripts/benchmark.mjs --workspace <id>
```

- **Quét lại với 20 file thay đổi** cần sửa file. Script **không bao giờ sửa mã nguồn gốc**: nó sao chép các Source vào thư mục tạm `<SOURCE_ROOT>/ft-bench-<mã>/` (bỏ `node_modules`, `.git`, `target`, `dist`…), đo trên bản sao, sửa 20 file **trong bản sao** rồi xóa bản sao khi xong. Dùng `--no-copy` để tắt (khi đó chỉ đo quét lại không file nào đổi). `SOURCE_ROOT` lấy từ `--source-root`, biến môi trường hoặc `.env`.
- RAM: đo bộ nhớ vật lý của tiến trình đang nghe cổng của `--base` (hoặc `--pid`); lấy mẫu mỗi nhịp chờ lúc quét.
- Dò vết: lấy đều tối đa `--traces` API (mặc định 30) và một nửa số đó phần tử giao diện có gọi API; luồng > 50 bước không tính vào NFR-04.
- `--container <tên>`: đo RAM của container Docker khi Engine chạy trong Docker (thay cho `--pid`). `--exclude <glob>`: loại thư mục / file khỏi mọi Source (hữu ích khi chạy trong Docker trên Windows — xem README).
- `--json` in kết quả dạng JSON.

## `evaluate-accuracy.mjs`

```bash
node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <đường-dẫn-cục-bộ> [--openapi <url>] [--verbose]
```

**Ground truth đặt NGOÀI repo** (CLAUDE.md §3.5). Ví dụ định dạng tự viết: `fixtures/ground-truth/spring-angular-mini.json`.

```json
{
  "links": [
    { "screen": "Danh mục", "element": "Lưu", "apis": ["POST /api/v1/categories", "GET /api/v1/categories"] },
    { "screen": "Danh mục", "element": "Thêm mới", "apis": [] }
  ],
  "traces": [
    { "start": { "api": "POST /api/v1/categories" }, "tables": ["category"] },
    { "start": { "screen": "Danh mục", "element": "Lưu" }, "tables": ["category"], "maxDepth": 20 }
  ]
}
```

- `links` (NFR-08): mỗi mục là **một phần tử giao diện** và **toàn bộ** API nó gọi (`[]` nếu chỉ thao tác giao diện). Phần tử tìm theo chữ trên giao diện (không phân biệt hoa thường, dấu) trong màn hình / hộp thoại / tab / form có tên chứa `screen`; trùng hoặc không thấy thì mục đó tính là thiếu. API so khớp theo phương thức + đường dẫn, tên biến `{id}` coi như nhau. Precision / recall tính trên các cặp (phần tử, API) của những phần tử có trong ground truth.
- `traces` (NFR-09): điểm xuất phát là một API (`api`) hoặc một phần tử (`screen` + `element`); đạt khi **mọi bảng** trong `tables` có mặt trong luồng và **không có bước `UNRESOLVED`**.
- NFR-07: gọi API C6 (`openapi-check`); tỉ lệ = số API khớp / số API trong đặc tả.
- Ghép phần tử: nhãn so không phân biệt hoa thường / dấu, bỏ dấu `*` và `:` cuối; nhiều phần tử cùng nhãn thì ưu tiên phần tử nằm trong màn hình / hộp thoại có tên **trùng đúng** với `screen`; vẫn nhiều thì nếu tool cho cùng một tập API coi là tương đương, còn khác tập thì tính là **mơ hồ** (báo riêng, không ép chọn). Báo cáo NFR-08 có hai recall: trên các phần tử xác định được, và **nghiêm ngặt** (phần tử không xác định được = bỏ sót toàn bộ API của nó).
- NFR-09 đạt khi luồng có **đủ bảng** kỳ vọng và **không có bước chưa xác định**.
- Mặc định không in tên; `--verbose` chỉ in số thứ tự mục sai (và loại sai) để bạn dò lại thủ công. `--diagnose` (CHỈ để gỡ lỗi trên máy bạn) in chi tiết có tên của các mục sai ra màn hình — không dán vào repo / tài liệu.
