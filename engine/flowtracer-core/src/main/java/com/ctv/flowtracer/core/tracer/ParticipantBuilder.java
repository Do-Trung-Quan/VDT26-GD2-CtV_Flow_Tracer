package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.trace.Participant;
import java.util.List;

/**
 * Dựng các cột của diagram từ các node trên đường đi (§2.2.4 của overview): toàn bộ Frontend gộp một cột; mỗi
 * Controller / Service / Repository một cột ({@code ServiceImpl} gộp với interface); mọi bảng vào cột Database. Nhãn =
 * vai trò + tên nghiệp vụ ({@code @Tag} → tên module FE → tên class).
 */
public final class ParticipantBuilder {

    public List<Participant> build(Subgraph subgraph, List<String> visitedKeys) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
