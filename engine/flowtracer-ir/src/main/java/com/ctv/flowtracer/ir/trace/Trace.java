package com.ctv.flowtracer.ir.trace;

import java.util.List;
import java.util.Objects;

/** Kết quả dò vết — đầu vào để vẽ sequence diagram và sinh kịch bản. Xem §2.2.4 của overview. */
public record Trace(
        String traceId,
        String startKey,
        DetailLevel detailLevel,
        List<Participant> participants,
        List<TraceStep> steps,
        List<TraceBlock> blocks,
        List<String> warnings) {

    public Trace {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(startKey, "startKey");
        Objects.requireNonNull(detailLevel, "detailLevel");
        participants = participants == null ? List.of() : List.copyOf(participants);
        steps = steps == null ? List.of() : List.copyOf(steps);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
