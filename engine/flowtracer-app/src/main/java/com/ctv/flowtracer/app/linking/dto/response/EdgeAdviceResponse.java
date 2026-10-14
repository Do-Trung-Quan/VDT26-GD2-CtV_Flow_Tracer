package com.ctv.flowtracer.app.linking.dto.response;

import java.util.List;

/**
 * Lời giải thích của AI cho một liên kết chưa chắc chắn (API E2). AI chỉ giải thích; liên kết KHÔNG đổi.
 *
 * @param edgeId id của liên kết
 * @param state {@code GENERATED} (vừa gọi AI) hoặc {@code CACHED} (dùng bản đã lưu)
 * @param provider provider đã viết lời giải thích
 * @param model model đã viết lời giải thích
 * @param analyses lời giải thích cho từng ứng viên của liên kết
 * @param extra một ứng viên AI đề xuất thêm (đã qua kiểm tra là node có thật và đúng loại); null nếu không có
 */
public record EdgeAdviceResponse(
        String edgeId, String state, String provider, String model, List<CandidateAdvice> analyses, ExtraCandidate extra) {

    /**
     * Lời giải thích cho một ứng viên.
     *
     * @param nodeKey key của ứng viên
     * @param advice {@code SHOULD} (nên chọn), {@code SHOULD_NOT} (không nên chọn) hoặc {@code UNSURE}
     * @param reason lý do ngắn, dẫn chứng từ đoạn mã
     * @param evidence bằng chứng: từ / chuỗi cụ thể trong chuỗi gọi hoặc mã (đã được Engine kiểm tra là có thật); null nếu không có
     */
    public record CandidateAdvice(String nodeKey, String advice, String reason, String evidence) {}

    /**
     * Ứng viên AI đề xuất thêm.
     *
     * @param node thành phần (có thật trong IR)
     * @param reason vì sao AI đề xuất
     */
    public record ExtraCandidate(EdgeNodeResponse node, String reason) {}
}
