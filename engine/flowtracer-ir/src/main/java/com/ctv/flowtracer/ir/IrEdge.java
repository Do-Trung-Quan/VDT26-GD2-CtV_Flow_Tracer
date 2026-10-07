package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

/**
 * Một quan hệ có hướng giữa hai node.
 *
 * <p>Bất biến (xem CLAUDE.md §3.3, §3.7):
 *
 * <ul>
 *   <li>Phải có {@code location} hoặc {@code reason}.
 *   <li>{@code toKey} chỉ được null khi {@code confidence = UNRESOLVED} hoặc khi còn {@code candidates} chờ xử lý.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrEdge(
        String fromKey,
        String toKey,
        EdgeKind kind,
        Confidence confidence,
        String reason,
        Integer callOrder,
        Guard guard,
        List<Candidate> candidates,
        SourceLocation location) {

    public IrEdge {
        Objects.requireNonNull(fromKey, "fromKey");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(confidence, "confidence");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (location == null && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Edge phải có location hoặc reason: " + fromKey + " -" + kind + "->");
        }
        if (toKey == null && confidence != Confidence.UNRESOLVED && candidates.isEmpty()) {
            throw new IllegalArgumentException("Edge thiếu đích nhưng không phải UNRESOLVED và không có ứng viên: " + fromKey);
        }
    }

    /** Edge chắc chắn, có vị trí trong code. */
    public static IrEdge certain(String fromKey, String toKey, EdgeKind kind, Integer callOrder, SourceLocation location) {
        return new IrEdge(fromKey, toKey, kind, Confidence.CERTAIN, null, callOrder, null, List.of(), location);
    }
}
