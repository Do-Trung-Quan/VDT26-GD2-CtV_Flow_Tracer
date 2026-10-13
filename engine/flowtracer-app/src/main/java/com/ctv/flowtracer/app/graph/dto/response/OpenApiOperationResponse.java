package com.ctv.flowtracer.app.graph.dto.response;

/**
 * Một API trong báo cáo đối chiếu OpenAPI.
 *
 * @param method phương thức HTTP viết hoa
 * @param path đường dẫn như ghi ở phía có API (code hoặc đặc tả)
 * @param nodeKey node_key của API trong kết quả quét; null với API chỉ có trong đặc tả
 * @param summary mô tả ngắn: {@code @Operation} / Javadoc ở phía code, {@code summary} ở phía đặc tả; có thể null
 */
public record OpenApiOperationResponse(String method, String path, String nodeKey, String summary) {}
