package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.app.explanation.service.CodeSnippetService;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Chuỗi gọi từ thành phần người dùng đang xem (phần tử giao diện, hàm…) tới điểm gọi của một liên kết — chính là chỗ chứa bằng chứng để
 * chọn đúng đích (tên hàm, chuỗi hằng truyền vào). Một hàm dùng chung (vd {@code get(resource)}) có nhiều nơi gọi, nên chuỗi phải đi
 * từ thành phần xuất phát của luồng đang xem, không thể suy ngược từ điểm gọi.
 */
@Service
public class CallChainService {

    private static final int DEPTH = 12;
    private static final int MAX_STEPS = 8;
    private static final int READ_LINES = 400;
    private static final int MAX_LINE_CHARS = 220;
    private static final Pattern STRING_LITERAL = Pattern.compile("'([^'\\\\]{2,80})'|\"([^\"\\\\]{2,80})\"|`([^`$\\\\]{2,80})`");
    private static final Set<EdgeKind> FLOW =
            Set.of(EdgeKind.TRIGGERS, EdgeKind.CALLS, EdgeKind.SENDS, EdgeKind.HTTP_REQUEST, EdgeKind.HANDLED_BY);

    private final GraphQueryService graphQueries;
    private final CodeSnippetService codeSnippets;

    public CallChainService(GraphQueryService graphQueries, CodeSnippetService codeSnippets) {
        this.graphQueries = graphQueries;
        this.codeSnippets = codeSnippets;
    }

    /**
     * Một mắt xích của chuỗi.
     *
     * @param node thành phần (phần tử giao diện, hàm, lời gọi HTTP…)
     * @param line dòng mã tại thành phần này dẫn tới mắt xích kế tiếp (dòng gọi hàm kế, thẻ HTML của phần tử); null nếu không có
     */
    public record Step(IrNode node, String line) {}

    /** Chuỗi gọi theo thứ tự thực thi, từ thành phần xuất phát tới nơi chứa điểm gọi. */
    public record CallChain(List<Step> steps) {

        public static final CallChain EMPTY = new CallChain(List.of());

        public boolean isEmpty() {
            return steps.isEmpty();
        }

        /** Các từ có nghĩa trong tên các mắt xích và trong các chuỗi hằng truyền vào ở dòng gọi. */
        public List<String> words() {
            List<String> result = new ArrayList<>();
            for (Step s : steps) {
                result.addAll(CandidateHintRule.words(s.node().name()));
                if (s.line() != null) {
                    Matcher m = STRING_LITERAL.matcher(s.line());
                    while (m.find()) {
                        for (int g = 1; g <= 3; g++) {
                            if (m.group(g) != null) {
                                result.addAll(CandidateHintRule.words(m.group(g)));
                            }
                        }
                    }
                }
            }
            return result;
        }

        /** Văn bản đưa vào prompt: mỗi mắt xích một dòng. */
        public String render() {
            StringBuilder out = new StringBuilder();
            int n = 1;
            for (Step s : steps) {
                IrNode node = s.node();
                String label = node.displayName() != null && !node.displayName().isBlank() ? node.displayName() + " (" + node.name() + ")" : node.name();
                out.append(n++).append(". [").append(kindLabel(node.kind())).append("] ").append(label);
                if (s.line() != null) {
                    out.append(" — dòng mã: ").append(s.line());
                }
                out.append('\n');
            }
            return out.toString().stripTrailing();
        }

        private static String kindLabel(NodeKind kind) {
            return switch (kind) {
                case UI_ELEMENT -> "Phần tử giao diện";
                case HTTP_CALL -> "Lời gọi HTTP";
                case FE_METHOD, BE_METHOD -> "Hàm";
                default -> kind.name();
            };
        }
    }

    /**
     * @param startKey thành phần xuất phát của luồng đang xem (null / rỗng thì không có chuỗi)
     * @param targetKey node chứa điểm gọi của liên kết (đầu "từ" của liên kết)
     * @return chuỗi gọi; rỗng nếu không tìm thấy đường đi
     */
    public CallChain chain(UUID workspaceId, String startKey, String targetKey) {
        if (startKey == null || startKey.isBlank() || targetKey == null) {
            return CallChain.EMPTY;
        }
        try {
            Subgraph graph = graphQueries.subgraphFrom(workspaceId, startKey, DEPTH);
            List<IrNode> path = path(new GraphIndex(graph), startKey, targetKey);
            if (path.isEmpty()) {
                return CallChain.EMPTY;
            }
            List<IrNode> shown = path.size() > MAX_STEPS ? path.subList(path.size() - MAX_STEPS, path.size()) : path;
            List<Step> steps = new ArrayList<>();
            for (int i = 0; i < shown.size(); i++) {
                IrNode next = i + 1 < shown.size() ? shown.get(i + 1) : null;
                steps.add(new Step(shown.get(i), lineOf(workspaceId, shown.get(i), next)));
            }
            return new CallChain(steps);
        } catch (RuntimeException e) {
            return CallChain.EMPTY; // không có chuỗi thì AI vẫn phân tích như trước
        }
    }

    /** Đường ngắn nhất theo các cạnh luồng (bỏ cạnh chưa xác định); danh sách rỗng nếu không tới được. */
    static List<IrNode> path(GraphIndex graph, String startKey, String targetKey) {
        if (graph.node(startKey).isEmpty()) {
            return List.of();
        }
        Map<String, String> parent = new HashMap<>();
        Set<String> seen = new HashSet<>(Set.of(startKey));
        Deque<String> queue = new ArrayDeque<>(List.of(startKey));
        while (!queue.isEmpty() && !seen.contains(targetKey)) {
            String current = queue.poll();
            for (IrEdge e : graph.outgoing(current)) {
                if (e.toKey() == null || e.confidence() == Confidence.UNRESOLVED || !FLOW.contains(e.kind()) || !seen.add(e.toKey())) {
                    continue;
                }
                parent.put(e.toKey(), current);
                queue.add(e.toKey());
            }
        }
        if (!seen.contains(targetKey)) {
            return List.of();
        }
        List<IrNode> path = new ArrayList<>();
        for (String key = targetKey; key != null; key = parent.get(key)) {
            graph.node(key).ifPresent(path::add);
        }
        Collections.reverse(path);
        return path;
    }

    private String lineOf(UUID workspaceId, IrNode node, IrNode next) {
        List<String> lines;
        try {
            lines = codeSnippets.nodeLines(workspaceId, node.key(), READ_LINES);
        } catch (RuntimeException e) {
            return null;
        }
        if (lines.isEmpty()) {
            return null;
        }
        if (next == null || node.kind() == NodeKind.UI_ELEMENT || node.kind() == NodeKind.HTTP_CALL) {
            return clean(lines.get(0));
        }
        String simple = next.name().substring(next.name().lastIndexOf('.') + 1);
        int paren = simple.indexOf('(');
        String needle = (paren > 0 ? simple.substring(0, paren) : simple) + "(";
        return lines.stream().filter(l -> l.contains(needle)).findFirst().map(CallChainService::clean).orElse(null);
    }

    private static String clean(String line) {
        String text = line.strip();
        return text.length() > MAX_LINE_CHARS ? text.substring(0, MAX_LINE_CHARS) + "…" : text;
    }
}
