package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;

/**
 * Cây API Backend {@code Module → Endpoint} (API C1). Số liệu đếm trực tiếp từ IR đã lưu của lần quét gần nhất.
 *
 * @param moduleCount số module
 * @param endpointCount tổng số endpoint
 * @param modules các module, sắp theo tên
 */
public record BackendTreeResponse(
        int moduleCount,
        int endpointCount,
        List<ApiModuleResponse> modules) {}
