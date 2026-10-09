package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.app.workspace.entity.DetectedStack;

/**
 * Công nghệ đã nhận diện trên một Source.
 *
 * @param adapter bộ phân tích dùng cho Source, vd {@code spring}, {@code angular}
 * @param stackName tên công nghệ, vd "Spring Boot"
 * @param version phiên bản đọc được (có thể null)
 */
public record DetectedStackResponse(
        String adapter,
        String stackName,
        String version) {

    public static DetectedStackResponse from(DetectedStack stack) {
        return new DetectedStackResponse(stack.adapter(), stack.stackName(), stack.version());
    }
}
