package com.ctv.flowtracer.app.ai.provider;

/**
 * Một trong 3 interface được phép trong ứng dụng (CLAUDE.md §3.2b). Chỉ {@code AiGateway} được gọi interface này.
 */
public interface LlmProvider {

    /** gemini | anthropic | openai-compatible | none */
    String providerId();

    /** false khi provider = none hoặc thiếu cấu hình bắt buộc. */
    boolean available();

    LlmResult complete(LlmRequest request);
}
