package com.ctv.flowtracer.app.ai.dto.response;

import java.util.List;

/**
 * Cấu hình AI hiện tại (API I1). KHÔNG bao giờ chứa khóa API.
 *
 * @param provider nhà cung cấp: {@code gemini}, {@code anthropic}, {@code openai-compatible} hoặc {@code none}
 * @param model model đang dùng (rỗng khi provider = none)
 * @param status {@code CHECKING} (đang kiểm tra kết nối tự động khi Engine vừa khởi động — chưa biết provider có chạy được không),
 *     {@code READY} (đã gọi thành công, hoặc chưa có lỗi nào), {@code COOLING} (hết hạn mức 429 — không gọi AI tới {@code cooldownUntil}), {@code OVERLOADED}
 *     (lần gọi gần nhất gặp 503 quá tải — vẫn cho thử lại), {@code ERROR} (lần gọi gần nhất lỗi khác: kết nối, hết giờ, khóa sai… — vẫn cho
 *     thử lại; tự về {@code READY} sau vài phút hoặc khi một lần gọi thành công), {@code OFF} (provider = none), {@code NOT_CONFIGURED}
 *     (thiếu khóa / model)
 * @param statusMessage câu giải thích trạng thái, tiếng Việt
 * @param cooldownUntil thời điểm được gọi lại (ISO-8601); chỉ có khi {@code status = COOLING}
 * @param cooldownReason loại lỗi gần nhất ({@code QUOTA}, {@code OVERLOADED}, {@code AUTH}, {@code BAD_REQUEST}, {@code TIMEOUT},
 *     {@code OTHER}); null khi không có lỗi gần đây
 * @param keyVariable tên BIẾN MÔI TRƯỜNG chứa khóa API (không phải giá trị), vd {@code GEMINI_API_KEY}; null khi provider = none
 * @param dailyTokenLimit giới hạn token mỗi ngày do CHÍNH NGƯỜI DÙNG đặt bằng {@code LLM_DAILY_TOKEN_LIMIT} (0 = không đặt; nhà cung cấp
 *     không công bố hạn mức token theo ngày cho gói miễn phí)
 * @param tokensToday token đã dùng hôm nay (vào + ra, giờ máy)
 * @param requestsToday số lượt gọi provider đã xử lý hôm nay (giờ máy)
 * @param dailyRequestLimit hạn mức lượt gọi theo ngày do nhà cung cấp báo trong lỗi 429 gần nhất cho model đang dùng (0 = chưa biết,
 *     vì nhà cung cấp chỉ báo khi chạm giới hạn)
 * @param purposes bật / tắt từng mục đích
 */
public record AiSettingsResponse(
        String provider,
        String model,
        String status,
        String statusMessage,
        String cooldownUntil,
        String cooldownReason,
        String keyVariable,
        long dailyTokenLimit,
        long tokensToday,
        long requestsToday,
        long dailyRequestLimit,
        List<AiPurposeSettingResponse> purposes) {}
