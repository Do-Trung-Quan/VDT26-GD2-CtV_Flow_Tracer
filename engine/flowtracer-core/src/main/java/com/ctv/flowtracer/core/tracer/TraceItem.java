package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;

/**
 * Phần tử của dòng sự kiện do {@link TraceRun} phát ra theo thứ tự thực thi: một bước (chưa đánh số) hoặc dấu mở / chuyển
 * nhánh / đóng khối. {@link BlockBuilder} đổi dòng này thành danh sách bước đã đánh số và danh sách khối.
 */
sealed interface TraceItem {

    /**
     * Một mũi tên; số thứ tự do {@link BlockBuilder} gán.
     *
     * @param id mã tạm của bước trong dòng sự kiện (để bước lặp lại tham chiếu tới lần gọi đầu)
     * @param repeatOfId mã tạm của lần gọi đầu tiên nếu đây là lần gọi lặp lại không vẽ lại phần bên trong; 0 nếu không
     */
    record StepItem(TraceStep step, int id, int repeatOfId) implements TraceItem {}

    /** Mở khối {@code alt}/{@code opt}/{@code loop}; {@code label} là điều kiện của nhánh đầu. */
    record Open(TraceBlock.Type type, String label) implements TraceItem {}

    /** Chuyển sang nhánh {@code else} của khối {@code alt} đang mở gần nhất. */
    record Else(String label) implements TraceItem {}

    /** Đóng khối đang mở gần nhất. */
    record Close() implements TraceItem {}
}
