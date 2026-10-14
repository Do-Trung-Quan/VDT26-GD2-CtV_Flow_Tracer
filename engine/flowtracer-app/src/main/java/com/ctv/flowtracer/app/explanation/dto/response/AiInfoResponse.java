package com.ctv.flowtracer.app.explanation.dto.response;

/**
 * Trạng thái nội dung AI của một phần trong tab (Mục đích + ràng buộc + lỗi, Dữ liệu mẫu hoặc Kịch bản).
 *
 * @param state {@code APPLIED} (đang hiện nội dung do AI viết, đã lưu), {@code MISSING} (AI dùng được nhưng chưa có bản lưu:
 *     giao diện gọi F4 để sinh), {@code OFF} (AI tắt, mục đích bị tắt hoặc chưa cấu hình: đang hiện nội dung tự sinh từ mã nguồn)
 * @param message lý do khi {@code OFF}; null với các trạng thái khác
 * @param provider provider đã viết nội dung (khi {@code APPLIED})
 * @param model model đã viết nội dung (khi {@code APPLIED})
 */
public record AiInfoResponse(String state, String message, String provider, String model) {

    public static AiInfoResponse applied(String provider, String model) {
        return new AiInfoResponse("APPLIED", null, provider, model);
    }

    public static AiInfoResponse missing() {
        return new AiInfoResponse("MISSING", null, null, null);
    }

    public static AiInfoResponse off(String message) {
        return new AiInfoResponse("OFF", message, null, null);
    }
}
