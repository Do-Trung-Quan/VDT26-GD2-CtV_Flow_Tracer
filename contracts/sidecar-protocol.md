# Giao thức Engine ↔ Node Analyzer (sidecar)

Sidecar là **CLI không trạng thái** với hai lệnh: `analyze` (phân tích một Source) và `detect` (nhận diện công nghệ của một thư mục). Engine gọi `analyze` một lần cho mỗi Source cần phân tích bằng adapter chạy trên Node.js (Angular; PHP-legacy ở bản mở rộng).

## Cách gọi

```bash
node --max-old-space-size=<MB> analyzer-node/dist/cli.js analyze < request.json > fragment.json 2> progress.ndjson
```

| Kênh | Nội dung | Schema |
| :--- | :--- | :--- |
| stdin | Một `AnalyzeRequest` (JSON) | `analyze-request.schema.json` |
| stdout | Một `IrFragment` (JSON) — **chỉ** in khi thành công | `ir-fragment.schema.json` |
| stderr | Tiến độ dạng NDJSON (mỗi dòng một `ProgressEvent`); dòng khác coi là log | `progress-event.schema.json` |

## Lệnh `detect` — nhận diện công nghệ của một thư mục

Dùng khi người dùng chọn thư mục (API A10) và khi thêm Source. Engine tìm các thư mục ứng viên có file đặc trưng của adapter (vd `angular.json`), rồi gọi sidecar **một lần cho mỗi ứng viên**:

```bash
node analyzer-node/dist/cli.js detect < detect-request.json > detect-result.json
```

| Kênh | Nội dung | Schema |
| :--- | :--- | :--- |
| stdin | Một `DetectRequest`: tên adapter và **nội dung** các file đặc trưng (vd `angular.json`, `package.json`) | `detect-request.schema.json` |
| stdout | Một `DetectResult` (`score` 0..1, `stackName`, `version`, `reason`) | `detect-result.schema.json` |

Sidecar **không đọc file nào** ở lệnh này — chỉ dùng nội dung Engine gửi kèm. Mã thoát giống lệnh `analyze`.

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

`protocolVersion` hiện là `1`. Thay đổi không tương thích phải tăng phiên bản và cập nhật đồng thời: schema trong thư mục này, record Java trong `flowtracer-ir` (`IrFragment`, `DetectResult`…), kiểu sinh ra trong `analyzer-node` (`npm run gen:contracts`).
