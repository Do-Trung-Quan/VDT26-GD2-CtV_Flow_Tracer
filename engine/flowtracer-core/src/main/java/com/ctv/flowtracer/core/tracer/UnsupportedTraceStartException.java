package com.ctv.flowtracer.core.tracer;

/** Node xuất phát không dò vết được (loại chưa hỗ trợ, hoặc luồng cần phần nối FE–BE chưa có). Thông điệp hiển thị cho người dùng. */
public final class UnsupportedTraceStartException extends RuntimeException {

    public UnsupportedTraceStartException(String message) {
        super(message);
    }
}
