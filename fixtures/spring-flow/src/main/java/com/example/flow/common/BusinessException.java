package com.example.flow.common;

/** Lỗi nghiệp vụ; {@code errorKey} là khóa thông báo i18n. */
public class BusinessException extends RuntimeException {

    private final String errorKey;

    public BusinessException(String errorKey) {
        super(errorKey);
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
