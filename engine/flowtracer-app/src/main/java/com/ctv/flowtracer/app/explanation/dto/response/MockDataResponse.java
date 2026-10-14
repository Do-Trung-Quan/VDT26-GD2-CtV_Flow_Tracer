package com.ctv.flowtracer.app.explanation.dto.response;

import java.util.List;

/**
 * Dữ liệu mẫu của một API (API F2): sinh từ CẤU TRÚC của DTO, không đọc dữ liệu thật.
 *
 * @param apiKey node_key của API
 * @param request thân yêu cầu mẫu; API không có thân thì là tham số đường dẫn / query mẫu
 * @param response phản hồi mẫu; null nếu API không trả nội dung
 * @param source nguồn sinh: {@code RULE} (bộ sinh tất định) hoặc {@code AI} (từ Giai đoạn 7)
 * @param problems chỗ dữ liệu mẫu không khớp cấu trúc DTO (rỗng nếu khớp hoàn toàn)
 * @param ai trạng thái nội dung AI (xem {@link AiInfoResponse})
 */
public record MockDataResponse(
        String apiKey,
        Object request,
        Object response,
        String source,
        List<String> problems,
        AiInfoResponse ai) {}
