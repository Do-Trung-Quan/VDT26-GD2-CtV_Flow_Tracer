package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.dto.response.BackendTreeResponse;
import com.ctv.flowtracer.app.graph.dto.response.FrontendTreeResponse;
import com.ctv.flowtracer.app.graph.dto.response.SearchGroupResponse;
import com.ctv.flowtracer.app.graph.dto.response.SearchHitResponse;
import com.ctv.flowtracer.app.graph.dto.response.SearchResponse;
import com.ctv.flowtracer.app.graph.model.SearchCategory;
import com.ctv.flowtracer.app.graph.model.SearchHitRow;
import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.repository.IrEdgeRepository;
import com.ctv.flowtracer.app.graph.repository.IrNodeRepository;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc IR — service công khai cho các package khác (thống kê lần quét…) và cho API nhóm C. */
@Service
public class GraphQueryService {

    private final IrNodeRepository nodes;
    private final IrEdgeRepository edges;
    private final WorkspaceService workspaceService;

    public GraphQueryService(IrNodeRepository nodes, IrEdgeRepository edges, WorkspaceService workspaceService) {
        this.nodes = nodes;
        this.edges = edges;
        this.workspaceService = workspaceService;
    }

    /**
     * API C1: cây {@code Module → Endpoint} của các Source Backend.
     *
     * @throws com.ctv.flowtracer.app.common.error.AppException {@code WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public BackendTreeResponse backendTree(UUID workspaceId) {
        Map<UUID, String> backendSources = new LinkedHashMap<>();
        for (SourceScanTarget s : workspaceService.scanTargets(workspaceId)) {
            if (s.role() == SourceRole.BACKEND) {
                backendSources.put(s.sourceId(), s.name());
            }
        }
        return TreeBuilder.backend(nodes.findByKind(workspaceId, NodeKind.API_ENDPOINT, backendSources.keySet()), backendSources);
    }

    /**
     * API C2: cây {@code Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử} của các Source Frontend, kèm cờ phần tử
     * "gọi API" hay "chỉ giao diện" (suy ra từ đồ thị: sự kiện → hàm xử lý → lời gọi HTTP).
     *
     * @throws com.ctv.flowtracer.app.common.error.AppException {@code WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public FrontendTreeResponse frontendTree(UUID workspaceId) {
        Map<UUID, String> frontendSources = new LinkedHashMap<>();
        for (SourceScanTarget s : workspaceService.scanTargets(workspaceId)) {
            if (s.role() == SourceRole.FRONTEND) {
                frontendSources.put(s.sourceId(), s.name());
            }
        }
        var kinds = new ArrayList<>(SearchCategory.SCREEN.kinds());
        kinds.add(NodeKind.UI_ELEMENT);
        return TreeBuilder.frontend(
                nodes.findByKinds(workspaceId, kinds, frontendSources.keySet()),
                edges.findContains(workspaceId, frontendSources.keySet()),
                edges.findUiKeysCallingApi(workspaceId),
                edges.findUiLinks(workspaceId),
                frontendSources);
    }

    /**
     * API C3: tìm kiếm nhanh theo đường dẫn API, nhãn nút, tên màn hình, tên class / hàm, tên bảng; kết quả nhóm theo loại.
     *
     * @param query từ khóa; nhiều từ cách nhau bằng khoảng trắng thì thành phần phải khớp TẤT CẢ các từ
     * @param categories chỉ tìm trong các nhóm này; rỗng hoặc null là mọi nhóm
     * @param limitPerGroup số kết quả tối đa mỗi nhóm
     * @throws com.ctv.flowtracer.app.common.error.AppException {@code WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public SearchResponse search(UUID workspaceId, String query, List<SearchCategory> categories, int limitPerGroup) {
        workspaceService.requireExists(workspaceId);
        List<String> tokens = Arrays.stream(query.trim().split("\\s+")).filter(t -> !t.isEmpty()).toList();
        List<SearchCategory> wanted = categories == null || categories.isEmpty() ? List.of(SearchCategory.values()) : categories;
        Map<SearchCategory, List<SearchHitRow>> grouped = nodes.search(workspaceId, tokens, wanted, limitPerGroup).stream()
                .collect(Collectors.groupingBy(
                        h -> SearchCategory.of(h.row().node().kind()), () -> new EnumMap<>(SearchCategory.class), Collectors.toList()));
        var groups = new ArrayList<SearchGroupResponse>();
        long total = 0;
        for (var entry : grouped.entrySet()) {
            List<SearchHitRow> rows = entry.getValue();
            long groupTotal = rows.get(0).categoryTotal();
            total += groupTotal;
            groups.add(new SearchGroupResponse(
                    entry.getKey(),
                    entry.getKey().label(),
                    (int) groupTotal,
                    rows.stream().map(h -> hit(h.row().node())).toList()));
        }
        return new SearchResponse(String.join(" ", tokens), (int) total, List.copyOf(groups));
    }

    private static SearchHitResponse hit(IrNode node) {
        Map<String, Object> attrs = node.attrs();
        String context = switch (node.kind()) {
            case API_ENDPOINT -> text(attrs.get("module"));
            case UI_SCREEN -> text(attrs.get("routePath"));
            case UI_ELEMENT, UI_DIALOG, UI_FORM, UI_TAB -> text(attrs.get("component"));
            case BE_METHOD, FE_METHOD -> text(attrs.get("layer"));
            case DB_TABLE -> text(attrs.get("entity"));
            default -> null;
        };
        return new SearchHitResponse(
                node.key(),
                node.kind(),
                node.name(),
                node.displayName(),
                context,
                node.location() == null ? null : node.location().filePath(),
                node.location() == null ? null : node.location().startLine());
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** Như {@link #subgraphFrom(UUID, String, int, Map)} khi không có liên kết chốt tay. */
    @Transactional(readOnly = true)
    public Subgraph subgraphFrom(UUID workspaceId, String startKey, int maxDepth) {
        return subgraphFrom(workspaceId, startKey, maxDepth, Map.of());
    }

    /**
     * Đồ thị con để dò vết: node xuất phát, mọi node đi tới được trong tối đa {@code maxDepth} bước, các edge xuất phát từ
     * chúng, và (chỉ để hiển thị) node của các ứng viên. Mỗi edge kèm id CSDL làm {@code edgeRef}. Đích của liên kết chốt
     * tay xuất phát từ một node đã nạp cũng được nạp cùng phần đi tới được từ nó — để Tracer đi tiếp sau khi áp chốt tay.
     *
     * @param pinTargets key node đi → các key đích đã chốt tay (PIN)
     * @throws AppException {@code NODE_NOT_FOUND} nếu Workspace chưa quét hoặc không có node này
     */
    @Transactional(readOnly = true)
    public Subgraph subgraphFrom(UUID workspaceId, String startKey, int maxDepth, Map<String, Set<String>> pinTargets) {
        IrNodeRow start = nodes.findByKey(workspaceId, startKey)
                .orElseThrow(() -> new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần cần dò vết trong kết quả quét"));
        // Mỗi bước đi qua method thêm tối đa 1 cạnh; cộng 3 cho cạnh xử lý, truy vấn và bảng ở hai đầu. Từ phần tử giao diện
        // còn thêm chuỗi hàm phía Frontend (hàm xử lý → facade → repository → lời gọi HTTP → API) trước khi tới Backend.
        int depth = maxDepth + 3 + (start.node().kind() == NodeKind.UI_ELEMENT ? FRONTEND_EXTRA_DEPTH : 0);
        Set<UUID> reachable = new java.util.TreeSet<>(edges.reachableNodeIds(start.id(), depth));
        Map<String, IrNode> byKey = new TreeMap<>();
        nodes.findByIds(workspaceId, reachable).forEach(r -> byKey.put(r.node().key(), r.node()));
        // Đích chốt tay chưa nằm trong phần đã nạp: nạp thêm (lặp vì đích mới có thể dẫn tới chốt tay khác)
        for (int round = 0; round < MAX_PIN_ROUNDS && !pinTargets.isEmpty(); round++) {
            Set<String> missing = new java.util.TreeSet<>();
            pinTargets.forEach((from, targets) -> {
                if (byKey.containsKey(from)) {
                    targets.stream().filter(t -> !byKey.containsKey(t)).forEach(missing::add);
                }
            });
            if (missing.isEmpty()) {
                break;
            }
            for (IrNodeRow target : nodes.findByKeys(workspaceId, missing)) {
                List<UUID> more = edges.reachableNodeIds(target.id(), maxDepth + 3);
                reachable.addAll(more);
                nodes.findByIds(workspaceId, more).forEach(r -> byKey.put(r.node().key(), r.node()));
            }
        }
        List<IrEdgeRow> edgeRows = edges.findByFromNodes(workspaceId, reachable);

        Set<String> candidateKeys = new LinkedHashSet<>();
        edgeRows.forEach(r -> r.edge().candidates().forEach(c -> candidateKeys.add(c.nodeKey())));
        candidateKeys.removeAll(byKey.keySet());
        nodes.findByKeys(workspaceId, candidateKeys).forEach(r -> byKey.put(r.node().key(), r.node()));

        var edgeList = new java.util.ArrayList<IrEdge>();
        var refs = new LinkedHashMap<String, String>();
        for (IrEdgeRow row : edgeRows) {
            edgeList.add(row.edge());
            refs.put(Subgraph.edgeId(row.edge()), row.id().toString());
        }
        return new Subgraph(List.copyOf(byKey.values()), edgeList, refs, Set.of());
    }

    /**
     * Node theo key.
     *
     * @throws AppException {@code NODE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public IrNodeRow requireNode(UUID workspaceId, String nodeKey) {
        workspaceService.requireExists(workspaceId);
        return nodes.findByKey(workspaceId, nodeKey)
                .orElseThrow(() -> new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần trong kết quả quét"));
    }

    /**
     * Mọi node {@code DATA_TYPE} mà các node cho trước dùng (đầu vào / đầu ra của endpoint) kể cả kiểu lồng bên trong, theo key.
     * Đi theo các biểu thức kiểu ghi trong thuộc tính, tới tối đa {@value #MAX_TYPE_ROUNDS} vòng.
     */
    @Transactional(readOnly = true)
    public Map<String, IrNode> dataTypes(UUID workspaceId, java.util.Collection<IrNode> roots) {
        Map<String, IrNode> found = new TreeMap<>();
        Set<String> wanted = new LinkedHashSet<>();
        for (IrNode n : roots) {
            collectTypeKeys(n.attrs(), wanted);
        }
        for (int round = 0; round < MAX_TYPE_ROUNDS && !wanted.isEmpty(); round++) {
            Set<String> next = new LinkedHashSet<>();
            for (IrNodeRow row : nodes.findByKeys(workspaceId, wanted)) {
                if (found.putIfAbsent(row.node().key(), row.node()) == null) {
                    collectTypeKeys(row.node().attrs(), next);
                }
            }
            next.removeAll(found.keySet());
            wanted = next;
        }
        return found;
    }

    /** Mọi key node đã gặp trong các biểu thức kiểu {@code {"t": "ref", "key": …}} nằm lồng trong thuộc tính. */
    private static void collectTypeKeys(Object value, Set<String> out) {
        if (value instanceof Map<?, ?> m) {
            if ("ref".equals(m.get("t")) && m.get("key") instanceof String key) {
                out.add(key);
            }
            m.values().forEach(v -> collectTypeKeys(v, out));
        } else if (value instanceof List<?> list) {
            list.forEach(v -> collectTypeKeys(v, out));
        }
    }

    /** Số cạnh thêm khi dò từ phần tử giao diện (chuỗi hàm phía Frontend). */
    private static final int FRONTEND_EXTRA_DEPTH = 12;
    /** Số vòng nạp kiểu dữ liệu lồng nhau tối đa. */
    private static final int MAX_TYPE_ROUNDS = 8;
    /** Số vòng nạp thêm đích chốt tay tối đa. */
    private static final int MAX_PIN_ROUNDS = 5;

    /** Mọi thành phần một loại của Workspace (vd lời gọi HTTP, endpoint cho Linker), sắp theo key. */
    public List<IrNodeRow> nodesOfKind(UUID workspaceId, NodeKind kind) {
        return nodes.findAllByKind(workspaceId, kind);
    }

    /** Thành phần theo key (bỏ qua key không có). */
    public List<IrNodeRow> nodesByKeys(UUID workspaceId, java.util.Collection<String> keys) {
        return nodes.findByKeys(workspaceId, keys);
    }

    /** Một edge theo id. */
    public java.util.Optional<IrEdgeRow> edge(UUID workspaceId, UUID edgeId) {
        return edges.findById(workspaceId, edgeId);
    }

    /** Mọi edge một loại của Workspace (vd liên kết HTTP_REQUEST để thống kê). */
    public List<IrEdgeRow> edgesOfKind(UUID workspaceId, EdgeKind kind) {
        return edges.findByKind(workspaceId, kind);
    }

    public long countNodes(UUID workspaceId) {
        return nodes.countByWorkspace(workspaceId);
    }

    public long countEdges(UUID workspaceId) {
        return edges.countByWorkspace(workspaceId);
    }

    public long countNodes(UUID workspaceId, NodeKind kind) {
        return nodes.countByWorkspaceAndKind(workspaceId, kind);
    }

    public long countNodesOfSource(UUID sourceId) {
        return nodes.countBySource(sourceId);
    }

    public long countNodesOfSource(UUID sourceId, NodeKind kind) {
        return nodes.countBySourceAndKind(sourceId, kind);
    }
}
