package com.ctv.flowtracer.app.linking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Đường đi từ thành phần xuất phát tới điểm gọi: phải theo đúng luồng đang xem, không lẫn các nơi khác cũng gọi hàm dùng chung. */
class CallChainServiceTest {

    private static IrNode node(String key, NodeKind kind, String name) {
        return new IrNode(key, kind, name, null, SourceLocation.of("f.ts", 1), Map.of());
    }

    private static IrEdge edge(String from, String to, EdgeKind kind, Confidence confidence) {
        return new IrEdge(from, to, kind, confidence, null, 1, null, List.of(), SourceLocation.of("f.ts", 1));
    }

    private final List<IrNode> nodes = List.of(
            node("ui1", NodeKind.UI_ELEMENT, "loadMostBorrowed"), node("ui2", NodeKind.UI_ELEMENT, "loadFeatured"),
            node("fa1", NodeKind.FE_METHOD, "HomeFacade.loadMostBorrowed"), node("fa2", NodeKind.FE_METHOD, "HomeFacade.loadFeatured"),
            node("rp1", NodeKind.FE_METHOD, "Repo.getMostBorrowedBooks"), node("rp2", NodeKind.FE_METHOD, "Repo.getFeaturedBooks"),
            node("get", NodeKind.FE_METHOD, "Repo.get"), node("http", NodeKind.HTTP_CALL, "GET /x/{resource}"));
    private final List<IrEdge> edges = List.of(
            edge("ui1", "fa1", EdgeKind.TRIGGERS, Confidence.CERTAIN), edge("ui2", "fa2", EdgeKind.TRIGGERS, Confidence.CERTAIN),
            edge("fa1", "rp1", EdgeKind.CALLS, Confidence.CERTAIN), edge("fa2", "rp2", EdgeKind.CALLS, Confidence.CERTAIN),
            edge("rp1", "get", EdgeKind.CALLS, Confidence.CERTAIN), edge("rp2", "get", EdgeKind.CALLS, Confidence.CERTAIN),
            edge("get", "http", EdgeKind.SENDS, Confidence.CERTAIN));

    @Test
    void followsTheCallersOfTheFlowBeingViewedNotTheOtherCallersOfTheSharedHelper() {
        var graph = new GraphIndex(new Subgraph(nodes, edges, Map.of(), Set.of()));

        assertThat(CallChainService.path(graph, "ui1", "http")).extracting(IrNode::key).containsExactly("ui1", "fa1", "rp1", "get", "http");
        assertThat(CallChainService.path(graph, "ui2", "http")).extracting(IrNode::key).containsExactly("ui2", "fa2", "rp2", "get", "http");
    }

    @Test
    void unreachableTargetOrUnresolvedEdgesGiveNoPath() {
        var broken = new ArrayList<>(edges);
        broken.set(4, edge("rp1", "get", EdgeKind.CALLS, Confidence.UNRESOLVED));
        var graph = new GraphIndex(new Subgraph(nodes, broken, Map.of(), Set.of()));

        assertThat(CallChainService.path(graph, "ui1", "http")).isEmpty();
        assertThat(CallChainService.path(graph, "zzz", "http")).isEmpty();
    }

    @Test
    void chainWordsIncludeFunctionNamesAndStringLiteralsPassedAlong() {
        var chain = new CallChainService.CallChain(List.of(
                new CallChainService.Step(nodes.get(4), "return this.get('most-borrowed-books', reference);"),
                new CallChainService.Step(nodes.get(6), null)));

        assertThat(chain.words()).contains("most", "borrowed", "books");
        assertThat(chain.render()).contains("Repo.getMostBorrowedBooks").contains("dòng mã: return this.get('most-borrowed-books'");
    }
}
