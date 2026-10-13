package com.ctv.flowtracer.app.graph.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu đối chiếu API tìm được từ code với đặc tả OpenAPI (API C6).
 *
 * @param openapiUrl địa chỉ đặc tả của Backend đang chạy (vd {@code http://localhost:8080/v3/api-docs}); bỏ trống thì tự tìm
 *     file {@code openapi.*} / {@code swagger.*} trong Source Backend. Tool không chạy dự án của bạn: địa chỉ chỉ dùng khi Backend đã chạy sẵn
 */
public record OpenApiCheckRequest(
        @Size(max = 1000, message = "Địa chỉ đặc tả tối đa 1000 ký tự")
        @Pattern(regexp = "^$|^(?i)https?://\\S+$", message = "Địa chỉ đặc tả phải bắt đầu bằng http:// hoặc https://")
        String openapiUrl) {}
