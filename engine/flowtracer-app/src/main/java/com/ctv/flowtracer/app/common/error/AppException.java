package com.ctv.flowtracer.app.common.error;

import java.util.Map;

/** Lỗi nghiệp vụ mang {@link ErrorCode}. Message phải là tiếng Việt thân thiện và KHÔNG chứa thông tin bí mật. */
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public AppException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public AppException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
