package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.trace.TraceStep;

/**
 * Một mũi tên trên sequence diagram; số {@code index} cũng là số bước trong tab Kịch bản.
 *
 * @param index số thứ tự, từ 1
 * @param from id cột nguồn
 * @param to id cột đích; bằng {@code from} nếu là bước tự xử lý
 * @param kind loại quan hệ gốc; null với bước tự xử lý và chiều trả về
 * @param label nhãn kỹ thuật ngắn, tất định (tên API, tên hàm kèm tham số, thao tác dữ liệu)
 * @param confidence mức tin cậy; {@code UNRESOLVED} là lời gọi chưa xác định được đích
 * @param reason vì sao có mức tin cậy này; null nếu {@code CERTAIN} không cần giải thích
 * @param edgeRef mã cạnh trong IR để mở chi tiết liên kết; null với bước không ứng với một cạnh
 * @param returning true nếu là chiều trả về
 * @param pinned true nếu bước đi qua liên kết người dùng đã chốt tay
 * @param error true nếu là chiều trả về do lỗi lan lên (nằm trong nhánh lỗi của khối alt)
 * @param repeatOf số bước của lần gọi đầu tiên tới cùng hàm đích nếu đây là lần gọi lặp lại không vẽ lại phần bên trong;
 *     null nếu không phải
 */
public record TraceStepResponse(
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

    public static TraceStepResponse from(TraceStep s) {
        return new TraceStepResponse(
                s.index(), s.from(), s.to(), s.kind(), s.label(), s.confidence(), s.reason(), s.edgeRef(), s.returning(), s.pinned(), s.error(), s.repeatOf());
    }
}
