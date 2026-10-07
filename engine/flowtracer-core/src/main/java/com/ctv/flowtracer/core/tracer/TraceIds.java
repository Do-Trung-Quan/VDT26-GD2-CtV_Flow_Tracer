package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.discovery.ContentHash;

/** trace_id tất định = hash(startKey, detailLevel, maxDepth, scanRunId) — tính lại được, không cần lưu. */
public final class TraceIds {

    private TraceIds() {}

    public static String of(TraceRequest request) {
        String raw = String.join("|", request.startKey(), request.detailLevel().name(), String.valueOf(request.maxDepth()),
                String.valueOf(request.scanRunId()));
        return ContentHash.sha256(raw).substring(0, 32);
    }
}
