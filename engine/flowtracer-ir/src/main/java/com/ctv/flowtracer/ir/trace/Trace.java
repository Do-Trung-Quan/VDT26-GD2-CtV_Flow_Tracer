package com.ctv.flowtracer.ir.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

/**
 * Kết quả dò vết — đầu vào để vẽ sequence diagram và sinh kịch bản. Diagram chỉ có một mức hiển thị. Xem §2.2.4 của
 * overview.
 *
 * @param uiOnly true nếu luồng chỉ gồm User và Frontend, không gọi máy chủ (thao tác như mở hộp thoại, chuyển trang);
 *     giao diện ghi "Không gọi máy chủ"
 */
public record Trace(
        String traceId,
        String startKey,
        List<Participant> participants,
        List<TraceStep> steps,
        List<TraceBlock> blocks,
        List<String> warnings,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean uiOnly) {

    /** Luồng có gọi máy chủ (mọi luồng xuất phát từ API Backend). */
    public Trace(
            String traceId,
            String startKey,
            List<Participant> participants,
            List<TraceStep> steps,
            List<TraceBlock> blocks,
            List<String> warnings) {
        this(traceId, startKey, participants, steps, blocks, warnings, false);
    }

    public Trace {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(startKey, "startKey");
        participants = participants == null ? List.of() : List.copyOf(participants);
        steps = steps == null ? List.of() : List.copyOf(steps);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
