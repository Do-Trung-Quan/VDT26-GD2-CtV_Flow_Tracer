package com.ctv.flowtracer.core.graph;

import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import java.util.List;

/** Một phần đồ thị do ứng dụng nạp từ CSDL quanh node xuất phát. */
public record Subgraph(List<IrNode> nodes, List<IrEdge> edges) {

    public Subgraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
