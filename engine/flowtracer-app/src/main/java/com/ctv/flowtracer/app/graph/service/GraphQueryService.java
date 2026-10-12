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

    /**
     * Đồ thị con để dò vết: node xuất phát, mọi node đi tới được trong tối đa {@code maxDepth} bước, các edge xuất phát từ
     * chúng, và (chỉ để hiển thị) node của các ứng viên. Mỗi edge kèm id CSDL làm {@code edgeRef}.
     *
     * @throws AppException {@code NODE_NOT_FOUND} nếu Workspace chưa quét hoặc không có node này
     */
    @Transactional(readOnly = true)
    public Subgraph subgraphFrom(UUID workspaceId, String startKey, int maxDepth) {
        IrNodeRow start = nodes.findByKey(workspaceId, startKey)
                .orElseThrow(() -> new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần cần dò vết trong kết quả quét"));
        // Mỗi bước đi qua method thêm tối đa 1 cạnh; cộng 3 cho cạnh xử lý, truy vấn và bảng ở hai đầu
        List<UUID> reachable = edges.reachableNodeIds(start.id(), maxDepth + 3);
        List<IrEdgeRow> edgeRows = edges.findByFromNodes(workspaceId, reachable);

        Map<String, IrNode> byKey = new TreeMap<>();
        nodes.findByIds(workspaceId, reachable).forEach(r -> byKey.put(r.node().key(), r.node()));
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
