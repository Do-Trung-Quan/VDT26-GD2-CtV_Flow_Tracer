package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Gom các đoạn tự xử lý liên tiếp trong thân một method (gọi hàm private, hàm của entity, hàm tiện ích…) thành MỘT bước
 * tự xử lý — mũi tên quay về chính cột đó, nhãn là tên các hàm chính ({@code checkPermission(), normalize()}). Mức tin
 * cậy của bước là mức thấp nhất trong các lời gọi được gom.
 */
final class SelfStepBuilder {

    /** Tối đa số tên hiện trong nhãn; phần còn lại thay bằng "…". */
    static final int MAX_NAMES = 4;

    private final Set<String> names = new LinkedHashSet<>();
    private String participantId;
    private String weakestReason;
    private Confidence weakest = Confidence.RUNTIME_VERIFIED;
    private String edgeRef;
    private boolean pinned;

    /** Ghi một lời gọi tự xử lý. Nếu đang gom cho cột khác thì bước cũ phải được {@link #flush()} trước. */
    void add(String participantId, String name, Confidence confidence, String reason, String edgeRef, boolean pinned) {
        this.participantId = participantId;
        names.add(name);
        if (this.edgeRef == null) {
            this.edgeRef = edgeRef;
        }
        if (confidence.rank() < weakest.rank()) {
            weakest = confidence;
            weakestReason = reason;
        }
        this.pinned |= pinned;
    }

    boolean isPendingFor(String otherParticipantId) {
        return !names.isEmpty() && !participantId.equals(otherParticipantId);
    }

    boolean isEmpty() {
        return names.isEmpty();
    }

    /** Bước tự xử lý đang gom (nếu có) và đặt lại trạng thái. */
    Optional<TraceStep> flush() {
        if (names.isEmpty()) {
            return Optional.empty();
        }
        var shown = names.stream().limit(MAX_NAMES).toList();
        String label = String.join(", ", shown) + (names.size() > MAX_NAMES ? ", …" : "");
        var step = new TraceStep(
                0, participantId, participantId, null, label, weakest, weakest == Confidence.CERTAIN ? null : weakestReason,
                edgeRef, false, pinned, false, null);
        names.clear();
        participantId = null;
        weakest = Confidence.RUNTIME_VERIFIED;
        weakestReason = null;
        edgeRef = null;
        pinned = false;
        return Optional.of(step);
    }
}
