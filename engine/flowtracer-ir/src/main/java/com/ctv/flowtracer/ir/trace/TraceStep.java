package com.ctv.flowtracer.ir.trace;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Một mũi tên trên sequence diagram (một bước trong tab Kịch bản).
 *
 * @param index số thứ tự, bắt đầu từ 1
 * @param from id participant nguồn
 * @param to id participant đích
 * @param kind loại quan hệ gốc
 * @param label nhãn kỹ thuật ngắn (vd {@code POST /api/v1/categories})
 * @param confidence mức tin cậy của edge gốc
 * @param reason lý do của mức tin cậy (hiện khi di chuột)
 * @param edgeRef tham chiếu edge gốc (id trong CSDL) để mở chi tiết
 * @param returning true nếu là chiều trả về
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceStep(
        int index,
        String from,
        String to,
        EdgeKind kind,
        String label,
        Confidence confidence,
        String reason,
        String edgeRef,
        boolean returning) {

    public TraceStep {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(confidence, "confidence");
    }
}
