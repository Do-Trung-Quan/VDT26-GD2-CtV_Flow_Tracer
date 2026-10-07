package com.ctv.flowtracer.ir;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Kết quả phân tích một Source: phần bản đồ IR do một adapter sinh ra. Cấu trúc JSON khớp
 * {@code contracts/ir-fragment.schema.json}.
 */
public record IrFragment(
        String adapterId,
        List<IrNode> nodes,
        List<IrEdge> edges,
        List<LinkRule> detectedLinkRules,
        List<Diagnostic> diagnostics) {

    private static final Comparator<IrEdge> EDGE_ORDER = Comparator.comparing(IrEdge::fromKey)
            .thenComparing(IrEdge::kind)
            .thenComparing(IrEdge::callOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(IrEdge::toKey, Comparator.nullsLast(Comparator.naturalOrder()));

    public IrFragment {
        Objects.requireNonNull(adapterId, "adapterId");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        detectedLinkRules = detectedLinkRules == null ? List.of() : List.copyOf(detectedLinkRules);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static IrFragment empty(String adapterId) {
        return new IrFragment(adapterId, List.of(), List.of(), List.of(), List.of());
    }

    /** Bản sao đã sắp xếp ổn định (node theo key, edge theo from/kind/callOrder/to) — bảo đảm tính tất định. */
    public IrFragment sorted() {
        return new IrFragment(
                adapterId,
                nodes.stream().sorted(Comparator.comparing(IrNode::key)).toList(),
                edges.stream().sorted(EDGE_ORDER).toList(),
                detectedLinkRules,
                diagnostics);
    }
}
