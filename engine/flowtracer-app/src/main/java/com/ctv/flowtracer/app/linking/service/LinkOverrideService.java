package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.linking.dto.request.CreateLinkOverrideRequest;
import com.ctv.flowtracer.app.linking.dto.response.LinkOverrideResponse;
import com.ctv.flowtracer.app.linking.entity.LinkOverride;
import com.ctv.flowtracer.app.linking.entity.OverrideDecision;
import com.ctv.flowtracer.app.linking.repository.LinkOverrideRepository;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.tracer.OverrideRule;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liên kết chốt tay (API E3–E5) — service công khai cho package {@code tracing}. Quyết định trỏ tới thành phần bằng KEY ổn
 * định nên giữ được qua mọi lần quét; nó không sửa IR mà được áp lúc dò vết ({@code OverrideApplier}), nên luôn thắng kết quả
 * tự động.
 *
 * <ul>
 *   <li>Ghim ({@code PIN}) thay mọi quyết định cũ cùng thành phần gọi và loại liên kết: mỗi lời gọi chỉ có một đích được ghim.
 *   <li>Loại bỏ ({@code REJECT}) thay quyết định cũ cùng thành phần gọi, đích và loại liên kết.
 * </ul>
 */
@Service
public class LinkOverrideService {

    /** Loại liên kết chốt tay được và loại thành phần đích hợp lệ của từng loại. */
    static final Map<EdgeKind, Set<NodeKind>> TARGET_KINDS = Map.of(
            EdgeKind.HTTP_REQUEST, Set.of(NodeKind.API_ENDPOINT, NodeKind.EXTERNAL),
            EdgeKind.CALLS, Set.of(NodeKind.BE_METHOD, NodeKind.FE_METHOD));

    private final LinkOverrideRepository overrides;
    private final WorkspaceService workspaceService;
    private final GraphQueryService graphQueries;

    public LinkOverrideService(
            LinkOverrideRepository overrides, WorkspaceService workspaceService, GraphQueryService graphQueries) {
        this.overrides = overrides;
        this.workspaceService = workspaceService;
        this.graphQueries = graphQueries;
    }

    /** E3 — các liên kết đã chốt, cũ trước. */
    @Transactional(readOnly = true)
    public List<LinkOverrideResponse> list(UUID workspaceId) {
        workspaceService.requireExists(workspaceId);
        List<LinkOverride> all = overrides.findByWorkspaceIdOrderByCreatedAt(workspaceId);
        Map<String, IrNode> nodes = nodes(workspaceId, all);
        return all.stream().map(o -> toResponse(o, nodes)).toList();
    }

    /**
     * E4 — chốt tay một liên kết.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code NODE_NOT_FOUND} nếu thành phần gọi / đích không có trong kết
     *     quả quét; {@code VALIDATION_FAILED} nếu loại liên kết không chốt tay được hoặc đích sai loại
     */
    @Transactional
    public LinkOverrideResponse create(UUID workspaceId, CreateLinkOverrideRequest request) {
        workspaceService.requireExists(workspaceId);
        Set<NodeKind> targetKinds = TARGET_KINDS.get(request.edgeKind());
        if (targetKinds == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Chỉ chốt tay được liên kết lời gọi HTTP → API hoặc lời gọi hàm",
                    Map.of("edgeKind", "Loại liên kết không chốt tay được: " + request.edgeKind()));
        }
        String fromKey = request.fromNodeKey().strip();
        String toKey = request.toNodeKey() == null || request.toNodeKey().isBlank() ? null : request.toNodeKey().strip();
        Map<String, IrNode> nodes = new HashMap<>();
        graphQueries.nodesByKeys(workspaceId, toKey == null ? List.of(fromKey) : List.of(fromKey, toKey))
                .forEach(r -> nodes.put(r.node().key(), r.node()));
        if (!nodes.containsKey(fromKey)) {
            throw new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần gọi trong kết quả quét");
        }
        if (toKey != null) {
            IrNode target = nodes.get(toKey);
            if (target == null) {
                throw new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần đích trong kết quả quét");
            }
            if (!targetKinds.contains(target.kind())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Thành phần đích không hợp lệ với loại liên kết này",
                        Map.of("toNodeKey", "Đích của " + request.edgeKind() + " phải là " + targetKinds.stream().sorted().toList()));
            }
        }
        List<LinkOverride> previous = overrides.findByWorkspaceIdAndFromNodeKeyAndEdgeKind(workspaceId, fromKey, request.edgeKind());
        overrides.deleteAll(previous.stream()
                .filter(o -> request.decision() == OverrideDecision.PIN || Objects.equals(o.getToNodeKey(), toKey))
                .toList());
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        LinkOverride saved = overrides.saveAndFlush(
                new LinkOverride(workspaceId, fromKey, toKey, request.edgeKind(), request.decision(), note));
        return toResponse(saved, nodes);
    }

    /**
     * E5 — bỏ một quyết định chốt tay; liên kết quay về kết quả tự động.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code LINK_OVERRIDE_NOT_FOUND}
     */
    @Transactional
    public void delete(UUID workspaceId, UUID overrideId) {
        workspaceService.requireExists(workspaceId);
        LinkOverride override = overrides.findByIdAndWorkspaceId(overrideId, workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.LINK_OVERRIDE_NOT_FOUND, "Không tìm thấy liên kết đã chốt tay"));
        overrides.delete(override);
    }

    /** Quyết định chốt tay dạng lõi để áp lúc dò vết — cho package {@code tracing}. */
    @Transactional(readOnly = true)
    public List<OverrideRule> rules(UUID workspaceId) {
        return overrides.findByWorkspaceIdOrderByCreatedAt(workspaceId).stream()
                .map(o -> new OverrideRule(o.getFromNodeKey(), o.getToNodeKey(), o.getEdgeKind(),
                        o.getDecision() == OverrideDecision.PIN ? OverrideRule.Decision.PIN : OverrideRule.Decision.REJECT))
                .toList();
    }

    /** Quyết định đang áp cho một liên kết (thành phần gọi + loại); ghim được ưu tiên, rồi quyết định mới nhất. */
    @Transactional(readOnly = true)
    public LinkOverrideResponse findFor(UUID workspaceId, String fromKey, EdgeKind kind, Map<String, IrNode> knownNodes) {
        List<LinkOverride> found = overrides.findByWorkspaceIdAndFromNodeKeyAndEdgeKind(workspaceId, fromKey, kind);
        return found.stream()
                .filter(o -> o.getDecision() == OverrideDecision.PIN)
                .findFirst()
                .or(() -> found.stream().reduce((a, b) -> b))
                .map(o -> toResponse(o, withNodes(workspaceId, o, knownNodes)))
                .orElse(null);
    }

    private Map<String, IrNode> withNodes(UUID workspaceId, LinkOverride o, Map<String, IrNode> known) {
        Map<String, IrNode> nodes = new HashMap<>(known);
        nodes.putAll(nodes(workspaceId, List.of(o)));
        return nodes;
    }

    private Map<String, IrNode> nodes(UUID workspaceId, Collection<LinkOverride> all) {
        Set<String> keys = new TreeSet<>();
        all.forEach(o -> {
            keys.add(o.getFromNodeKey());
            if (o.getToNodeKey() != null) {
                keys.add(o.getToNodeKey());
            }
        });
        Map<String, IrNode> nodes = new HashMap<>();
        graphQueries.nodesByKeys(workspaceId, keys).stream().map(IrNodeRow::node).forEach(n -> nodes.put(n.key(), n));
        return nodes;
    }

    private static LinkOverrideResponse toResponse(LinkOverride o, Map<String, IrNode> nodes) {
        IrNode from = nodes.get(o.getFromNodeKey());
        IrNode to = o.getToNodeKey() == null ? null : nodes.get(o.getToNodeKey());
        boolean stale = from == null || o.getToNodeKey() != null && to == null;
        return new LinkOverrideResponse(
                o.getId(),
                o.getFromNodeKey(),
                from == null ? null : label(from),
                o.getToNodeKey(),
                to == null ? null : label(to),
                o.getEdgeKind(),
                o.getDecision(),
                o.getNote(),
                o.getCreatedAt(),
                stale);
    }

    private static String label(IrNode node) {
        return node.displayName() != null && !node.displayName().isBlank() ? node.displayName() : node.name();
    }
}
