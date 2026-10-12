package com.ctv.flowtracer.app.tracing.service;

import com.ctv.flowtracer.app.tracing.model.CachedTrace;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bộ nhớ đệm kết quả dò vết theo {@code trace_id} (Dò vết không lưu CSDL). {@code trace_id} tính được từ đầu vào nên mất
 * đệm thì chỉ cần dò lại. Giới hạn số mục (bỏ mục lâu không dùng nhất) và thời gian sống, để một lần quét mới không để lại
 * kết quả cũ quá lâu.
 */
@Component
public class TraceCache {

    static final int MAX_ENTRIES = 256;
    static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

    private record CachedEntry(UUID workspaceId, CachedTrace value, Instant createdAt) {}

    private final Map<String, CachedEntry> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /** Chỉ trả về nếu mục thuộc đúng Workspace và chưa hết hạn. */
    public synchronized Optional<CachedTrace> get(UUID workspaceId, String traceId) {
        CachedEntry entry = entries.get(traceId);
        if (entry == null || !entry.workspaceId().equals(workspaceId)) {
            return Optional.empty();
        }
        if (entry.createdAt().plus(TIME_TO_LIVE).isBefore(Instant.now())) {
            entries.remove(traceId);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized void put(UUID workspaceId, String traceId, CachedTrace value) {
        entries.put(traceId, new CachedEntry(workspaceId, value, Instant.now()));
    }
}
