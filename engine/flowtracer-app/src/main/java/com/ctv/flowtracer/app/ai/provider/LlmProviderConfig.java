package com.ctv.flowtracer.app.ai.provider;

import com.ctv.flowtracer.app.config.FlowTracerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chọn {@link LlmProvider} theo {@code flowtracer.ai.provider} (CLAUDE.md §3.6). API key chỉ đọc từ biến môi trường
 * ({@code GEMINI_API_KEY} / {@code ANTHROPIC_API_KEY} / {@code LLM_API_KEY}) — không log, không lưu CSDL, không trả về API. Thiếu
 * khóa hoặc model thì provider ở trạng thái không khả dụng, ứng dụng vẫn chạy đầy đủ bằng nội dung tất định.
 */
@Configuration
public class LlmProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderConfig.class);

    static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    static final String DEFAULT_ANTHROPIC_MODEL = "claude-haiku-5-5";
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final int GEMINI_MAX_OUTPUT_TOKENS = 16384;
    /** Gemini miễn phí có lúc trả lời rất chậm (hàng chục giây); hết giờ giữa chừng vừa mất lượt gọi vừa không có kết quả. */
    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(120);

    @Bean
    public LlmProvider llmProvider(FlowTracerProperties properties, Environment env) {
        String id = properties.ai().provider() == null ? "none" : properties.ai().provider().toLowerCase();
        String model = properties.ai().model();
        try {
            return switch (id) {
                case "gemini" -> gemini(env.getProperty("GEMINI_API_KEY"), blank(model) ? DEFAULT_GEMINI_MODEL : model);
                case "anthropic" -> anthropic(env.getProperty("ANTHROPIC_API_KEY"), blank(model) ? DEFAULT_ANTHROPIC_MODEL : model);
                case "openai-compatible" -> openAi(env.getProperty("LLM_API_KEY"), properties.ai().baseUrl(), model);
                case "none" -> new NoopLlmProvider();
                default -> {
                    log.warn("Provider AI '{}' không hợp lệ (gemini | anthropic | openai-compatible | none) — tắt AI", id);
                    yield new NoopLlmProvider();
                }
            };
        } catch (RuntimeException e) {
            log.warn("Không khởi tạo được provider AI '{}' ({}) — tắt AI", id, e.getClass().getSimpleName());
            return new SpringAiLlmProvider(id, model, null);
        }
    }

    private static LlmProvider gemini(String apiKey, String model) {
        if (blank(apiKey)) {
            return missing("gemini", model, "GEMINI_API_KEY");
        }
        // SDK Gemini mặc định tự thử lại khi 429 / 503 (mỗi lần lỗi treo 15–40 giây không báo gì): chỉ gọi MỘT lần, quá 120 giây thì bỏ.
        // Việc nghỉ / thử lại do cổng AI quyết định (ProviderHealth).
        var http = com.google.genai.types.HttpOptions.builder()
                .timeout((int) TIMEOUT.toMillis())
                .retryOptions(com.google.genai.types.HttpRetryOptions.builder().attempts(1))
                .build();
        var client = com.google.genai.Client.builder().apiKey(apiKey).httpOptions(http).build();
        // JSON mode; model "thinking" tốn token đầu ra và làm chậm → hạ mức suy nghĩ để đầu ra không bị cắt cụt (JSON hỏng)
        var builder = GoogleGenAiChatOptions.builder().model(model).temperature(0.2).maxOutputTokens(GEMINI_MAX_OUTPUT_TOKENS)
                .responseMimeType("application/json");
        if (model.startsWith("gemini-3")) {
            builder.thinkingLevel(org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel.MINIMAL);
        }
        var options = builder.build();
        // Không thử lại trong Spring AI (mặc định chờ 20–60 giây khi 429 làm người dùng đứng hình): cổng AI tự quyết định nghỉ / thử lại
        var noRetry = new org.springframework.core.retry.RetryTemplate(org.springframework.core.retry.RetryPolicy.withMaxRetries(0));
        ChatModel chat = GoogleGenAiChatModel.builder().genAiClient(client).options(options).retryTemplate(noRetry).build();
        return new SpringAiLlmProvider("gemini", model, chat);
    }

    // maxRetries(0): SDK không tự thử lại khi 429 / 503 (mặc định chờ nhiều giây làm người dùng đứng hình); cổng AI tự quyết định nghỉ / thử lại
    private static LlmProvider anthropic(String apiKey, String model) {
        if (blank(apiKey)) {
            return missing("anthropic", model, "ANTHROPIC_API_KEY");
        }
        var options = AnthropicChatOptions.builder().apiKey(apiKey).model(model).temperature(0.2).maxTokens(MAX_OUTPUT_TOKENS)
                .maxRetries(0).timeout(TIMEOUT).build();
        ChatModel chat = AnthropicChatModel.builder().options(options).build();
        return new SpringAiLlmProvider("anthropic", model, chat);
    }

    private static LlmProvider openAi(String apiKey, String baseUrl, String model) {
        if (blank(apiKey) || blank(baseUrl) || blank(model)) {
            return missing("openai-compatible", model, "LLM_API_KEY / LLM_BASE_URL / LLM_MODEL");
        }
        var options = OpenAiChatOptions.builder().apiKey(apiKey).baseUrl(baseUrl).model(model).temperature(0.2)
                .maxTokens(MAX_OUTPUT_TOKENS).maxRetries(0).timeout(TIMEOUT).build();
        ChatModel chat = OpenAiChatModel.builder().options(options).build();
        return new SpringAiLlmProvider("openai-compatible", model, chat);
    }

    private static LlmProvider missing(String id, String model, String variable) {
        log.warn("Provider AI '{}' được chọn nhưng thiếu cấu hình trong biến môi trường {} — AI không khả dụng", id, variable);
        return new SpringAiLlmProvider(id, model, null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
