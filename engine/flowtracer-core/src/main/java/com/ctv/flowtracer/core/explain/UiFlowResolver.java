package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Một thao tác trên giao diện dẫn tới đâu: các API Backend nó gọi (theo liên kết {@code HTTP_REQUEST} đã xác định), hộp thoại
 * mở ra và màn hình chuyển tới. Đi theo sự kiện → hàm xử lý → hàm gọi tiếp theo thứ tự thực thi; bỏ lời gọi chạy khi hộp thoại
 * đóng ({@code branch = afterClosed}) và không đi vào bên trong hộp thoại / màn hình được mở — như dò vết.
 *
 * @param apis API Backend được gọi, không trùng, theo thứ tự gặp
 * @param unresolved lời gọi HTTP mà chưa xác định được API đích (chưa nối, nhiều ứng viên ngang nhau, hoặc đã loại bỏ) — phần tử
 *     VẪN gọi máy chủ, chỉ là chưa biết gọi API nào; chốt tay liên kết thì chuyển sang {@code apis}
 * @param opens hộp thoại được mở
 * @param navigates màn hình được chuyển tới
 */
public record UiFlowResolver(List<IrNode> apis, List<IrNode> unresolved, List<IrNode> opens, List<IrNode> navigates) {

    public static UiFlowResolver of(IrNode element, GraphIndex graph) {
        var flow = new Collector(graph);
        flow.visit(element.key());
        return new UiFlowResolver(flow.apis, flow.unresolved, flow.opens, flow.navigates);
    }

    private static final class Collector {
        private final GraphIndex graph;
        private final Set<String> visited = new HashSet<>();
        private final List<IrNode> apis = new ArrayList<>();
        private final List<IrNode> unresolved = new ArrayList<>();
        private final List<IrNode> opens = new ArrayList<>();
        private final List<IrNode> navigates = new ArrayList<>();

        Collector(GraphIndex graph) {
            this.graph = graph;
        }

        void visit(String key) {
            if (!visited.add(key)) {
                return;
            }
            for (IrEdge e : graph.outgoing(key)) {
                if (e.toKey() == null || e.confidence() == Confidence.UNRESOLVED) {
                    continue;
                }
                if (e.guard() != null && "afterClosed".equals(e.guard().branch())) {
                    continue;
                }
                graph.node(e.toKey()).ifPresent(target -> follow(e.kind(), target));
            }
        }

        private void follow(EdgeKind kind, IrNode target) {
            switch (kind) {
                case SENDS -> {
                    noteIfUnresolved(target);
                    visit(target.key());
                }
                case TRIGGERS, CALLS -> visit(target.key());
                case HTTP_REQUEST -> {
                    if (target.kind() == NodeKind.API_ENDPOINT && apis.stream().noneMatch(a -> a.key().equals(target.key()))) {
                        apis.add(target);
                    }
                }
                case OPENS -> add(opens, target);
                case NAVIGATES -> add(navigates, target);
                default -> {
                    // CONTAINS, HANDLED_BY…: không thuộc luồng thao tác của phần tử
                }
            }
        }

        /** Lời gọi HTTP không có liên kết nào tới một API / hệ thống đã xác định. */
        private void noteIfUnresolved(IrNode httpCall) {
            boolean linked = graph.outgoing(httpCall.key()).stream()
                    .anyMatch(e -> e.kind() == EdgeKind.HTTP_REQUEST && e.toKey() != null && e.confidence() != Confidence.UNRESOLVED);
            if (!linked) {
                add(unresolved, httpCall);
            }
        }

        private static void add(List<IrNode> list, IrNode node) {
            if (list.stream().noneMatch(n -> n.key().equals(node.key()))) {
                list.add(node);
            }
        }
    }
}
