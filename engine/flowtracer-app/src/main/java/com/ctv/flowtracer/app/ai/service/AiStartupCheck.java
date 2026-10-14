package com.ctv.flowtracer.app.ai.service;

import com.ctv.flowtracer.app.ai.provider.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tự "Kiểm tra kết nối" MỘT lần khi Engine khởi động (chạy nền, không chặn việc khởi động) để trạng thái AI phản ánh đúng thực tế thay
 * vì mặc định "sẵn sàng" chỉ vì có khóa API. Tốn 1 lượt gọi AI mỗi lần Engine khởi động; bỏ qua khi provider = none / thiếu khóa. Tắt
 * bằng {@code flowtracer.ai.startup-check=false}.
 */
@Component
public class AiStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(AiStartupCheck.class);

    private final AiSettingsService settings;
    private final ProviderHealth health;
    private final boolean enabled;

    public AiStartupCheck(
            LlmProvider provider,
            AiSettingsService settings,
            ProviderHealth health,
            @Value("${flowtracer.ai.startup-check:true}") boolean enabled) {
        this.settings = settings;
        this.health = health;
        this.enabled = enabled && provider.available();
        if (this.enabled) {
            health.checking(true); // đánh dấu ngay từ lúc tạo bean để giao diện không kịp thấy "sẵn sàng" giả
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        if (!enabled) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                var result = settings.test();
                log.info("Kiểm tra kết nối AI khi khởi động: {} ({} ms) {}", result.ok() ? "thành công" : "thất bại", result.latencyMs(),
                        result.ok() ? "" : result.message());
            } catch (RuntimeException e) {
                log.warn("Kiểm tra kết nối AI khi khởi động lỗi: {}", e.toString());
            } finally {
                health.checking(false);
            }
        });
    }
}
