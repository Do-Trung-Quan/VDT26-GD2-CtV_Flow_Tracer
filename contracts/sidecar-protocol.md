# Giao thức Engine ↔ Node Analyzer (sidecar)

Sidecar là **CLI không trạng thái**. Engine gọi nó một lần cho mỗi Source cần phân tích bằng adapter chạy trên Node.js (Angular; PHP-legacy ở bản mở rộng).

## Cách gọi

```bash
node --max-old-space-size=<MB> analyzer-node/dist/cli.js analyze < request.json > fragment.json 2> progress.ndjson
```

| Kênh | Nội dung | Schema |
| :--- | :--- | :--- |
| stdin | Một `AnalyzeRequest` (JSON) | `analyze-request.schema.json` |
| stdout | Một `IrFragment` (JSON) — **chỉ** in khi thành công | `ir-fragment.schema.json` |
| stderr | Tiến độ dạng NDJSON (mỗi dòng một `ProgressEvent`); dòng khác coi là log | `progress-event.schema.json` |

## Mã thoát

| Mã | Ý nghĩa | Engine xử lý |
| :--- | :--- | :--- |
| `0` | Thành công | Kiểm tra stdout với schema rồi dùng |
| `2` | Lỗi đầu vào (request sai schema, adapter không hỗ trợ, đường dẫn ngoài `sourceRoot`) | Lỗi `SIDECAR_FAILED`, ghi log |
| `3` | Lỗi phân tích không phục hồi được | Lỗi `SIDECAR_FAILED`; lần quét `PARTIAL` hoặc `FAILED` |
| `4` | Hết thời gian / vượt bộ nhớ | Engine kill tiến trình khi quá `flowtracer.sidecar.timeout` |

File lỗi cú pháp **không** làm sidecar thoát với mã khác 0: sidecar ghi `Diagnostic` với `severity = ERROR` vào kết quả và tiếp tục.

## Ràng buộc an toàn

- Chỉ **đọc** đúng các file trong `files[]`, bên trong `sourceRoot`; từ chối đường dẫn chứa `..` hoặc symlink trỏ ra ngoài.
- Chỉ **ghi** vào `cacheDir` (cache dữ kiện pha 1, tên file = `<hash>.json`).
- Không gọi mạng, không chạy mã của dự án được phân tích.
- Kết quả phải **tất định**: node sắp theo `key`, edge sắp theo `fromKey → kind → callOrder → toKey`.

## Phiên bản

`protocolVersion` hiện là `1`. Thay đổi không tương thích phải tăng phiên bản và cập nhật đồng thời: schema trong thư mục này, record Java trong `flowtracer-ir`, kiểu sinh ra trong `analyzer-node` (`npm run gen:contracts`).
