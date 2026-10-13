package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Các hàm Backend, bảng dữ liệu và hệ thống khác mà một API đi qua (theo thứ tự gặp), để rút ràng buộc, lỗi có thể gặp và danh
 * sách "Gọi tới". Chỉ đi theo liên kết đã xác định đích (kể cả suy luận); lời gọi chưa xác định không được đi vào — như Tracer.
 *
 * @param methods hàm xử lý rồi các hàm nó gọi, theo thứ tự thực thi
 * @param tables bảng mà các truy vấn đọc hoặc ghi
 * @param writtenTables bảng mà các truy vấn ghi vào
 * @param remoteCalls API khác trong Workspace hoặc hệ thống bên ngoài mà các lời gọi HTTP của luồng này tới
 */
public record BackendFlow(List<IrNode> methods, List<IrNode> tables, List<IrNode> writtenTables, List<IrNode> remoteCalls) {

    /** Giới hạn số hàm duyệt, chặn đồ thị bất thường. */
    private static final int MAX_METHODS = 300;

    public static BackendFlow of(IrNode endpoint, GraphIndex graph) {
        var methods = new ArrayList<IrNode>();
        var tables = new ArrayList<IrNode>();
        var written = new ArrayList<IrNode>();
        var remote = new ArrayList<IrNode>();
        Optional<IrNode> handler = graph.outgoing(endpoint.key()).stream()
                .filter(e -> e.kind() == EdgeKind.HANDLED_BY && e.toKey() != null)
                .findFirst()
                .flatMap(e -> graph.node(e.toKey()));
        if (handler.isEmpty()) {
            return new BackendFlow(methods, tables, written, remote);
        }
        Set<String> seen = new HashSet<>();
        Deque<IrNode> stack = new ArrayDeque<>();
        stack.push(handler.get());
        while (!stack.isEmpty() && methods.size() < MAX_METHODS) {
            IrNode method = stack.pop();
            if (!seen.add(method.key())) {
                continue;
            }
            methods.add(method);
            var next = new ArrayList<IrNode>();
            for (IrEdge e : graph.outgoing(method.key())) {
                if (e.toKey() == null || e.confidence() == Confidence.UNRESOLVED) {
                    continue;
                }
                if (e.kind() == EdgeKind.CALLS) {
                    graph.node(e.toKey()).filter(n -> n.kind() == NodeKind.BE_METHOD).ifPresent(next::add);
                } else if (e.kind() == EdgeKind.EXECUTES) {
                    for (IrEdge w : graph.outgoing(e.toKey())) {
                        if ((w.kind() == EdgeKind.READS || w.kind() == EdgeKind.WRITES) && w.toKey() != null) {
                            graph.node(w.toKey()).ifPresent(t -> {
                                addOnce(tables, t);
                                if (w.kind() == EdgeKind.WRITES) {
                                    addOnce(written, t);
                                }
                            });
                        }
                    }
                } else if (e.kind() == EdgeKind.SENDS) {
                    for (IrEdge link : graph.outgoing(e.toKey())) {
                        if (link.kind() == EdgeKind.HTTP_REQUEST && link.toKey() != null) {
                            graph.node(link.toKey()).ifPresent(t -> addOnce(remote, t));
                        }
                    }
                }
            }
            // đẩy ngược để hàm gọi trước được xử lý trước (ngăn xếp)
            for (int i = next.size() - 1; i >= 0; i--) {
                stack.push(next.get(i));
            }
        }
        return new BackendFlow(methods, tables, written, remote);
    }

    private static void addOnce(List<IrNode> list, IrNode node) {
        if (list.stream().noneMatch(n -> n.key().equals(node.key()))) {
            list.add(node);
        }
    }
}
