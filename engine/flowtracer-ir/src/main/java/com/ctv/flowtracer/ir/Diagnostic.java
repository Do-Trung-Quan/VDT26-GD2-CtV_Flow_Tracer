package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Cảnh báo/lỗi phát sinh khi phân tích (file lỗi cú pháp, tham chiếu không giải được…). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Diagnostic(Severity severity, String filePath, Integer line, String message) {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }
}
