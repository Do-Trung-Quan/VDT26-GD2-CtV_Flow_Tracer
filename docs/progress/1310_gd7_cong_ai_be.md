# Giai đoạn 7 — Cổng AI & nội dung AI (BE + FE)

> Ngày 13/10. Phạm vi lần này: **Task 7.1** (cổng AI, provider, API I1–I3, I6) và phần **BE của Task 7.2** (4 mục đích MVP + mục đích mới `ELEMENT_NAMING`, F4, E2). Phần FE xem mục 5 (làm ngay sau BE theo cho phép của người dùng).

---

## 1. Việc đã làm

### 1.1. Cổng AI (Task 7.1)

| Thành phần | Nội dung |
| :--- | :--- |
| `AiGateway` | 7 bước: mục đích bật? → cache theo mã băm đầu vào (tăng `hit_count`) → provider dùng được? → hạn mức token ngày → che secret → gọi provider + kiểm tra đầu ra → lưu `ai_artifacts` + ghi `llm_call_logs` (chỉ mã băm). Không bước nào ném lỗi ra ngoài: trả `AiOutcome` (`GENERATED` / `CACHED` / `DISABLED` / `UNAVAILABLE` / `QUOTA_EXCEEDED` / `REJECTED`) để người gọi dùng kết quả tất định. Bản đã lưu vẫn được dùng khi provider tắt. |
| `SecretRedactor` | Che giá trị của khóa có tên chứa `password / secret / token / key / credential` (`keyword` không bị che), chuỗi kết nối CSDL / dịch vụ, khối private key, `Bearer …`, vài dạng API key quen thuộc; chặn gửi file `.env`, keystore, dữ liệu. |
| `SpringAiLlmProvider` + `LlmProviderConfig` | Gemini / Anthropic / OpenAI-compatible qua Spring AI `ChatModel`; khóa chỉ đọc từ biến môi trường; thiếu khóa thì AI không khả dụng và ứng dụng vẫn chạy. Gemini: JSON mode, ngân sách đầu ra 16384 token, `thinkingLevel = MINIMAL` cho model `gemini-3*` (thinking mặc định làm cụt JSON và chậm). Lỗi provider chỉ trả loại lỗi cho người dùng; log kèm thông điệp đã che secret để chẩn đoán. |
| `AiSettingsService` + `AiSettingsController` | I1 xem cấu hình (không bao giờ trả khóa), I2 bật / tắt mục đích (lưu `app_settings`), I3 kiểm tra kết nối, I6 thống kê (lượt gọi, token, trúng cache, lỗi, độ trễ, token hôm nay / hạn mức). |
| `PromptTemplates` + `prompts/*.v1.st` | 5 prompt có phiên bản (SYSTEM / USER / SCHEMA). |
| Migration `V3` | Thêm giá trị `ELEMENT_NAMING` cho enum `ai_purpose` (đã cập nhật `database.md`, `database.dbml`). |

### 1.2. Các mục đích AI (BE của Task 7.2)

| Mục đích | Cách làm | Kiểm tra đầu ra |
| :--- | :--- | :--- |
| `PURPOSE_TEXT` | Mục đích 80–100 từ + **diễn đạt lại ràng buộc và "Lỗi có thể gặp"** (yêu cầu của người dùng: không tự dịch tay hết). API trong prompt dùng mã `a1…`, mục thô dùng `c1…` / `e1…` | Đoạn mục đích 40–140 từ; chỉ giữ câu có id thuộc đầu vào; id AI không trả thì giữ câu tất định |
| `SCENARIO_POLISH` | Lời kể từng bước từ khung câu | `ScenarioPolishRule`: cùng số bước, thứ tự; câu gốc nhắc actor nào thì câu AI cũng phải nhắc |
| `MOCK_DATA` | Sinh một lần từ JSON Schema request / response của DTO (`FallbackMockGenerator.schemas`) | `SchemaValidator` — sai schema thì bỏ |
| `EDGE_RESOLUTION` (E2) | Giải thích từng ứng viên + tối đa một ứng viên đề xuất thêm | `AiAdviceRule`: ứng viên thêm phải là node có thật, đúng loại, đúng phương thức HTTP. **Liên kết không bao giờ đổi** |
| `ELEMENT_NAMING` (**mục đích mới**) | Mỗi lần 30 phần tử của một màn hình / hộp thoại: loại, chữ trên giao diện, tên control, hàm xử lý, khóa dịch, route, API gọi (method, đường dẫn, mô tả, Controller#hàm), hộp thoại / màn hình mở. Tên AI lưu theo từng thành phần chứa và hiện qua `aiLabel` ở cây Frontend (C2) và quan hệ (C4) — **không đổi cây, liên kết, diagram** | `ElementNameRule`: ≤ 60 ký tự, không giống tên biến / hàm, bỏ tiền tố loại ("Nút", "Ô"); id phải thuộc đầu vào |

API: F1 / F2 / F3 trả thêm `ai` {`APPLIED` / `MISSING` / `OFF`} và chỉ **đọc bản đã lưu** (không bao giờ chờ AI); F4 `POST /ai-artifacts/regenerate` sinh / sinh lại (AI lỗi → `503` kèm lý do, nội dung cũ được giữ); E2 `POST /edges/{id}/advice`.

Đã cập nhật: overview §2.7 (thêm `ELEMENT_NAMING`; `PURPOSE_TEXT` có thêm `errors`), CLAUDE.md §3.6, `API_list.md`, `database.md` + `dbml`, `project_structure.md` (cây `ai/`, `model/`), roadmap, README của `prompts/`.

### 1.3. Chịu lỗi khi AI hết hạn mức / quá tải (bổ sung sau khi duyệt hướng làm)

| Việc | Cách làm |
| :--- | :--- |
| Phân loại lỗi | `SpringAiLlmProvider.classify`: `QUOTA` (429 / RESOURCE_EXHAUSTED, nhận ra hạn mức theo ngày và thời gian `retry in Ns`), `OVERLOADED` (502 / 503 / 504), `AUTH` (401 / 403), `BAD_REQUEST` (400), `TIMEOUT`, `OTHER`. Thông điệp lỗi được che secret và cắt ngắn. Quá tải được thử lại đúng 1 lần. Tắt retry mặc định của Spring AI cho Gemini (trước đây treo 20–60 giây khi 429). |
| Thời gian nghỉ | `ProviderHealth`: sau lỗi, provider "nghỉ" (429 theo lời nhắc của nhà cung cấp + 2 giây, mặc định 60 giây; theo ngày 1 giờ; 503 30 giây; sai khóa / model 5 phút; tối đa 6 giờ). Trong lúc nghỉ cổng AI **không gọi provider**, trả ngay nội dung tất định kèm câu lý do và giờ thử lại; bản AI đã lưu vẫn dùng. Gọi thành công hoặc "Kiểm tra kết nối" thành công thì hết nghỉ. |
| Giới hạn đồng thời | Tối đa 2 lời gọi provider cùng lúc; các yêu cầu giống hệt nhau đang chạy được gộp thành một lời gọi. |
| API | I1 thêm `status = COOLING`, `cooldownUntil`, `cooldownReason`; `503` của F4 / E2 kèm `retryAt`, `reason` trong `errors`. |
| Thu hẹp `ELEMENT_NAMING` | Chỉ đặt tên phần tử có gọi API / mở màn hình / lời gọi chưa xác định, chưa có chữ trên giao diện hoặc chữ quá chung; phần tử có API xếp trước. Phần tử còn lại giữ tên từ mã nguồn. |
| Bỏ khỏi phạm vi (đã thống nhất) | Provider `replay` và script làm nóng cache: cache lưu ngay khi người dùng thao tác trên web. |

---

## 2. Kiểm thử

- Engine `clean install`: **245 test** qua (+ `AiGatewayTest` 4 ca nghỉ / gộp yêu cầu, `ProviderErrorClassificationTest` 4, `ProviderHealthTest` 1; trước đó 236) (thêm `SecretRedactorTest` 4, `AiGatewayTest` 9 — cache / che secret / hạn mức / đầu ra sai / tắt mục đích / cache khi provider tắt, `AiRulesTest` 3, `AiApiIT` — toàn luồng với provider giả).
- `AiApiIT` (Testcontainers, provider giả): I1 / I3, F4 sinh mục đích → F1 dùng bản AI (id không có thật bị bỏ, id thiếu giữ câu tất định), `force=false` → `CACHED`, tắt mục đích → F1 `OFF` + F4 `503 AI_DISABLED`, kịch bản AI giữ nguyên số bước, dữ liệu mẫu sai schema bị loại (`503 AI_UNAVAILABLE`, F2 vẫn `RULE`), đặt tên phần tử → `aiLabel` trên cây (số phần tử không đổi), E2 trả ứng viên thêm là API thật mà liên kết E1 không đổi, I6 khớp.
- **Chạy thật với Gemini trên dự án demo** (Workspace tạm, đã xóa; chỉ ghi số liệu tổng hợp): mục đích 103 từ, ràng buộc và lỗi viết lại thành câu tiếng Việt tự nhiên không còn mã / biểu thức; dữ liệu mẫu khớp schema và đúng ngữ cảnh ("tác giả", không còn "Điện thoại"); đặt tên 48/48 phần tử của màn hình Tác giả trong ~8 giây (tên như "Chọn tác giả", "Làm mới dữ liệu tác giả", "Cột thao tác tác giả"); kịch bản 24 bước = 24 mũi tên, lời kể tự nhiên. Độ trễ sau khi hạ thinking: dữ liệu mẫu 1,8 giây, kịch bản 4,4 giây, đặt tên 8 giây (trước đó 9–68 giây và JSON bị cụt).

---

## 3. Việc còn dở / điểm cần biết

- FE (Task 7.2) đã làm — xem mục 5.
- **Chưa có F1 `stream=true` (SSE)**: giao diện hiện ngay nội dung tất định rồi gọi F4; hiệu ứng chữ hiện dần do FE làm. NFR-05 "chữ đầu tiên ≤ 5 giây" được đáp ứng bởi nội dung tất định hiện ngay; bản AI thường 2–20 giây tùy model.
- **Hạn mức Gemini miễn phí (429)** bị chạm khi chạy thử nhiều lần liên tiếp: đã xử lý ở mục 1.3 (phân loại lỗi + thời gian nghỉ).
- `ELEMENT_NAMING` chưa dùng mã Controller / DTO của API ngoài tên hàm xử lý và mô tả; tìm kiếm (C3) chưa tìm theo tên AI.
- Cache theo mã băm đầu vào: đổi mã nguồn của phần tử thì lần "Đặt tên bằng AI" kế tiếp sinh lại; `aiLabel` hiện tại luôn lấy bản mới nhất của từng màn hình.

## 4. Vấn đề gặp phải

- **API key trong prompt bị che**: nhãn `apiKey` trông như khóa bí mật nên `SecretRedactor` che luôn giá trị (key của API) → AI không thể trả lại đúng khóa và ràng buộc / lỗi không được áp. Phát hiện nhờ `AiApiIT`; đổi sang mã ngắn `a1…`, ghi chú trong `prompts/README.md`.
- **JSON cụt do model thinking** (Gemini 3.x): ngân sách đầu ra 4096 bị thinking ăn mất → đầu ra bị loại. Sửa bằng JSON mode + 16384 token + `thinkingLevel = MINIMAL`.
- **Lớp biên dịch do IDE ghi đè `target/classes`** (đã gặp ở GĐ6): sau khi thêm lớp mới, extension Java của VS Code có thể biên dịch lại bằng trình biên dịch Eclipse vào `target/classes` với classpath cũ và gây `Unresolved compilation problems` khi chạy `spring-boot:run`. Cách xử lý: `clean install` ngay trước khi chạy; hoặc tắt auto build của extension Java.

---

## 5. Phần FE (Task 7.2) — làm sau khi BE xong

### 5.1. Việc đã làm

| Màn hình / thành phần | Nội dung |
| :--- | :--- |
| `core/api/ai.api.ts`, `core/ai/ai-status.ts` | Client I1–I3, I6, F4, E2; trạng thái AI dùng chung (nạp khi mở ứng dụng, nạp lại sau mỗi lần AI báo lỗi hoặc khi mở một thành phần). |
| Header | Đèn AI: **AI bật** (xanh), **AI đang nghỉ** (cam — mục đã thêm so với bản vẽ), **AI tắt** (xám), **AI lỗi** (đỏ, thiếu cấu hình). |
| Cột phải — Diễn giải / Kịch bản | Nhãn "✦ AI" (tím) khi đang hiện bản AI, ngược lại nhãn nguồn (Swagger / Javadoc / Câu khung / Bộ sinh dự phòng). Nút **Sinh lại** khi đã có bản AI; nút **Sinh bằng AI** khi chưa có bản lưu. Mục đích hiện chữ dần sau khi vừa sinh. Ràng buộc / lỗi: hiện dòng "AI chưa viết lại" khi chưa có bản AI. |
| Banner | Khi provider đang nghỉ (429 / 503 / sai khóa): thanh vàng ở đầu cột phải nêu lý do + giờ thử lại + "đang dùng nội dung tự sinh"; các nút sinh bị ẩn / mờ. Cấu hình thiếu khóa: thanh đỏ. |
| Popover liên kết | "Nhờ AI phân tích" (E2): mỗi ứng viên có nhãn Nên chọn / Không nên chọn / Chưa chắc + lý do; ứng viên "AI đề xuất" (đã kiểm tra) xuất hiện như một dòng chọn được; dòng "AI chỉ giải thích và đề xuất, không tự đổi liên kết". Nút mờ kèm giải thích khi AI tắt / đang nghỉ / mục đích bị tắt. |
| Thành phần chứa (màn hình / tab / hộp thoại / form) | Khối "Tên các phần tử" + nút **Đặt tên bằng AI** (F4 `ELEMENT_NAMING`); kết quả "Đã đặt tên n/m phần tử"; cây Frontend hiện tên AI kèm dấu ✦ (chỉ đổi cách hiển thị). |
| Trang Cấu hình AI | Nhà cung cấp (provider, model, tên biến khóa — không bao giờ giá trị khóa) + trạng thái Sẵn sàng / Đang nghỉ tới HH:mm / Đã tắt / Chưa cấu hình; Kiểm tra kết nối (I3); công tắc 5 mục đích (I2); token hôm nay / hạn mức (thanh); thống kê 7 / 30 ngày (4 ô số, biểu đồ cột theo mục đích, bảng — I6). |

### 5.2. Kiểm thử

- Web: **94 test** qua (thêm `ai-usage-model.spec.ts`, `ai-labels.spec.ts`), lint sạch, build sạch.
- Chrome headless (CDP) trên fixture, Engine thật + **máy chủ giả kiểu OpenAI** (đóng vai provider, trả JSON hợp lệ theo từng loại prompt; có chế độ trả 429): **29/29 kiểm tra qua** ở chế độ bình thường (sinh mục đích / dữ liệu mẫu / kịch bản, tải lại trang vẫn còn bản AI, đặt tên phần tử + dấu ✦ trên cây, popover Nhờ AI phân tích, trang Cấu hình AI: bật / tắt, kiểm tra kết nối, đổi 7 / 30 ngày) và **14/14** ở chế độ 429 (banner + giờ thử lại, đèn "AI đang nghỉ", nút Sinh bị ẩn, kịch bản "Khung câu tự sinh (AI đang nghỉ)", kiểm tra kết nối báo lý do). Một lần 429 chỉ tốn đúng 1 lời gọi tới provider (đã tắt retry của SDK).
- Engine: `clean install` **245 test** qua; `spring-boot:run` như người dùng, `/api/health` UP.
- **Chưa** chạy lại giao diện với khóa Gemini thật: khóa miễn phí đã hết hạn mức THEO NGÀY (429) sau các lần chạy thử trước — chính tình huống này đã được dùng để kiểm chứng đường 429 thật (banner hiện đúng "hết hạn mức theo ngày … thử lại sau HH:mm"). Cần chạy lại khi quota hồi để xem chất lượng chữ AI thật.

### 5.3. Điểm khác bản vẽ `UI_offline.html` (đã báo người dùng)

| # | Bản vẽ | Bản thật | Lý do |
| :--- | :--- | :--- | :--- |
| 1 | AI tự sinh khi mở thành phần | Hiện nội dung tự sinh + nút **"Sinh bằng AI"**; chỉ gọi AI khi bấm, kết quả lưu lại cho lần sau | Tiết kiệm hạn mức; mở nhiều thành phần không đốt token |
| 2 | Chữ hiện dần mỗi lần mở | Chỉ hiện dần ngay sau khi vừa sinh | Mở lại thì đọc từ bản đã lưu, không cần hiệu ứng |
| 3 | Đèn AI: bật / tắt / lỗi | Thêm **"AI đang nghỉ"** + banner đếm giờ thử lại | Xử lý 429 / 503 |
| 4 | 4 mục đích + `LEGACY_MATCH` | 5 mục đích (thêm "Đặt tên thành phần giao diện"), không có `LEGACY_MATCH` | Yêu cầu mới của người dùng; đối chiếu hệ thống cũ đang tạm hoãn |
| 5 | Không có | Khối "Tên các phần tử" + nút "Đặt tên bằng AI" ở thành phần chứa, dấu ✦ trên cây | Mục đích `ELEMENT_NAMING` |
| 6 | Nút "Kiểm tra kết nối" luôn bấm được | Mờ khi provider = none | Không có gì để kiểm tra |

## 6. Sửa sau khi người dùng thử thủ công (14/10)

Người dùng báo: bấm "Kiểm tra kết nối" lúc thì thành công lúc thì "hết hạn mức", chờ rất lâu không có tiến độ, và phải đổi trang rồi quay lại mới thấy kết quả. Điều tra bằng nhật ký `llm_call_logs` và một request thô tới Gemini:

- **Không phải dữ liệu giả**: mọi lần kiểm tra đều gọi Gemini thật. Gói miễn phí chỉ cho **20 lượt / ngày / model** (`GenerateRequestsPerDayPerProjectPerModel-FreeTier`, limit 20); mỗi lần "Kiểm tra kết nối" tốn 1 lượt nên hết nhanh, rồi các lần sau 429.
- **Chờ 16–40 giây khi lỗi**: SDK Gemini tự thử lại nhiều lần khi 429 (Spring AI đã tắt retry nhưng SDK có retry riêng). Đã đặt `HttpOptions` `attempts(1)` + timeout 60 s; lỗi 429 nay trả trong < 1 giây.
- **Giờ "thử lại sau" sai**: thông báo `retry in 18h26m53s` không được đọc (chỉ đọc giây) nên Engine tưởng 1 giờ rồi cắt 6 giờ. Nay đọc `h/m/s`, tối đa 24 giờ, kèm "gói đang dùng chỉ cho 20 lượt mỗi ngày".
- **Kiểm tra kết nối**: Engine chờ tối đa 15 giây rồi báo (trước đó không giới hạn).
- **Giao diện**: trạng thái kiểm tra chuyển vào `AiStatus` (service dùng chung): hiện vòng quay + số giây đã chờ + dòng nhắc "mỗi lần kiểm tra tốn 1 lượt"; đổi trang rồi quay lại vẫn thấy tiến độ / kết quả (kèm giờ kiểm tra); nút không bấm được khi đang chạy. Có test `ai-status.spec.ts`.

### 6.1. Lỗi hết giờ bị báo nhầm và cách hiển thị trạng thái AI (14/10, buổi chiều)

Người dùng thử sinh dữ liệu mẫu: lỗi `503 AI_UNAVAILABLE` "Không gọi được AI", và cả thẻ trạng thái lẫn mọi nút AI bị khóa như "đang nghỉ".

- **Nguyên nhân (từ `llm_call_logs`):** `MOCK_DATA` lỗi đúng **60,0 giây** = hạn chờ SDK vừa đặt; Gemini miễn phí hôm đó trả lời rất chậm (tạo mục đích: 37 giây cho 253 token). SDK khi hết giờ ném `GenAiIOException → InterruptedIOException("timeout") → SocketException("Socket closed")` (tái hiện bằng một máy chủ cục bộ không bao giờ trả lời), nhưng bộ phân loại chỉ xét lỗi sâu nhất ("Socket closed") nên báo `OTHER`.
- **Sửa phân loại:** duyệt cả chuỗi nguyên nhân tìm hết giờ (`InterruptedIOException`, `…Timeout…`, "timed out/timeout"). Hạn chờ của SDK nâng 60 → 120 giây để không bỏ ngang một lượt gọi vẫn đang được xử lý (mất lượt hạn mức mà không có kết quả).
- **Trạng thái AI theo đúng loại lỗi** (I1 `status`): `READY` (AI sẵn sàng) · `COOLING` (**429**: AI đạt giới hạn, chặn gọi tới giờ nhà cung cấp báo) · `OVERLOADED` (**503**: AI quá tải) · `ERROR` (lỗi khác: kết nối, hết giờ, khóa sai…: AI bị lỗi) · `OFF` · `NOT_CONFIGURED`. **Chỉ 429 mới chặn gọi AI**; 503 và lỗi khác chỉ hiển thị (banner + đèn, tự hết sau 2 phút hoặc khi một lần gọi thành công) và các nút AI vẫn bấm được để thử lại.
- **Giao diện:** nhãn đèn đổi theo trạng thái; banner đúng loại (vàng: đạt giới hạn / quá tải; đỏ: lỗi); các nút "Sinh bằng AI / Sinh lại / Đặt tên bằng AI" hiện số giây đã chờ ("Đang sinh… 23 giây").

### 6.2. Khung chờ (skeleton) (14/10, buổi tối)

Người dùng thấy sinh dữ liệu mẫu có thể mất hơn 60 giây và đề nghị thêm khung chờ cho từng phần dùng AI.

- **Khung chờ theo từng phần** (`node-panel.ts`): khi bấm sinh, phần tương ứng đổi nội dung cũ thành các thanh xám nhấp nháy kèm dòng "AI đang … N giây": **Mục đích** (đồng thời **Ràng buộc** và "Lỗi có thể gặp" vì cùng một lượt gọi), **Dữ liệu mẫu**, **Kịch bản** (danh sách bước), **Đặt tên phần tử**. Phần khác không bị ảnh hưởng (Sinh lại Mục đích thì JSON dữ liệu mẫu giữ nguyên). Trạng thái bận chuyển từ một giá trị sang danh sách (`busyKinds`).
- **Kiểm thử:** thử trên Chrome headless với máy chủ giả kiểu OpenAI (độ trễ 4 giây): 13/13 kiểm tra đạt. (Nút "Sinh cả Mục đích và Dữ liệu mẫu" làm kèm ở lượt này đã bị gỡ theo yêu cầu người dùng ngày 14/10.) Chỉ kiểm thử giao diện, không gọi Gemini thật (đã hết hạn mức ngày).
- **Về tốc độ:** Engine không làm chậm; thời gian nằm ở phía Gemini gói miễn phí (hôm 13/10 sinh dữ liệu mẫu chỉ 1,8 giây, hôm 14/10 tới hơn 60 giây). Chưa làm: dùng model nhẹ hơn cho từng mục đích (cần người dùng quyết định).


### 6.3. Rà soát trang Cấu hình AI (14/10, tối)

Người dùng yêu cầu xác nhận và sửa trạng thái, mô tả mục đích, hạn mức, thống kê và nhật ký.

- **Trạng thái "Sẵn sàng":** trước đây chỉ có nghĩa "có khóa API và chưa có lỗi nào được ghi nhận" — chưa gọi thử lần nào. Nay Engine **tự kiểm tra kết nối một lần khi khởi động** (`AiStartupCheck`, chạy nền, bỏ qua khi provider = none / thiếu khóa; tắt bằng `flowtracer.ai.startup-check=false`). Trong lúc kiểm tra, I1 trả `CHECKING` (đèn "AI đang kiểm tra", vẫn cho gọi AI; web tự hỏi lại mỗi 2 giây). Provider chưa từng thành công thì thông báo lỗi giữ nguyên tới khi có một lần gọi thành công (không tự về "Sẵn sàng" sau 2 phút). Tốn 1 lượt hạn mức mỗi lần Engine khởi động; mở / tải lại trang web **không** gọi AI.
- **Mục đích sử dụng AI:** bỏ nhãn mã tiếng Anh; viết lại mô tả cho khớp thực tế — cả năm mục đều chỉ chạy khi người dùng bấm nút, mô tả nêu nút nào, việc AI làm và ràng buộc.
- **Hạn mức:** con số 2.000.000 token/ngày là mặc định do dự án tự đặt, **không phải số của Gemini** (Google chỉ công bố loại hạn mức RPM / TPM / RPD, không công bố hạn mức token theo ngày cho gói miễn phí; con số cụ thể xem trong Google AI Studio). Mặc định nay là 0 (không đặt); `LLM_DAILY_TOKEN_LIMIT` chỉ còn là giới hạn tự đặt. Thẻ "Mức dùng hôm nay" hiện lượt gọi hôm nay kèm hạn mức theo ngày **do nhà cung cấp báo trong lỗi 429** (vd 20, lưu trong `app_settings` theo model) và token đã dùng.
- **Thống kê:**
  - **Token của "Kiểm tra kết nối" luôn 0:** do `test()` ghi cứng 0, 0. Nay ghi token thật do nhà cung cấp trả về (các dòng cũ giữ nguyên 0).
  - Đổi `CONNECTION_TEST` thành "Kiểm tra kết nối AI"; khoảng thời gian 1 ngày / 7 ngày / Tất cả; biểu đồ có thêm cột "Lượt gọi AI (lượt)" bên cạnh "Token (token)" kèm chú giải và đơn vị; bảng ghi đơn vị; bỏ dòng "Nguồn số liệu".
  - **Thống kê "Trúng cache" đã gỡ bỏ** theo yêu cầu (thẻ KPI, cột bảng, `cacheHits` trong I6). Ghi chú kỹ thuật: bộ đếm `ai_artifacts.hit_count` không bao giờ tăng vì các API đọc Diễn giải / Dữ liệu mẫu / Kịch bản chạy trong giao dịch chỉ-đọc nên lệnh ghi bị mất âm thầm; cột và đoạn ghi cũ vẫn giữ nguyên (không đổi CSDL).
  - Migration V4 (thêm `CACHE_HIT`, bỏ hai cột `hit_count` / `last_hit_at`) thực ra **đã chạy trên CSDL dev của người dùng** (16:30 ngày 14/10) nên không thể chỉ xóa file: Engine báo `missing column [hit_count]`. Đã giữ nguyên file V4 và thêm **V5** (với sự đồng ý của người dùng) khôi phục hai cột và xóa các dòng nhật ký `CACHE_HIT`; giá trị enum `CACHE_HIT` còn thừa trong CSDL (PostgreSQL không xóa được), không dùng. Quy ước: mọi thay đổi CSDL phải xin xác nhận trước.
- **Nhật ký Engine:** mọi lời gọi provider (kể cả kiểm tra kết nối) được ghi ra log của Engine (logger `flowtracer.ai`) gồm mục đích, đối tượng, prompt đã che secret, kết quả trả về, token, thời gian; lỗi và đầu ra bị loại cũng được ghi (`AiCallTrace`). `LLM_LOG_CONTENT=false` để chỉ ghi dòng tóm tắt. CSDL vẫn chỉ lưu mã băm; khóa API không bao giờ có trong log.
