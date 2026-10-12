package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.discovery.ContentHash;
import java.util.stream.Collectors;

/**
 * trace_id tất định = hash(startKey, maxDepth, scanRunId, liên kết chốt tay, bộ lọc nhiễu) — tính lại được, không cần
 * lưu. Chốt tay và bộ lọc nhiễu đều làm đổi luồng nên phải nằm trong khóa.
 */
public final class TraceIds {

    private TraceIds() {}

    public static String of(TraceRequest request) {
        String overrides = request.overrides().stream()
                .map(o -> o.fromKey() + ">" + o.toKey() + ":" + o.edgeKind() + ":" + o.decision())
                .sorted()
                .collect(Collectors.joining(","));
        String filters = request.noiseFilters().stream().map(f -> f.pattern()).sorted().collect(Collectors.joining(","));
        String raw = String.join(
                "|",
                request.startKey(),
                String.valueOf(request.maxDepth()),
                String.valueOf(request.scanRunId()),
                overrides,
                filters);
        return ContentHash.sha256(raw).substring(0, 32);
    }
}
