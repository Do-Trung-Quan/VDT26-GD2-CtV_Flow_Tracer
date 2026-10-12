package com.ctv.flowtracer.core.graph;

import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Một phần đồ thị do ứng dụng nạp từ CSDL quanh node xuất phát.
 *
 * @param edgeRefs mã tham chiếu của từng edge (id trong CSDL), tra bằng {@link #edgeId(IrEdge)}; edge không có mã thì
 *     bước tương ứng trên diagram không có {@code edgeRef}
 * @param pinned các edge đã được thay đích theo liên kết chốt tay (do {@code OverrideApplier} điền), tra bằng
 *     {@link #edgeId(IrEdge)}
 */
public record Subgraph(List<IrNode> nodes, List<IrEdge> edges, Map<String, String> edgeRefs, Set<String> pinned) {

    public Subgraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        edgeRefs = edgeRefs == null ? Map.of() : Map.copyOf(edgeRefs);
        pinned = pinned == null ? Set.of() : Set.copyOf(pinned);
    }

    public Subgraph(List<IrNode> nodes, List<IrEdge> edges) {
        this(nodes, edges, Map.of(), Set.of());
    }

    /** Khóa nhận diện một edge trong đồ thị: {@code từ|loại|đến|thứ tự gọi}. */
    public static String edgeId(IrEdge edge) {
        return edge.fromKey() + "|" + edge.kind() + "|" + edge.toKey() + "|" + edge.callOrder();
    }

    public String edgeRef(IrEdge edge) {
        return edgeRefs.get(edgeId(edge));
    }

    public boolean isPinned(IrEdge edge) {
        return pinned.contains(edgeId(edge));
    }
}
