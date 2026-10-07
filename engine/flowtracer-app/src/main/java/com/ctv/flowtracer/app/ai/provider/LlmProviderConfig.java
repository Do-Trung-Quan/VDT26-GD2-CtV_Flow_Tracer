package com.ctv.flowtracer.app.ai.provider;

import com.ctv.flowtracer.app.config.FlowTracerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chọn {@link LlmProvider} theo {@code flowtracer.ai.provider}. API key chỉ đọc từ biến môi trường
 * (GEMINI_API_KEY / ANTHROPIC_API_KEY / LLM_API_KEY) — không log, không lưu CSDL.
 *
 * <p>TODO (phase AI): hiện thực SpringAiLlmProvider bằng Spring AI ChatClient cho gemini / anthropic /
 * openai-compatible. Hiện mọi cấu hình đều dùng Noop.
 */
@Configuration
public class LlmProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderConfig.class);

    @Bean
    public LlmProvider llmProvider(FlowTracerProperties properties) {
        String provider = properties.ai().provider();
        if (!"none".equalsIgnoreCase(provider)) {
            log.warn("Provider AI '{}' chưa được triển khai — tạm dùng chế độ tắt AI", provider);
        }
        return new NoopLlmProvider();
    }
}
