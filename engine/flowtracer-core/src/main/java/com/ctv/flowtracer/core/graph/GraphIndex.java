package com.ctv.flowtracer.core.graph;

import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Đồ thị trong bộ nhớ: tra node theo key, cạnh đi/đến, lọc theo kind. Thứ tự duyệt luôn ổn định. */
public final class GraphIndex {

    private final Map<String, IrNode> nodesByKey = new TreeMap<>();
    private final Map<String, List<IrEdge>> outgoing = new TreeMap<>();
    private final Map<String, List<IrEdge>> incoming = new TreeMap<>();

    public GraphIndex(Subgraph graph) {
        graph.nodes().forEach(n -> nodesByKey.put(n.key(), n));
        for (IrEdge e : graph.edges()) {
            outgoing.computeIfAbsent(e.fromKey(), k -> new ArrayList<>()).add(e);
            if (e.toKey() != null) {
                incoming.computeIfAbsent(e.toKey(), k -> new ArrayList<>()).add(e);
            }
        }
        Comparator<IrEdge> byOrder = Comparator.comparing(IrEdge::callOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IrEdge::kind)
                .thenComparing(IrEdge::toKey, Comparator.nullsLast(Comparator.naturalOrder()));
        outgoing.values().forEach(l -> l.sort(byOrder));
        incoming.values().forEach(l -> l.sort(Comparator.comparing(IrEdge::fromKey)));
    }

    public Optional<IrNode> node(String key) {
        return Optional.ofNullable(nodesByKey.get(key));
    }

    /** Cạnh đi, đã sắp theo callOrder. */
    public List<IrEdge> outgoing(String key) {
        return outgoing.getOrDefault(key, List.of());
    }

    public List<IrEdge> incoming(String key) {
        return incoming.getOrDefault(key, List.of());
    }

    public List<IrNode> nodesOfKind(NodeKind kind) {
        return nodesByKey.values().stream().filter(n -> n.kind() == kind).toList();
    }
}
