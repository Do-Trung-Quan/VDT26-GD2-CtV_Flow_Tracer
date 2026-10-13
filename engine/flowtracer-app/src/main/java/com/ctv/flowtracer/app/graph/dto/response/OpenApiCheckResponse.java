package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;

/**
 * Báo cáo đối chiếu API tìm được từ code với đặc tả OpenAPI (API C6).
 *
 * @param source nguồn đặc tả đã dùng: đường dẫn file trong repo hoặc địa chỉ URL
 * @param specVersion phiên bản đặc tả, vd "OpenAPI 3.0.3", "Swagger 2.0"
 * @param codeTotal số API tìm được từ code
 * @param specTotal số API trong đặc tả
 * @param matched API có ở cả hai phía
 * @param onlyInCode API chỉ có trong code (đặc tả có thể thiếu hoặc lỗi thời)
 * @param onlyInSpec API chỉ có trong đặc tả (tool có thể bỏ sót, hoặc đặc tả lỗi thời)
 */
public record OpenApiCheckResponse(
        String source,
        String specVersion,
        int codeTotal,
        int specTotal,
        List<OpenApiOperationResponse> matched,
        List<OpenApiOperationResponse> onlyInCode,
        List<OpenApiOperationResponse> onlyInSpec) {}
