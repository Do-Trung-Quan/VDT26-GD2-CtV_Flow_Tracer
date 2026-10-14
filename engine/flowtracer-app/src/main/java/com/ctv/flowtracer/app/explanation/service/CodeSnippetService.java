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
    /** Số dòng tối đa của MỘT đoạn (chỉ để chặn trường hợp bất thường; hàm thường không bao giờ chạm tới). */
    static final int MAX_LINES = 1000;
    /** Hai hàm cách nhau không quá số dòng này thì hiện chung một đoạn; xa hơn thì tách đoạn riêng, không kéo theo phần ở giữa. */
    private static final int MERGE_GAP = 3;
    /** Số dòng tối đa xét lùi lên trên để lấy chú thích / annotation / decorator ngay trước hàm. */
    private static final int LOOK_BACK = 40;
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

    /**
     * Các dòng mã của một node (từ dòng đầu tới dòng cuối của nó, tối đa {@code maxLines}); rỗng nếu node không có vị trí hoặc không đọc
     * được file. Dùng cho các tính năng cần đọc đúng thân hàm (vd phân tích liên kết bằng AI).
     */
    @Transactional(readOnly = true)
    public List<String> nodeLines(UUID workspaceId, String nodeKey, int maxLines) {
        IrNodeRow row = graphQueries.requireNode(workspaceId, nodeKey);
        IrNode node = row.node();
        if (node.location() == null || node.location().startLine() == null) {
            return List.of();
        }
        SourceScanTarget source = workspaceService.scanTargets(workspaceId).stream()
                .filter(s -> s.sourceId().equals(row.sourceId())).findFirst().orElse(null);
        if (source == null) {
            return List.of();
        }
        int start = node.location().startLine();
        int end = node.location().endLine() == null ? start : Math.max(start, node.location().endLine());
        String file = node.location().filePath();
        String path = source.rootPath() == null || source.rootPath().isEmpty() ? file : source.rootPath() + "/" + file;
        try {
            return reader.readLines(path, start, Math.min(end, start + Math.max(1, maxLines) - 1));
        } catch (RuntimeException e) {
            return List.of();
        }
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

    /** Khoảng dòng của một hàm / khối cần hiện. */
    private record Range(int start, int end) {}

    /** Thẻ HTML không có thẻ đóng. */
    private static final Set<String> VOID_TAGS =
            Set.of("input", "img", "br", "hr", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr");
    /** Số dòng tối đa xét tiếp khi tự tìm điểm kết thúc của phần tử HTML / câu lệnh gọi. */
    private static final int SCAN_LINES = 200;
    /** Phần tử HTML dài hơn số dòng này thì chỉ hiện thẻ mở và thẻ đóng (giữa là dòng ngăn cách). */
    private static final int TEMPLATE_MAX_LINES = 60;
    /** Số dòng tối đa của thẻ mở khi phần tử bị rút gọn. */
    private static final int TEMPLATE_HEAD_LINES = 25;
    private static final String GAP = "⋯";

    /**
     * MỘT tab cho mỗi file: các hàm / khối liên quan của file đó (mỗi cái từ dòng đầu — lùi lên lấy cả chú thích, annotation,
     * decorator ngay trước — tới dòng cuối của nó) nối tiếp nhau và được tô sáng. Hai đoạn sát nhau thì liền một khối, cách xa nhau
     * thì ngăn bằng một dòng {@code ⋯} — không kéo cả phần mã ở giữa vào và không cắt cụt đầu / đuôi.
     */
    private java.util.Optional<CodeTabResponse> codeTab(
            SourceScanTarget source, String file, String title, String role, List<Spot> group, int context) {
        String path = source.rootPath() == null || source.rootPath().isEmpty() ? file : source.rootPath() + "/" + file;
        boolean template = "TEMPLATE".equals(role);
        boolean http = "HTTP".equals(role);
        List<Range> ranges = new ArrayList<>();
        Set<Integer> highlight = new TreeSet<>();
        for (Spot spot : group) {
            int start = spot.node().location().startLine() == null ? 1 : spot.node().location().startLine();
            Integer stored = spot.node().location().endLine();
            int end = stored == null ? start : Math.max(start, stored);
            int openEnd = start;
            if (template) {
                int[] span = htmlElementSpan(path, start);
                openEnd = span[0];
                if (end <= start) {
                    end = Math.max(end, span[1]); // dữ liệu quét cũ chưa có dòng cuối của phần tử
                }
            } else if (http && end <= start) {
                end = Math.max(end, statementEnd(path, start));
            }
            if (template && end - start + 1 > TEMPLATE_MAX_LINES) {
                // phần tử HTML quá dài (vd bảng chứa cả các ô tùy biến): thẻ mở đầy đủ, dòng ngăn cách, rồi thẻ đóng
                int headEnd = Math.min(Math.max(openEnd, start), start + TEMPLATE_HEAD_LINES - 1);
                markHighlight(highlight, start, headEnd);
                markHighlight(highlight, end, end);
                ranges.add(new Range(start, headEnd));
                ranges.add(new Range(end, end));
                continue;
            }
            markHighlight(highlight, start, end);
            ranges.add(new Range(code(role) ? withLeadingComments(path, start) : start, end));
        }
        ranges.sort(java.util.Comparator.comparingInt(Range::start).thenComparingInt(Range::end));
        List<Range> merged = new ArrayList<>();
        for (Range r : ranges) {
            Range last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && r.start() - last.end() <= MERGE_GAP + 2 * context) {
                merged.set(merged.size() - 1, new Range(last.start(), Math.max(last.end(), r.end())));
            } else {
                merged.add(r);
            }
        }
        List<String> text = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        for (Range r : merged) {
            int begin = Math.max(1, r.start() - context);
            int end = Math.min(Math.max(r.end() + context, begin), begin + MAX_LINES - 1);
            List<String> lines;
            try {
                lines = reader.readLines(path, begin, end);
            } catch (RuntimeException e) {
                continue; // file đã đổi hoặc bị xóa sau lần quét: bỏ đoạn này
            }
            if (lines.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.add(GAP);
                numbers.add(0);
            }
            for (int i = 0; i < lines.size(); i++) {
                text.add(lines.get(i));
                numbers.add(begin + i);
            }
        }
        if (text.isEmpty()) {
            return java.util.Optional.empty();
        }
        int first = numbers.get(0);
        Set<Integer> shown = new java.util.HashSet<>(numbers);
        highlight.removeIf(l -> !shown.contains(l));
        return java.util.Optional.of(new CodeTabResponse(
                title, role, file, source.name(), first, List.copyOf(highlight), String.join("\n", text), List.copyOf(numbers)));
    }

    private static void markHighlight(Set<Integer> highlight, int from, int to) {
        for (int line = from; line <= Math.min(to, from + MAX_HIGHLIGHT - 1); line++) {
            highlight.add(line);
        }
    }

    /** Vai trò là mã (Java / TypeScript) — có chú thích, annotation, decorator đứng trước; mẫu HTML và lời gọi HTTP thì không. */
    private static boolean code(String role) {
        return !"TEMPLATE".equals(role) && !"HTTP".equals(role);
    }

    /** Lùi từ dòng đầu của hàm lên qua các dòng chú thích / annotation / decorator liền kề (dừng ở dòng trống hoặc mã khác). */
    private int withLeadingComments(String path, int start) {
        if (start <= 1) {
            return start;
        }
        int from = Math.max(1, start - LOOK_BACK);
        List<String> above;
        try {
            above = reader.readLines(path, from, start - 1);
        } catch (RuntimeException e) {
            return start;
        }
        int first = start;
        for (int i = above.size() - 1; i >= 0; i--) {
            String t = above.get(i).strip();
            boolean attached = t.startsWith("@") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("//") || t.endsWith("*/");
            if (!attached) {
                break;
            }
            first = from + i;
        }
        return first;
    }

    private List<String> linesFrom(String path, int start) {
        try {
            return reader.readLines(path, start, start + SCAN_LINES - 1);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Dòng cuối của một phần tử HTML bắt đầu ở dòng {@code start}: tới thẻ đóng tương ứng (đếm lồng nhau, bỏ qua dấu {@code >}
     * trong chuỗi); không cân bằng được thì tới hết thẻ mở. Dùng khi dữ liệu quét cũ chưa lưu dòng cuối của phần tử.
     */
    static int htmlElementEnd(List<String> lines, int start) {
        return htmlElementSpan(lines, start)[1];
    }

    /** {dòng cuối của thẻ mở, dòng cuối của phần tử}; cả hai bằng {@code start} khi không đọc được. */
    static int[] htmlElementSpan(List<String> lines, int start) {
        if (lines.isEmpty()) {
            return new int[] {start, start};
        }
        String text = String.join("\n", lines);
        int open = text.indexOf('<');
        if (open < 0) {
            return new int[] {start, start};
        }
        int nameEnd = open + 1;
        while (nameEnd < text.length() && (Character.isLetterOrDigit(text.charAt(nameEnd)) || "-_:.".indexOf(text.charAt(nameEnd)) >= 0)) {
            nameEnd++;
        }
        String tag = text.substring(open + 1, nameEnd).toLowerCase(java.util.Locale.ROOT);
        if (tag.isEmpty()) {
            return new int[] {start, start};
        }
        int depth = 0;
        int firstTagEnd = -1;
        int i = open;
        while (i < text.length()) {
            if (text.charAt(i) != '<') {
                i++;
                continue;
            }
            boolean closing = i + 1 < text.length() && text.charAt(i + 1) == '/';
            int nameStart = i + (closing ? 2 : 1);
            int j = nameStart;
            while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || "-_:.".indexOf(text.charAt(j)) >= 0)) {
                j++;
            }
            boolean same = text.substring(nameStart, j).toLowerCase(java.util.Locale.ROOT).equals(tag);
            int end = j;
            char quote = 0;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    break;
                }
                end++;
            }
            if (end >= text.length()) {
                break;
            }
            boolean selfClosing = end > 0 && text.charAt(end - 1) == '/';
            if (same) {
                if (closing) {
                    depth--;
                } else if (!selfClosing && !VOID_TAGS.contains(tag)) {
                    depth++;
                }
                if (firstTagEnd < 0) {
                    firstTagEnd = end;
                }
                if (depth <= 0) {
                    return new int[] {start + lineBreaks(text, firstTagEnd), start + lineBreaks(text, end)};
                }
            }
            i = end + 1;
        }
        int openEnd = firstTagEnd < 0 ? start : start + lineBreaks(text, firstTagEnd);
        return new int[] {openEnd, openEnd};
    }

    private static int lineBreaks(String text, int upTo) {
        return (int) text.substring(0, upTo).chars().filter(c -> c == '\n').count();
    }

    private int[] htmlElementSpan(String path, int start) {
        return htmlElementSpan(linesFrom(path, start), start);
    }

    /**
     * Dòng cuối của câu lệnh gọi bắt đầu ở dòng {@code start}: tới khi ngoặc cân bằng và không còn chuỗi {@code .method(...)} nối tiếp.
     * Không cân bằng được thì giữ nguyên dòng đầu.
     */
    static int statementEnd(List<String> lines, int start) {
        int depth = 0;
        boolean opened = false;
        char quote = 0;
        for (int line = 0; line < lines.size(); line++) {
            String t = lines.get(line);
            for (int k = 0; k < t.length(); k++) {
                char c = t.charAt(k);
                if (quote != 0) {
                    if (c == '\\') {
                        k++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'' || c == '`') {
                    quote = c;
                } else if (c == '(' || c == '[' || c == '{') {
                    depth++;
                    opened = true;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                }
            }
            if (opened && depth <= 0) {
                String next = line + 1 < lines.size() ? lines.get(line + 1).strip() : "";
                if (!next.startsWith(".") && !next.startsWith("?.")) {
                    return start + line;
                }
            }
        }
        return start;
    }

    private int statementEnd(String path, int start) {
        return statementEnd(linesFrom(path, start), start);
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
