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
 * @param kind loại quan hệ gốc; null với bước tự xử lý và chiều trả về
 * @param label nhãn kỹ thuật ngắn (vd {@code POST /api/v1/categories}, {@code existsByCode(code)})
 * @param confidence mức tin cậy của edge gốc
 * @param reason lý do của mức tin cậy (hiện khi di chuột)
 * @param edgeRef tham chiếu edge gốc (id trong CSDL) để mở chi tiết; null với bước không ứng với một edge
 * @param returning true nếu là chiều trả về
 * @param pinned true nếu bước đi qua liên kết người dùng đã chốt tay
 * @param error true nếu là chiều trả về do lỗi ({@code throw}) lan lên — nhánh lỗi của khối {@code alt}
 * @param repeatOf số bước của lần gọi đầu tiên tới cùng hàm đích nếu đây là lần gọi lặp lại mà phần bên trong KHÔNG được
 *     vẽ lại (chỉ có bước đi và bước trả về); null nếu không phải
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
        boolean pinned,
        boolean error,
        Integer repeatOf) {

    public TraceStep {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(confidence, "confidence");
    }

    /** true nếu là bước tự xử lý (mũi tên quay về chính cột đó). */
    public boolean isSelf() {
        return from.equals(to);
    }

    /** Bản sao với số thứ tự mới. */
    public TraceStep withIndex(int newIndex) {
        return new TraceStep(newIndex, from, to, kind, label, confidence, reason, edgeRef, returning, pinned, error, repeatOf);
    }

    /** Bản sao đánh dấu là lần gọi lặp lại của bước {@code firstStep}. */
    public TraceStep withRepeatOf(int firstStep) {
        return new TraceStep(index, from, to, kind, label, confidence, reason, edgeRef, returning, pinned, error, firstStep);
    }
}
