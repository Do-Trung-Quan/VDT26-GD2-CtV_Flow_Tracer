package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.ir.trace.TraceBlock;

/**
 * Một nhánh {@code else} của khối {@code alt}.
 *
 * @param label nhãn nhánh; "ngược lại" khi nhánh là phần còn lại sau điều kiện lỗi
 * @param fromStep số bước đầu tiên của nhánh
 */
public record TraceBranchResponse(String label, int fromStep) {

    public static TraceBranchResponse from(TraceBlock.Branch b) {
        return new TraceBranchResponse(b.label(), b.fromStep());
    }
}
