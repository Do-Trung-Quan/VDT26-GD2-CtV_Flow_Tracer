package com.ctv.flowtracer.app.ai.entity;

/** Kết quả một lần gọi AI. */
public enum LlmCallStatus {
    OK,
    ERROR,
    REJECTED_BY_VALIDATION,
    QUOTA_EXCEEDED
}
