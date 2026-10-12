package com.ctv.flowtracer.app.tracing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu dò vết luồng (API D1).
 *
 * @param startKey node_key của thành phần xuất phát; Giai đoạn này hỗ trợ endpoint Backend ({@code java:endpoint:…})
 * @param maxDepth số cấp gọi tối đa; bỏ trống thì dùng mặc định của Workspace, rồi mặc định hệ thống (12)
 */
public record CreateTraceRequest(
        @NotBlank(message = "Cần chọn thành phần xuất phát để dò vết")
        @Size(max = 1000, message = "Mã thành phần xuất phát tối đa 1000 ký tự")
        String startKey,

        @Min(value = 1, message = "Độ sâu dò vết tối thiểu là 1")
        @Max(value = 50, message = "Độ sâu dò vết tối đa là 50")
        Integer maxDepth) {}
