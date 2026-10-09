package com.ctv.flowtracer.ir.trace;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Một mũi tên trên sequence diagram (bước cùng số trong tab Kịch bản).
 *
 * @param index số thứ tự, bắt đầu từ 1
 * @param from id participant nguồn
 * @param to id participant đích; bằng {@code from} nếu là bước tự xử lý (gom các đoạn kiểm tra trong một method)
 * @param kind loại quan hệ gốc; null với bước tự xử lý
 * @param label nhãn kỹ thuật ngắn (vd {@code POST /api/v1/categories}, {@code existsByCode(code)})
 * @param confidence mức tin cậy của edge gốc
 * @param reason lý do của mức tin cậy (hiện khi di chuột)
 * @param edgeRef tham chiếu edge gốc (id trong CSDL) để mở chi tiết
 * @param returning true nếu là chiều trả về
 * @param pinned true nếu bước đi qua liên kết người dùng đã chốt tay
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
        boolean returning,
        boolean pinned) {

    public TraceStep {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(confidence, "confidence");
    }

    /** true nếu là bước tự xử lý (mũi tên quay về chính cột đó). */
    public boolean isSelf() {
        return from.equals(to);
    }
}
