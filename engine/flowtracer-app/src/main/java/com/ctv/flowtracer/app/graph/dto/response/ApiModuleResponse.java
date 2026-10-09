package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Một module trên cây API Backend.
 *
 * @param name tên module
 * @param nameSource nguồn tên: {@code TAG} ({@code @Tag} của Swagger), {@code CONTROLLER} (tên lớp Controller) hoặc
 *     {@code PACKAGE}
 * @param sourceId Source chứa module
 * @param sourceName tên Source
 * @param endpointCount số endpoint trong module
 * @param endpoints các endpoint, sắp theo đường dẫn rồi phương thức
 */
public record ApiModuleResponse(
        String name,
        String nameSource,
        UUID sourceId,
        String sourceName,
        int endpointCount,
        List<EndpointNodeResponse> endpoints) {}
