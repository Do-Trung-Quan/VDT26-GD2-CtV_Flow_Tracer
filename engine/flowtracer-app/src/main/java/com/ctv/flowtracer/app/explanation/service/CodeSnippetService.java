package com.ctv.flowtracer.app.explanation.service;

import com.ctv.flowtracer.app.explanation.dto.response.CodeTabResponse;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.linking.service.LinkOverrideService;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.core.tracer.OverrideApplier;
import com.ctv.flowtracer.core.tracer.OverrideRule;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đoạn mã nguồn theo từng lớp trong luồng của một thành phần (API C5), cho popup "Xem code". Đi theo đúng đồ thị mà dò vết đi
 * (đã áp liên kết chốt tay): phần tử → hàm xử lý → hàm Frontend → lời gọi HTTP → API → Controller → Service → Repository. Chỉ đọc,
 * chỉ qua {@link SourceReader}, chỉ file thuộc Source của Workspace.
 */
@Service
public class CodeSnippetService {

    private static final int FLOW_DEPTH = 12;
    /** Số dòng tối đa của một đoạn (cả phần ngữ cảnh); hàm dài hơn thì cắt ở đây. */
    static final int MAX_LINES = 160;
    /** Số dòng tô sáng tối đa của một hàm. */
    private static final int MAX_HIGHLIGHT = 30;
    private static final int MAX_NODES = 60;

    private final GraphQueryService graphQueries;
    private final LinkOverrideService overrideService;
    private final WorkspaceService workspaceService;
    private final SourceReader reader;

    public CodeSnippetService(
            GraphQueryService graphQueries, LinkOverrideService overrideService, WorkspaceService workspaceService, SourceReader reader) {
        this.graphQueries = graphQueries;
        this.overrideService = overrideService;
        this.workspaceService = workspaceService;
        this.reader = reader;
    }

    /** Một nơi trong mã nguồn cần hiện, kèm vai trò (để xếp tab). */
    private record Spot(IrNode node, String title, String role) {}

    /**
     * @param contextLines số dòng ngữ cảnh lấy thêm trước và sau mỗi hàm
     * @throws com.ctv.flowtracer.app.common.error.AppException {@code WORKSPACE_NOT_FOUND}; {@code NODE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<CodeTabResponse> snippets(UUID workspaceId, String nodeKey, int contextLines) {
        IrNodeRow start = graphQueries.requireNode(workspaceId, nodeKey);
        List<OverrideRule> overrides = overrideService.rules(workspaceId);
        Subgraph graph = new OverrideApplier()
                .apply(graphQueries.subgraphFrom(workspaceId, nodeKey, FLOW_DEPTH, LinkOverrideService.pinTargets(overrides)), overrides);
        List<IrNode> flow = walk(start.node(), new GraphIndex(graph));

        Map<String, IrNodeRow> rows = graphQueries.nodesByKeys(workspaceId, flow.stream().map(IrNode::key).toList()).stream()
                .collect(Collectors.toMap(r -> r.node().key(), r -> r, (a, b) -> a));
        Map<UUID, SourceScanTarget> sources = workspaceService.scanTargets(workspaceId).stream()
                .collect(Collectors.toMap(SourceScanTarget::sourceId, s -> s, (a, b) -> a));

        // một tab cho mỗi (tên tab, file); nhiều hàm cùng file gộp vào một đoạn
        Map<String, List<Spot>> tabs = new LinkedHashMap<>();
        for (IrNode node : flow) {
            Spot spot = spotOf(node);
            if (spot != null && node.location() != null && rows.containsKey(node.key())) {
                tabs.computeIfAbsent(spot.title() + "|" + node.location().filePath() + "|" + rows.get(node.key()).sourceId(), k -> new ArrayList<>()).add(spot);
            }
        }
        Map<String, Long> filesPerTitle = tabs.values().stream()
                .collect(Collectors.groupingBy(s -> s.get(0).title(), Collectors.counting()));
        var result = new ArrayList<CodeTabResponse>();
        for (List<Spot> group : tabs.values()) {
            Spot first = group.get(0);
            IrNodeRow row = rows.get(first.node().key());
            SourceScanTarget source = sources.get(row.sourceId());
            if (source == null) {
                continue;
            }
            String file = first.node().location().filePath();
            String title = filesPerTitle.get(first.title()) > 1 ? first.title() + " · " + fileName(file) : first.title();
            codeTab(source, file, title, first.role(), group, contextLines).ifPresent(result::add);
        }
        return result;
    }

    // ---------------- duyệt luồng ----------------

    /** Các node có mã nguồn trên đường đi của luồng, theo thứ tự thực thi. */
    private static List<IrNode> walk(IrNode start, GraphIndex graph) {
        var ordered = new ArrayList<IrNode>();
        Set<String> seen = new HashSet<>();
        Deque<IrNode> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty() && ordered.size() < MAX_NODES) {
            IrNode node = stack.pop();
            if (!seen.add(node.key())) {
                continue;
            }
            ordered.add(node);
            var next = new ArrayList<IrNode>();
            for (IrEdge e : graph.outgoing(node.key())) {
                if (e.toKey() == null || e.confidence() == Confidence.UNRESOLVED
                        || e.guard() != null && "afterClosed".equals(e.guard().branch())) {
                    continue;
                }
                if (isFlow(e.kind())) {
                    graph.node(e.toKey()).ifPresent(next::add);
                }
            }
            for (int i = next.size() - 1; i >= 0; i--) {
                stack.push(next.get(i));
            }
        }
        return ordered;
    }

    private static boolean isFlow(EdgeKind kind) {
        return kind == EdgeKind.TRIGGERS || kind == EdgeKind.CALLS || kind == EdgeKind.SENDS || kind == EdgeKind.HTTP_REQUEST
                || kind == EdgeKind.HANDLED_BY;
    }

    private static Spot spotOf(IrNode node) {
        return switch (node.kind()) {
            case UI_ELEMENT -> new Spot(node, "Template", "TEMPLATE");
            case HTTP_CALL -> new Spot(node, "Kết nối API", "HTTP");
            case FE_METHOD -> switch (String.valueOf(node.attrs().get("layer"))) {
                case "COMPONENT" -> new Spot(node, "Component", "COMPONENT");
                case "FACADE" -> new Spot(node, "Facade", "FRONTEND");
                case "USECASE" -> new Spot(node, "Use case", "FRONTEND");
                case "SERVICE" -> new Spot(node, "Service FE", "FRONTEND");
                case "REPOSITORY" -> new Spot(node, "Repository FE", "FRONTEND");
                default -> new Spot(node, "Frontend", "FRONTEND");
            };
            case BE_METHOD -> {
                if (Boolean.TRUE.equals(node.attrs().get("unresolvedMethod")) || Boolean.TRUE.equals(node.attrs().get("synthetic"))) {
                    yield null; // không có mã nguồn thật của hàm này
                }
                yield switch (String.valueOf(node.attrs().get("layer"))) {
                    case "CONTROLLER" -> new Spot(node, "Controller", "CONTROLLER");
                    case "SERVICE" -> new Spot(node, "Service", "SERVICE");
                    case "REPOSITORY" -> new Spot(node, "Repository", "REPOSITORY");
                    default -> new Spot(node, "Backend", "BACKEND");
                };
            }
            default -> null;
        };
    }

    // ---------------- đọc mã ----------------

    private java.util.Optional<CodeTabResponse> codeTab(
            SourceScanTarget source, String file, String title, String role, List<Spot> group, int context) {
        int from = Integer.MAX_VALUE;
        int to = 0;
        Set<Integer> highlight = new TreeSet<>();
        for (Spot spot : group) {
            int start = spot.node().location().startLine() == null ? 1 : spot.node().location().startLine();
            int end = spot.node().location().endLine() == null ? start : spot.node().location().endLine();
            from = Math.min(from, start);
            to = Math.max(to, end);
            for (int line = start; line <= Math.min(end, start + MAX_HIGHLIGHT - 1); line++) {
                highlight.add(line);
            }
        }
        int begin = Math.max(1, from - context);
        int end = Math.max(to + context, begin);
        if (end - begin + 1 > MAX_LINES) {
            end = begin + MAX_LINES - 1;
        }
        final int last = end;
        highlight.removeIf(l -> l > last);
        String path = source.rootPath() == null || source.rootPath().isEmpty() ? file : source.rootPath() + "/" + file;
        List<String> lines;
        try {
            lines = reader.readLines(path, begin, end);
        } catch (RuntimeException e) {
            return java.util.Optional.empty(); // file đã đổi hoặc bị xóa sau lần quét: bỏ tab này, các tab khác vẫn hiện
        }
        if (lines.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new CodeTabResponse(
                title, role, file, source.name(), begin, List.copyOf(highlight), String.join("\n", lines)));
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
