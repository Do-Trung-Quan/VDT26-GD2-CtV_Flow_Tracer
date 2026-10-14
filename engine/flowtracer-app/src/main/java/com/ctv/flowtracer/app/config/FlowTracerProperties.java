package com.ctv.flowtracer.app.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình {@code flowtracer.*} (đọc từ biến môi trường — xem {@code .env.example}).
 *
 * @param sourceRoot thư mục gốc chứa mã nguồn cần phân tích (gắn read-only); mọi Source nằm bên trong
 * @param dataDir thư mục dữ liệu riêng của tool (cache dữ kiện…)
 */
@Validated
@ConfigurationProperties(prefix = "flowtracer")
public record FlowTracerProperties(
        @NotBlank String sourceRoot, @NotBlank String dataDir, @NotNull Scan scan, @NotNull Sidecar sidecar, @NotNull Ai ai) {

    /**
     * @param workers số luồng chạy pha extract song song (0 = theo số CPU)
     * @param maxFileSizeKb bỏ qua file lớn hơn ngưỡng
     * @param defaultMaxDepth độ sâu dò vết mặc định
     */
    public record Scan(@Min(0) int workers, @Min(1) int maxFileSizeKb, @Min(1) int defaultMaxDepth) {}

    /**
     * @param nodeCommand lệnh chạy Node.js
     * @param cliPath đường dẫn {@code analyzer-node/dist/cli.js}
     * @param timeout thời gian tối đa cho một lần phân tích một Source
     * @param maxOldSpaceMb giới hạn bộ nhớ heap của sidecar
     */
    public record Sidecar(@NotBlank String nodeCommand, @NotBlank String cliPath, @NotNull Duration timeout, @Min(256) int maxOldSpaceMb) {}

    /**
     * Cấu hình AI. API key KHÔNG nằm ở đây — chỉ đọc trực tiếp từ biến môi trường trong {@code LlmProviderConfig}.
     *
     * @param provider gemini | anthropic | openai-compatible | none
     * @param model tên model (rỗng = mặc định của provider)
     * @param baseUrl chỉ dùng với openai-compatible
     * @param dailyTokenLimit giới hạn token/ngày do người dùng tự đặt (0 = không đặt)
     */
    public record Ai(@NotBlank String provider, String model, String baseUrl, @Min(0) long dailyTokenLimit) {}
}
