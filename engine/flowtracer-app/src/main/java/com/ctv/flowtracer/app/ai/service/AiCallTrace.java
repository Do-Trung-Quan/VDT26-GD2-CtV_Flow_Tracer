package com.ctv.flowtracer.app.ai.service;

import com.ctv.flowtracer.app.ai.provider.LlmRequest;
import com.ctv.flowtracer.app.ai.provider.LlmResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ghi ra nhật ký của Engine (logger {@code flowtracer.ai}) MỌI lời gọi provider AI: mục đích, đối tượng, prompt đã gửi, kết quả trả về.
 * Prompt ở đây là bản ĐÃ che secret ({@link SecretRedactor}) — đúng nội dung rời khỏi máy. Khóa API không bao giờ có trong đây. Chỉ ghi ra
 * log của tiến trình; CSDL vẫn chỉ lưu mã băm ({@code llm_call_logs}). Đặt {@code LLM_LOG_CONTENT=false} để chỉ ghi dòng tóm tắt, bỏ nội
 * dung prompt / kết quả.
 */
@Component
public class AiCallTrace {

    private static final Logger log = LoggerFactory.getLogger("flowtracer.ai");

    private final boolean content;

    public AiCallTrace(@Value("${flowtracer.ai.log-content:true}") boolean content) {
        this.content = content;
    }

    /** Một lời gọi chuẩn bị gửi đi. */
    public void request(String purpose, String target, String provider, String model, LlmRequest request) {
        var text = new StringBuilder("AI >> GỌI ").append(purpose).append(" | đối tượng: ").append(target == null ? "-" : target)
                .append(" | provider: ").append(provider).append(" | model: ").append(model);
        if (content) {
            text.append("\n----- prompt hệ thống -----\n").append(request.system() == null ? "" : request.system())
                    .append("\n----- prompt người dùng -----\n").append(request.user());
        }
        log.info("{}", text);
    }

    /** Provider đã trả lời (chưa kiểm tra đầu ra). */
    public void response(String purpose, LlmResult result, int latencyMs) {
        var text = new StringBuilder("AI << TRẢ VỀ ").append(purpose).append(" | ").append(seconds(latencyMs)).append(" giây | token vào ")
                .append(result.inputTokens()).append(" / ra ").append(result.outputTokens()).append(" | model: ").append(result.model());
        if (content) {
            text.append("\n----- kết quả -----\n").append(result.content());
        }
        log.info("{}", text);
    }

    /** Provider báo lỗi hoặc không phản hồi. */
    public void failure(String purpose, String reason, int latencyMs) {
        log.warn("AI !! LỖI {} | {} giây | {}", purpose, seconds(latencyMs), reason);
    }

    /** Provider trả lời nhưng đầu ra không đạt kiểm tra nên không dùng. */
    public void rejected(String purpose, String reason) {
        log.warn("AI !! BỊ LOẠI {} | đầu ra không đạt kiểm tra: {}", purpose, reason);
    }

    private static String seconds(int ms) {
        return String.format(java.util.Locale.ROOT, "%.1f", ms / 1000.0);
    }
}
