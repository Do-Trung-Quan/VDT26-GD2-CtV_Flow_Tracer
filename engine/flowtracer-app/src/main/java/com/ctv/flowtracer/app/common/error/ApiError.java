package com.ctv.flowtracer.app.common.error;

import java.util.Map;

/** Thân response lỗi chuẩn: {@code { "error": { code, message, details } }}. */
public record ApiError(Body error) {

    public record Body(String code, String message, Map<String, Object> details) {}

    public static ApiError of(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiError(new Body(code.name(), message, details == null ? Map.of() : details));
    }
}
