package com.ctv.flowtracer.app.ai.provider;

import java.util.Objects;

/**
 * Yêu cầu gửi tới provider. Nội dung đã được che secret trước khi tạo request này.
 *
 * @param jsonSchema schema đầu ra bắt buộc (JSON mode); null nếu trả văn bản tự do
 */
public record LlmRequest(String system, String user, String jsonSchema, int maxOutputTokens) {

    public LlmRequest {
        Objects.requireNonNull(user, "user");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens phải > 0");
        }
    }
}
