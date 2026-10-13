package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.dto.response.NodeDetailResponse;
import com.ctv.flowtracer.app.graph.dto.response.NodeRelationResponse;
import com.ctv.flowtracer.app.graph.model.ContainerStats;
import com.ctv.flowtracer.app.graph.model.DescendantRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.repository.IrEdgeRepository;
import com.ctv.flowtracer.app.graph.repository.IrNodeRepository;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.explain.BackendFlow;
import com.ctv.flowtracer.core.explain.UiFlowResolver;
import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quan hệ của một thành phần (API C4): chuỗi thành phần chứa nó, "Được gọi từ", "Gọi tới", và thống kê bên trong thành phần
 * chứa — service công khai cho package {@code explanation}. Mọi thứ suy từ IR đã lưu; không đoán.
 */
@Service
public class NodeDetailService {

    /** Số thành phần "Được gọi từ" tối đa trả về (tổng số vẫn được báo). */
    static final int MAX_CALLERS = 30;
    private static final int MAX_TRAIL = 8;
    private static final int RELATION_DEPTH = 12;

    /** Quan hệ đã tính của một thành phần. */
    public record Relations(
            List<NodeRelationResponse> trail,
            List<NodeRelationResponse> calledFrom,
            int calledFromTotal,
            List<NodeRelationResponse> callsTo) {}

    private final GraphQueryService graphQueries;
    private final IrNodeRepository nodes;
    private final IrEdgeRepository edges;
    private final WorkspaceService workspaceService;

    public NodeDetailService(
            GraphQueryService graphQueries, IrNodeRepository nodes, IrEdgeRepository edges, WorkspaceService workspaceService) {
        this.graphQueries = graphQueries;
        this.nodes = nodes;
        this.edges = edges;
        this.workspaceService = workspaceService;
    }

    /**
     * C4 — chi tiết một thành phần.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code NODE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public NodeDetailResponse detail(UUID workspaceId, String nodeKey) {
        IrNodeRow row = graphQueries.requireNode(workspaceId, nodeKey);
        Relations relations = relations(workspaceId, row, null);
        IrNode node = row.node();
        Map<UUID, String> sourceNames = workspaceService.scanTargets(workspaceId).stream()
                .collect(Collectors.toMap(SourceScanTarget::sourceId, SourceScanTarget::name, (a, b) -> a));
        return new NodeDetailResponse(
                node.key(),
                node.kind(),
                node.name(),
                node.displayName(),
                node.location() == null ? null : node.location().filePath(),
                node.location() == null ? null : node.location().startLine(),
                sourceNames.get(row.sourceId()),
                relations.trail(),
                relations.calledFrom(),
                relations.calledFromTotal(),
                relations.callsTo());
    }

    /**
     * Quan hệ của một thành phần.
     *
     * @param flow đồ thị con quanh thành phần (đã áp liên kết chốt tay); null thì tự nạp từ IR đã lưu
     */
    @Transactional(readOnly = true)
    public Relations relations(UUID workspaceId, IrNodeRow row, Subgraph flow) {
        IrNode node = row.node();
        return switch (node.kind()) {
            case API_ENDPOINT -> endpointRelations(workspaceId, row, flow);
            case UI_ELEMENT -> elementRelations(workspaceId, row, flow);
            case UI_APP, UI_SCREEN, UI_TAB, UI_DIALOG, UI_FORM -> new Relations(trail(workspaceId, row), List.of(), 0, List.of());
            default -> new Relations(List.of(), List.of(), 0, List.of());
        };
    }

    /** Thống kê bên trong một thành phần chứa: số phần tử, số phần tử gọi API, số hộp thoại. */
    @Transactional(readOnly = true)
    public ContainerStats containerStats(UUID workspaceId, IrNodeRow container) {
        List<DescendantRow> inside = edges.findDescendants(workspaceId, container.id());
        Set<String> callingApi = edges.findUiKeysCallingApi(workspaceId);
        int elements = 0;
        int calling = 0;
        int dialogs = 0;
        for (DescendantRow d : inside) {
            if (d.kind() == NodeKind.UI_ELEMENT) {
                elements++;
                if (callingApi.contains(d.key())) {
                    calling++;
                }
            } else if (d.kind() == NodeKind.UI_DIALOG) {
                dialogs++;
            }
        }
        return new ContainerStats(elements, calling, dialogs);
    }

    // ---------------- API ----------------

    private Relations endpointRelations(UUID workspaceId, IrNodeRow row, Subgraph flow) {
        List<UUID> callerIds = edges.findUiCallerIds(workspaceId, row.id());
        List<IrNodeRow> callers = nodes.findByIds(workspaceId, callerIds).stream()
                .sorted(Comparator.comparingInt((IrNodeRow r) -> r.node().kind() == NodeKind.UI_ELEMENT ? 0 : 1)
                        .thenComparing(r -> r.node().key()))
                .toList();
        var from = new ArrayList<NodeRelationResponse>();
        for (IrNodeRow caller : callers.stream().limit(MAX_CALLERS).toList()) {
            from.add(relation(caller.node(), contextOf(workspaceId, caller)));
        }
        Subgraph graph = flow != null ? flow : graphQueries.subgraphFrom(workspaceId, row.node().key(), RELATION_DEPTH);
        BackendFlow backend = BackendFlow.of(row.node(), new GraphIndex(graph));
        var to = new ArrayList<NodeRelationResponse>();
        backend.tables().forEach(t -> to.add(relation(t, null)));
        backend.remoteCalls().forEach(t -> to.add(relation(t, null)));
        return new Relations(List.of(), from, callers.size(), to);
    }

    private Relations elementRelations(UUID workspaceId, IrNodeRow row, Subgraph flow) {
        Subgraph graph = flow != null ? flow : graphQueries.subgraphFrom(workspaceId, row.node().key(), RELATION_DEPTH);
        UiFlowResolver ui = UiFlowResolver.of(row.node(), new GraphIndex(graph));
        var to = new ArrayList<NodeRelationResponse>();
        ui.apis().forEach(n -> to.add(relation(n, null)));
        ui.opens().forEach(n -> to.add(relation(n, null)));
        ui.navigates().forEach(n -> to.add(relation(n, null)));
        return new Relations(trail(workspaceId, row), List.of(), 0, to);
    }

    /** Chuỗi thành phần chứa, từ cao nhất xuống cha trực tiếp. */
    private List<NodeRelationResponse> trail(UUID workspaceId, IrNodeRow row) {
        var chain = new ArrayList<NodeRelationResponse>();
        Set<UUID> seen = new HashSet<>();
        Optional<IrNodeRow> parent = nodes.findParent(workspaceId, row.id());
        while (parent.isPresent() && chain.size() < MAX_TRAIL && seen.add(parent.get().id())) {
            chain.add(relation(parent.get().node(), null));
            parent = nodes.findParent(workspaceId, parent.get().id());
        }
        Collections.reverse(chain);
        return chain;
    }

    /** Thành phần chứa gần nhất mà người dùng nhìn thấy: bỏ qua form (chỉ là khung nhóm ô nhập, thường không có nhãn). */
    private String contextOf(UUID workspaceId, IrNodeRow element) {
        Optional<IrNodeRow> parent = nodes.findParent(workspaceId, element.id());
        while (parent.isPresent() && parent.get().node().kind() == NodeKind.UI_FORM) {
            parent = nodes.findParent(workspaceId, parent.get().id());
        }
        return parent.map(p -> contextLabel(p.node())).orElse(null);
    }

    /** Tên thành phần chứa kèm loại, như trên cây Frontend: "Màn hình Danh mục", "Hộp thoại Thêm danh mục". */
    private static String contextLabel(IrNode container) {
        String prefix = switch (container.kind()) {
            case UI_APP -> "Ứng dụng";
            case UI_SCREEN -> "Màn hình";
            case UI_TAB -> "Tab";
            case UI_DIALOG -> "Hộp thoại";
            case UI_FORM -> "Form";
            default -> null;
        };
        return prefix == null ? label(container) : prefix + " " + label(container);
    }

    /** Quan hệ kèm đủ thông tin để giao diện đặt nhãn như trên cây Frontend ("Nút Lưu", "Màn hình Danh mục"). */
    private static NodeRelationResponse relation(IrNode node, String context) {
        boolean element = node.kind() == NodeKind.UI_ELEMENT;
        Object type = node.attrs().get("elementType");
        Object route = node.attrs().get("routePath");
        return new NodeRelationResponse(
                node.key(),
                node.kind(),
                node.name(),
                node.displayName(),
                context,
                element && type instanceof String t && !t.isBlank() ? t : null,
                element && node.displayName() == null ? TreeBuilder.fallbackName(node) : null,
                node.kind() == NodeKind.UI_SCREEN && route instanceof String r && !r.isBlank() ? r : null);
    }

    private static String label(IrNode node) {
        return node.displayName() != null && !node.displayName().isBlank() ? node.displayName() : node.name();
    }
}
