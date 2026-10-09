package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.common.sse.SseHub;
import com.ctv.flowtracer.app.scan.dto.response.ScanProgressResponse;
import com.ctv.flowtracer.app.scan.dto.response.ScanRunResponse;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Gửi tiến độ quét lên giao diện qua SSE (kênh {@code scan:<id>}): sự kiện {@code progress} trong lúc chạy, {@code done}
 * khi kết thúc. Số liệu là số mục thật đã xử lý; gửi tối đa mỗi {@value #MIN_INTERVAL_MS} ms trong cùng giai đoạn để
 * không làm nghẽn trình duyệt.
 */
@Component
public class ProgressReporter {

    public static final String EVENT_PROGRESS = "progress";
    public static final String EVENT_DONE = "done";

    static final long MIN_INTERVAL_MS = 150;

    private final SseHub sseHub;
    private final Map<UUID, Long> lastSentAt = new ConcurrentHashMap<>();

    public ProgressReporter(SseHub sseHub) {
        this.sseHub = sseHub;
    }

    public static String channel(UUID scanRunId) {
        return "scan:" + scanRunId;
    }

    /** Bắt đầu một giai đoạn — luôn gửi. */
    void stage(UUID scanRunId, ScanStage stage, int total, String message) {
        send(scanRunId, stage, 0, total, message, true);
    }

    /** Tiến độ trong giai đoạn — bỏ bớt nếu gửi quá dày, trừ mục cuối cùng. */
    void progress(UUID scanRunId, ScanStage stage, int processed, int total, String message) {
        send(scanRunId, stage, processed, total, message, processed >= total);
    }

    /** Kết thúc lần quét: gửi trạng thái cuối và đóng kênh. */
    public void done(ScanRunResponse result) {
        lastSentAt.remove(result.id());
        sseHub.publish(channel(result.id()), EVENT_DONE, result);
        sseHub.complete(channel(result.id()));
    }

    private void send(UUID scanRunId, ScanStage stage, int processed, int total, String message, boolean force) {
        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(scanRunId);
        if (!force && last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        lastSentAt.put(scanRunId, now);
        sseHub.publish(channel(scanRunId), EVENT_PROGRESS, new ScanProgressResponse(
                scanRunId,
                stage,
                stage.label(),
                stage.ordinal() + 1,
                ScanStage.values().length,
                processed,
                total,
                message));
    }
}
