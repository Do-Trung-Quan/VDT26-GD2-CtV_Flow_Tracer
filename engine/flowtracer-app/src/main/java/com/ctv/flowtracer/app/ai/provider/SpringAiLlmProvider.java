package com.ctv.flowtracer.app.ai.provider;

import com.ctv.flowtracer.app.ai.service.SecretRedactor;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * {@link LlmProvider} dùng Spring AI {@link ChatModel} (Gemini / Anthropic / OpenAI-compatible). {@code chatModel} null nghĩa là
 * provider được chọn nhưng thiếu cấu hình bắt buộc (khóa API, model): {@link #available()} = false và mọi tính năng đi đường
 * tất định. Lỗi khi gọi được PHÂN LOẠI thành {@link AiProviderException} (hết hạn mức, quá tải, sai khóa…) để cổng AI quyết định
 * thời gian nghỉ; lỗi quá tải được thử lại đúng một lần. Không log nội dung prompt, không log khóa.
 */
public final class SpringAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmProvider.class);
    /** "retry in 18h26m53.0079s", "retryDelay": "66413s", "retry in 30s": thời gian nhà cung cấp yêu cầu chờ. */
    private static final Pattern RETRY_AFTER = Pattern.compile("(?i)retry(?:Delay)?\\W{1,8}(?:in\\s*)?((?:\\d+(?:\\.\\d+)?[hms]\\s*)+)");

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)([hms])");
    private static final Pattern QUOTA_LIMIT = Pattern.compile("(?i)limit:\\s*(\\d+)");
    private static final long OVERLOAD_RETRY_MS = 1500;

    private final String providerId;
    private final String model;
    private final ChatModel chatModel;

    public SpringAiLlmProvider(String providerId, String model, ChatModel chatModel) {
        this.providerId = providerId;
        this.model = model == null ? "" : model;
        this.chatModel = chatModel;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public boolean available() {
        return chatModel != null;
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (chatModel == null) {
            throw new AppException(ErrorCode.AI_UNAVAILABLE, "AI chưa được cấu hình đủ (khóa API / model)");
        }
        var messages = request.system() == null || request.system().isBlank()
                ? List.of(new UserMessage(request.user()))
                : List.of(new SystemMessage(request.system()), new UserMessage(request.user()));
        ChatResponse response = null;
        for (int attempt = 0; response == null; attempt++) {
            try {
                response = chatModel.call(new Prompt(List.copyOf(messages)));
            } catch (RuntimeException e) {
                AiProviderException classified = classify(e);
                log.warn("Provider {} lỗi ({}): {}", providerId, classified.kind(), classified.getMessage());
                if (classified.kind() == AiProviderException.Kind.OVERLOADED && attempt == 0 && sleep(OVERLOAD_RETRY_MS)) {
                    continue;
                }
                throw classified;
            }
        }
        String text = response.getResult() == null || response.getResult().getOutput() == null ? null : response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new AiProviderException(AiProviderException.Kind.OTHER, "Provider AI không trả nội dung", null, false);
        }
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int in = usage == null || usage.getPromptTokens() == null ? estimate(request.system()) + estimate(request.user()) : usage.getPromptTokens();
        int out = usage == null || usage.getCompletionTokens() == null ? estimate(text) : usage.getCompletionTokens();
        String used = response.getMetadata() == null || response.getMetadata().getModel() == null || response.getMetadata().getModel().isBlank()
                ? model
                : response.getMetadata().getModel();
        return new LlmResult(text, in, out, used);
    }

    /**
     * Phân loại lỗi của SDK theo mã trạng thái / từ khóa trong thông điệp của lỗi gốc. Thông điệp được che secret và cắt ngắn trước
     * khi giữ lại.
     */
    static AiProviderException classify(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String raw = String.valueOf(root.getMessage());
        String lower = raw.toLowerCase(Locale.ROOT);
        Duration retryAfter = null;
        Matcher m = RETRY_AFTER.matcher(raw);
        if (m.find()) {
            double seconds = 0;
            Matcher part = DURATION_PART.matcher(m.group(1));
            while (part.find()) {
                double n = Double.parseDouble(part.group(1));
                seconds += switch (part.group(2).toLowerCase(Locale.ROOT)) {
                    case "h" -> n * 3600;
                    case "m" -> n * 60;
                    default -> n;
                };
            }
            retryAfter = Duration.ofMillis(Math.round(seconds * 1000));
        }
        Matcher lim = QUOTA_LIMIT.matcher(raw);
        Integer quotaLimit = lim.find() ? Integer.valueOf(lim.group(1)) : null;
        String safe = abbreviate(new SecretRedactor().redact(raw));
        AiProviderException.Kind kind;
        if (hasStatus(lower, "429") || lower.contains("resource_exhausted") || lower.contains("quota") || lower.contains("rate limit")) {
            kind = AiProviderException.Kind.QUOTA;
        } else if (hasStatus(lower, "503") || hasStatus(lower, "502") || hasStatus(lower, "504") || lower.contains("overloaded")
                || lower.contains("unavailable")) {
            kind = AiProviderException.Kind.OVERLOADED;
        } else if (hasStatus(lower, "401") || hasStatus(lower, "403") || lower.contains("unauthenticated") || lower.contains("permission")
                || lower.contains("api key")) {
            kind = AiProviderException.Kind.AUTH;
        } else if (timedOut(error)) {
            kind = AiProviderException.Kind.TIMEOUT;
        } else if (hasStatus(lower, "400") || lower.contains("invalid argument") || lower.contains("not found")) {
            kind = AiProviderException.Kind.BAD_REQUEST;
        } else {
            kind = AiProviderException.Kind.OTHER;
        }
        boolean daily = kind == AiProviderException.Kind.QUOTA && (lower.contains("perday") || lower.contains("per day") || lower.contains("daily"));
        return new AiProviderException(kind, root.getClass().getSimpleName() + ": " + safe, retryAfter, daily, quotaLimit);
    }

    /**
     * Hết giờ ở BẤT KỲ tầng nào của chuỗi lỗi: SDK Gemini báo {@code InterruptedIOException: timeout} bọc ngoài {@code SocketException: Socket
     * closed}, nên chỉ nhìn lỗi sâu nhất sẽ không thấy chữ "timeout".
     */
    static boolean timedOut(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (t instanceof TimeoutException || t instanceof java.io.InterruptedIOException || t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.http.HttpTimeoutException || message.contains("timed out") || message.contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    /** Mã trạng thái HTTP đứng riêng (không nằm trong một số dài hơn). */
    private static boolean hasStatus(String text, String code) {
        return Pattern.compile("(?<!\\d)" + code + "(?!\\d)").matcher(text).find();
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String abbreviate(String text) {
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() > 240 ? oneLine.substring(0, 240) + "…" : oneLine;
    }

    /** Ước lượng khi provider không báo số token: khoảng 4 ký tự một token. */
    private static int estimate(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }
}
