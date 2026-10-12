package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.IrEdge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Áp liên kết chốt tay lên đồ thị TRƯỚC khi dò vết — liên kết chốt tay luôn thắng liên kết tự động.
 *
 * <ul>
 *   <li>{@code REJECT} (từ, tới, loại): bỏ edge đúng như vậy; nếu {@code tới} chỉ là một ứng viên của edge khác thì bỏ
 *       ứng viên đó.
 *   <li>{@code PIN} (từ, tới, loại): edge cùng {@code từ} và {@code loại} mà đích hoặc một ứng viên là {@code tới} được
 *       đổi thành edge chắc chắn tới {@code tới} (lý do "Chốt tay"), đánh dấu {@code pinned}; ứng viên khác bị bỏ.
 *       Nếu {@code từ} có nhiều lời gọi cùng ứng viên đó thì tất cả cùng được chốt.
 * </ul>
 */
public final class OverrideApplier {

    static final String PINNED_REASON = "Chốt tay";

    public Subgraph apply(Subgraph graph, List<OverrideRule> overrides) {
        if (overrides.isEmpty()) {
            return graph;
        }
        List<IrEdge> edges = new ArrayList<>(graph.edges());
        Map<String, String> refs = new HashMap<>(graph.edgeRefs());
        Set<String> pinned = new HashSet<>(graph.pinned());
        // REJECT trước, PIN sau: một liên kết vừa bị bỏ vừa được chốt thì chốt tay thắng (lần áp sau cùng)
        overrides.stream().filter(o -> o.decision() == OverrideRule.Decision.REJECT).forEach(o -> reject(edges, o));
        overrides.stream().filter(o -> o.decision() == OverrideRule.Decision.PIN).forEach(o -> pin(edges, refs, pinned, o));
        return new Subgraph(graph.nodes(), edges, refs, pinned);
    }

    private static void reject(List<IrEdge> edges, OverrideRule rule) {
        for (int i = edges.size() - 1; i >= 0; i--) {
            IrEdge e = edges.get(i);
            if (!e.fromKey().equals(rule.fromKey()) || e.kind() != rule.edgeKind()) {
                continue;
            }
            if (java.util.Objects.equals(rule.toKey(), e.toKey())) {
                edges.remove(i);
            } else if (e.candidates().stream().anyMatch(c -> c.nodeKey().equals(rule.toKey()))) {
                List<Candidate> rest = e.candidates().stream().filter(c -> !c.nodeKey().equals(rule.toKey())).toList();
                edges.set(i, withCandidates(e, rest));
            }
        }
    }

    private static void pin(List<IrEdge> edges, Map<String, String> refs, Set<String> pinned, OverrideRule rule) {
        for (int i = 0; i < edges.size(); i++) {
            IrEdge e = edges.get(i);
            if (!e.fromKey().equals(rule.fromKey()) || e.kind() != rule.edgeKind()) {
                continue;
            }
            boolean sameTarget = rule.toKey().equals(e.toKey());
            boolean isCandidate = e.candidates().stream().anyMatch(c -> c.nodeKey().equals(rule.toKey()));
            if (!sameTarget && !isCandidate) {
                continue;
            }
            IrEdge fixed = new IrEdge(
                    e.fromKey(), rule.toKey(), e.kind(), Confidence.CERTAIN, PINNED_REASON, e.callOrder(), e.guard(), List.of(), e.location());
            String oldId = Subgraph.edgeId(e);
            String newId = Subgraph.edgeId(fixed);
            if (refs.containsKey(oldId)) {
                refs.put(newId, refs.get(oldId));
            }
            pinned.add(newId);
            edges.set(i, fixed);
        }
    }

    private static IrEdge withCandidates(IrEdge e, List<Candidate> candidates) {
        return new IrEdge(e.fromKey(), e.toKey(), e.kind(), e.confidence(), e.reason(), e.callOrder(), e.guard(), candidates, e.location());
    }
}
