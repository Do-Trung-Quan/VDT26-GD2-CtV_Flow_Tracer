package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.trace.Trace;

/** CỐT LÕI: dò vết End-to-End theo callOrder từ node xuất phát (phần tử UI, endpoint, hoặc luồng chỉ UI). */
public final class Tracer {

    /**
     * @param graph đồ thị con quanh node xuất phát (đã nạp từ CSDL)
     * @return trace gồm participant, các bước (cả chiều trả về), khối alt/opt và cảnh báo
     */
    public Trace trace(Subgraph graph, TraceRequest request) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
