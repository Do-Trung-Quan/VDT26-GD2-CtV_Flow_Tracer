package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.List;

/**
 * Gom các đoạn kiểm tra liên tiếp trong thân một method ({@code if … throw}, lời gọi kiểm tra quyền, hàm private) thành
 * một "bước tự xử lý" (mũi tên quay về chính cột đó), nhãn là tên các hàm/điều kiện chính.
 */
public final class SelfStepBuilder {

    public List<TraceStep> build(IrNode method, String participantId) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
