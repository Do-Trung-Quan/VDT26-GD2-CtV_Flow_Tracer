package com.ctv.flowtracer.app.common.error;

import org.springframework.http.HttpStatus;

/** Mã lỗi nghiệp vụ trả về trong {@code { "error": { "code", "message", "details" } }}. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    PATH_OUTSIDE_SOURCE_ROOT(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND),
    NODE_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRACE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EDGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    LINK_OVERRIDE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SCAN_NOT_FOUND(HttpStatus.NOT_FOUND),
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    WORKSPACE_NAME_TAKEN(HttpStatus.CONFLICT),
    FEATURE_NAME_TAKEN(HttpStatus.CONFLICT),
    SCAN_IN_PROGRESS(HttpStatus.CONFLICT),
    SCAN_NOT_RUNNING(HttpStatus.CONFLICT),
    SOURCE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    NO_PROJECT_ROOT(HttpStatus.UNPROCESSABLE_CONTENT),
    TRACE_START_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT),
    EDGE_HAS_NO_CANDIDATES(HttpStatus.UNPROCESSABLE_CONTENT),
    UNSUPPORTED_IMPORT_VERSION(HttpStatus.UNPROCESSABLE_CONTENT),
    SIDECAR_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    AI_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
