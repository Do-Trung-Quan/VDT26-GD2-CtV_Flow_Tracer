# 14/10 — Chỉnh màn hình Luồng sau khi người dùng review

Việc ngoài giai đoạn (chỉnh giao diện / dữ liệu hiển thị của màn hình chi tiết Workspace). Không đổi CSDL.

## 1. Hoàn tác thống kê cache (Cấu hình AI)
- Gỡ thẻ "Trúng cache", cột "Trúng cache" và `cacheHits` khỏi I6; khôi phục `hit_count` / `last_hit_at` và đoạn ghi bộ đếm như trước. V4 đã chạy trên CSDL dev của người dùng nên giữ file V4 và thêm migration **V5** (khôi phục 2 cột, xóa dòng `CACHE_HIT`; đã được người dùng đồng ý). Quy ước mới: đổi CSDL phải xin xác nhận trước.

## 2. Cột trái
- Tìm kiếm Frontend: thay lọc cây bằng **danh sách gợi ý hai nhóm** "Màn hình" / "Phần tử giao diện" (giống Ctrl+K); chạy trên cây đã tải, **không phân biệt dấu**, khớp cả tên do AI đặt, tên kỹ thuật, tên class, route; bấm một kết quả thì quay về cây đầy đủ, mở và chọn mục đó (Enter mở kết quả đầu). Backend giữ nguyên. (`searchUi`, `highlightFold` trong `frontend-tree-model.ts`).
- Dòng chú thích: "N ứng dụng · M màn hình · K phần tử giao diện".
- Checkbox "Hiện cả phần tử chỉ thao tác giao diện": giữ nguyên; nút Thêm mới / Sửa vẫn hiện vì mở hộp thoại / màn hình có phần tử gọi API (`leadsToApi`).

## 3. Cột phải
- Màn hình / hộp thoại: chỉ còn tiêu đề, nhãn loại, nút "Sinh lại tên thành phần bằng AI" (gọi `force=true`) và khung chờ chỉ dẫn bấm vào thành phần con. Đặt tên áp dụng cho **mọi phần tử bên dưới, kể cả trong hộp thoại lồng nhau** (engine lấy hậu duệ qua `CONTAINS`), chỉ phần tử đáng đặt tên được gửi AI.
- Bỏ các ghi chú: cảnh báo luồng (vượt 70 bước, lời gọi / truy vấn chưa xác định…), "Chưa có bản AI…", "Đang hiện khung câu / dữ liệu mẫu…", "Mã nguồn không có nhãn…", API ngừng dùng. Chỉ giữ banner dịch vụ AI. Engine không đổi (cảnh báo trace vẫn có cho trạng thái rỗng của canvas).
- Bỏ nút "Sinh cả Mục đích và Dữ liệu mẫu".
- Nhãn nội dung không do AI thống nhất "Hệ thống tự sinh" (chỉ ở web; không đổi nhãn trong engine vì nhãn nằm trong prompt → đổi sẽ làm mất cache AI).
- Kịch bản: chỉ **bấm** một bước mới bôi đỏ mũi tên (bỏ rê chuột).
- Xem code: **một tab cho mỗi file**; các hàm / khối liên quan trong file nối tiếp nhau và được tô sáng, mỗi cái từ dòng đầu (lùi lên lấy Javadoc / chú thích / annotation / decorator liền trước) tới dòng cuối của nó; hai đoạn cách xa nhau thì ngăn bằng một dòng `⋯` thay vì kéo cả phần mã ở giữa (không còn gộp min–max rồi cắt ở 160 dòng; chỉ chặn ở 1000 dòng). DTO C5 thêm `lineNumbers[]` (số dòng từng dòng `code`, `0` = dòng ngăn cách). Phần tử HTML và lời gọi HTTP mà dữ liệu quét chưa có dòng cuối (workspace quét trước 14/10) thì Engine **tự suy ra khi đọc**: tới thẻ đóng tương ứng / hết chuỗi `.pipe(...)`; sidecar cũng ghi thêm `endLine` cho các nút này và constructor giả trỏ đúng chỗ khởi tạo field. Tăng `FACTS_VERSION` = 5 và `ANGULAR_ANALYSIS_VERSION` = 5 → quét lại để có dòng cuối chính xác từ IR. Golden `angular-mini.ir.json` cập nhật.

## 4. Canvas
- Toast xuất: "Xuất ảnh SVG thành công" / "Xuất ảnh PNG thành công".
- Popover "7 API khớp nhưng chỉ 5 đề xuất": giới hạn cứng 5 ứng viên ở Linker; nay lưu **đủ mọi API ngang điểm** (tối đa 30) → số trong câu lý do khớp số dòng. Cần quét lại để dữ liệu cũ có đủ ứng viên.
- Bước chưa xác định không bấm được: bước tự xử lý (từ `parseEpochSecond`… cùng tên trong một lớp) có `from == to` nên web không mở popover dù có ứng viên → cho phép mở; đồng thời sửa `SelfStepBuilder` để `edgeRef` là của liên kết yếu nhất (trước đây của lời gọi đầu tiên, lệch với mức tin cậy / lý do hiển thị).
- Câu lý do trong popover là mẫu câu tổng quát (`Linker`, `FlowResolver`, `ImplementationResolver`, `DataAccessResolver`, `di.resolver.ts`…), thay tham số bằng dữ liệu dự án; không gắn cứng tên dự án nào. Heuristic chung duy nhất: regex kiểu truy cập dữ liệu (`repository|dao|template|jdbc|session|executor`) và danh sách `EntityManager/JdbcTemplate/…`.

## 5. Còn dở / lưu ý
- Cạnh truy vấn bảng (EXECUTES/READS/WRITES) mở được popover nhưng không có ứng viên / không chốt tay được mà vẫn hiện câu "chưa tìm được ứng viên" — chưa sửa.
- Bước chưa xác định nằm ngoài diagram (hàm không "nghiệp vụ", hoặc luồng quá 70 bước ở chế độ rút gọn) không hiện được.

## 6. Gợi ý của AI không hiện trên popover (14/10, tối)
- Nguyên nhân: liên kết suy luận chỉ khớp **một** API không lưu danh sách ứng viên (`Linker` chỉ lưu khi có nhiều ứng viên) nên popover chỉ có một dòng "Đích tự động hiện tại". Engine lại đưa chính đích đó vào mục "có thể chọn thêm" của prompt; AI trả lời `extra` = đúng đích đó (đúng thật) và Engine báo thành công. Web không thêm dòng vì dòng đó đã có sẵn trong danh sách, còn lời giải thích chỉ gắn vào dòng theo `analyses` hoặc dòng do AI thêm → lời giải thích bị mất.
- Sửa: Engine coi đích tự động là ứng viên duy nhất khi không có danh sách (`EdgeAdviceService.candidates`) nên AI trả `analyses` cho nó và trừ nó khỏi mục "chọn thêm"; web (`adviceFor` trong `edge-popover-model.ts`) vẫn hiện lời giải thích của `extra` trên dòng trùng khớp. Không đổi dữ liệu quét; lần "Nhờ AI phân tích" sau sẽ gọi lại AI vì nội dung prompt đã đổi.

## 7. Cải thiện phân tích liên kết bằng AI (14/10, tối)
AI trước đây chỉ thấy đoạn mã tại lời gọi HTTP (đi xuống) và danh sách ứng viên; bằng chứng (chuỗi hằng truyền vào, tên hàm, tên phần tử) nằm phía **trên** điểm gọi nên lời giải thích chung chung và "Nên chọn" cho cả vài ứng viên. Đã làm (không đổi CSDL, không đổi IR):
1. **Chuỗi gọi vào prompt** (`CallChainService`): đường ngắn nhất từ thành phần người dùng đang xem (`start_key` / `startKey`) tới điểm gọi theo các cạnh luồng — theo đúng luồng đang xem, không lẫn các nơi khác cùng gọi hàm dùng chung (vd `get(resource)`); mỗi mắt xích kèm dòng mã dẫn tới mắt xích kế (chuỗi hằng truyền vào). Prompt đổi sang `edge-resolution.v2.st`; khóa cache theo `cạnh#chuỗi`.
2. **Tín hiệu khớp tên tất định** (`CandidateHintRule`, `hints[]` ở E1): từ trong chuỗi gọi trùng với đường dẫn ứng viên (tách camelCase, bỏ từ chung, gốc từ đơn giản), bỏ từ có ở mọi ứng viên; hiện "Khớp tên: …" trên từng ứng viên, xếp ứng viên nhiều tín hiệu lên trước; chạy cả khi AI tắt / hết hạn mức.
3. **Bằng chứng và nhất quán** (`AdviceConsistencyRule`): AI phải trả `evidence`; "SHOULD" không có bằng chứng thật trong chuỗi gọi / mã bị hạ xuống UNSURE; nhiều SHOULD thì giữ ứng viên nhiều tín hiệu nhất (khi duy nhất), còn lại UNSURE. Popover hiện "Bằng chứng: …".
Kiểm thử bằng test đơn vị và AI giả (không tốn lượt Gemini); chưa thử với Gemini thật.

## 8. Khung giao diện chung và rà soát tài liệu (14/10, tối)
**Giao diện (web):**
- Header: thêm nút 3 gạch mở rộng / thu gọn menu bên trái (nhớ lựa chọn trong trình duyệt); logo là ô trắng "CtV" + tên "Flow Tracer"; dấu ngăn; dropdown Workspace theo thiết kế của bản vẽ (không còn `<select>`), có mục "Danh sách Workspace".
- Menu trái thu gọn (icon) hoặc mở rộng (có tên): Danh sách Workspace · **Chi tiết Workspace** (trước là "Màn hình chính"; bản vẽ `UI_offline.html` cũng đổi theo) · Cài đặt Workspace · Cấu hình AI.
- Thành phần dùng chung mới `core/ui/select-menu.ts` (dropdown phím ↑ ↓ Enter / Esc, kiểu `header` và `field`); đã thay `<select>` ở hộp thoại luật liên kết. Ô tích / ô chọn (checkbox, radio) và ô tìm kiếm được ép theo thiết kế bản vẽ ở `styles.scss` (không còn kiểu mặc định của trình duyệt).
- Tiêu đề tab "Flow Tracer"; favicon là logo "CtV" (`public/favicon.svg`, kèm `favicon.ico` sinh từ SVG).
- Kiểm tra bằng Chrome headless (API giả): logo, menu mở rộng 212px, dropdown Workspace và dropdown Loại luật hiển thị đúng; không còn `<select>` nào.

**Tài liệu `.context/`** rà soát theo mã nguồn và sửa chỗ lệch: `database.md` (search_text, `input_hash` không gồm model, `target_key` các mục đích, bộ đếm `hit_count`, khóa `app_settings`, lịch sử migration V1–V5), `API_list.md` (E1/E2 `start_key`/`hints`/`evidence`, C5 `lineNumbers`, F1 không có SSE / `stream`, I1/I3/I6 trạng thái AI, `/api/health`), `feature_list.md` (tìm kiếm cây Frontend, Xem code, đặt tên phần tử bằng AI là một chức năng MVP → 73 chức năng / 47 MVP, trạng thái AI, thống kê), `overview.md` (NFR-05/15/17/20, công nghệ web không dùng Angular Material, SSE chỉ cho tiến độ quét, §2.3 tên bảng và migration, §2.6 giao diện, §2.7 khi nào gọi AI / trạng thái / log), `project_structure.md` (service AI mới, prompt v2, dropdown, favicon).

## 9. Cập nhật `docs/diagrams/database.dbml` (14/10, tối)
Đối chiếu với `.context/database.md` và migration V1–V5: giữ nguyên 12 bảng / cột / enum (đã khớp); bổ sung giá trị mặc định (`gen_random_uuid()`, `now()`), các chỉ mục còn thiếu (`idx_sources_workspace`, `idx_scan_runs_workspace`, `idx_link_overrides_workspace`, `idx_llm_call_logs_created`, tên các chỉ mục / ràng buộc duy nhất), hành vi xóa của hai khóa ngoại (`workspaces.last_scan_run_id` và `sources.parent_source_id` → `set null`), ghi chú cho từng bảng / cột quan trọng theo tài liệu mới (`input_hash` không gồm model, `target_key`, `hit_count` chưa ghi được, khóa `app_settings`, `CONNECTION_TEST`, ràng buộc CHECK của `link_overrides`, giá trị enum `CACHE_HIT` còn thừa) và 4 nhóm bảng (cấu hình phân tích, kết quả phân tích, AI, dữ liệu người dùng). Đã kiểm tra bằng bộ phân tích DBML v2 (cùng bộ của dbdiagram.io): 12 bảng, 9 enum, 18 quan hệ, 4 nhóm, số cột từng bảng khớp V1.

## 10. Gỡ hẳn nhóm chức năng (Feature) (14/10, tối)
Theo yêu cầu của người dùng (đã đồng ý đổi CSDL): nhóm chức năng là chức năng Mở rộng đã loại khỏi kế hoạch, hai bảng chưa từng được dùng nên gỡ hẳn.
- **CSDL:** migration mới `V6__drop_feature_tables.sql` xóa bảng `feature_members`, `features` và kiểu `feature_origin` (V1–V5 giữ nguyên). Giá trị `FEATURE_SUMMARY` của `ai_purpose` còn lại trong enum, không dùng. Engine cần khởi động lại để Flyway chạy V6.
- **Mã nguồn:** xóa package `feature/` (entity, repository, package-info) trong `flowtracer-app`; bỏ `feature` khỏi `ArchitectureTest`; bỏ `ErrorCode.FEATURE_NAME_TAKEN` (không nơi nào dùng).
- **Tài liệu:** `database.md` (bỏ §11–§12, bảng quan hệ, thêm V6), `database.dbml` (bỏ enum/bảng/Ref/nhóm bảng; còn 10 bảng, parse lại OK), `overview.md` (12 → 10 bảng, thuật ngữ, nhóm 7, `FEATURE_SUMMARY`), `project_structure.md` (bỏ `feature/`, prompt `feature-summary`), `feature_list.md` (Nhóm 7) và `API_list.md` (Nhóm G) ghi chú "đã loại khỏi kế hoạch, chưa triển khai", `CLAUDE.md` (bỏ package/bảng `feature`, 12 → 10 bảng).
