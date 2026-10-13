package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.graph.service.GraphWriteService;
import com.ctv.flowtracer.app.linking.dto.response.EdgeCandidateResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeDetailResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeNodeResponse;
import com.ctv.flowtracer.app.linking.model.LinkSummary;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.linker.CandidateScorer;
import com.ctv.flowtracer.core.linker.Linker;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.Comparator;
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
 * Nối mọi lời gọi HTTP của Workspace với API đích — service công khai cho package {@code scan} (bước LINK sau khi lưu IR)
 * và dùng lại khi người dùng sửa luật liên kết; chi tiết một liên kết (API E1). Thuật toán ở {@link Linker} (lõi, tất định); ở đây chỉ đọc IR + luật, ghi
 * kết quả. Liên kết chốt tay KHÔNG được ghi vào IR mà áp lúc dò vết, nên luôn thắng và giữ qua mọi lần nối lại.
 */
@Service
public class LinkingService {

    private final GraphQueryService graphQueries;
    private final GraphWriteService graphWriter;
    private final WorkspaceService workspaceService;
    private final LinkOverrideService overrideService;
    private final Linker linker = new Linker();

    public LinkingService(
            GraphQueryService graphQueries,
            GraphWriteService graphWriter,
            WorkspaceService workspaceService,
            LinkOverrideService overrideService) {
        this.graphQueries = graphQueries;
        this.graphWriter = graphWriter;
        this.workspaceService = workspaceService;
        this.overrideService = overrideService;
    }

    /**
     * E1 — chi tiết một liên kết: mức tin cậy tự động, lý do, các ứng viên kèm mức khớp, vị trí điểm gọi và quyết định chốt
     * tay đang áp (nếu có).
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code EDGE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public EdgeDetailResponse edgeDetail(UUID workspaceId, UUID edgeId) {
        workspaceService.requireExists(workspaceId);
        IrEdgeRow row = graphQueries.edge(workspaceId, edgeId)
                .orElseThrow(() -> new AppException(ErrorCode.EDGE_NOT_FOUND, "Không tìm thấy liên kết; hãy dò vết lại sau khi quét"));
        IrEdge edge = row.edge();
        Set<String> keys = new TreeSet<>();
        keys.add(edge.fromKey());
        if (edge.toKey() != null) {
            keys.add(edge.toKey());
        }
        edge.candidates().forEach(c -> keys.add(c.nodeKey()));
        Map<String, IrNode> nodes = new HashMap<>();
        graphQueries.nodesByKeys(workspaceId, keys).forEach(r -> nodes.put(r.node().key(), r.node()));

        List<EdgeCandidateResponse> candidates = edge.candidates().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::nodeKey))
                .map(c -> {
                    CandidateScorer.Level level = CandidateScorer.level(c.score());
                    IrNode node = nodes.get(c.nodeKey());
                    return new EdgeCandidateResponse(
                            node == null ? null : EdgeNodeResponse.from(node), c.nodeKey(), level, level.label(), c.reason(),
                            c.nodeKey().equals(edge.toKey()));
                })
                .toList();
        boolean overridable = LinkOverrideService.TARGET_KINDS.containsKey(edge.kind());
        return new EdgeDetailResponse(
                row.id().toString(),
                edge.kind(),
                edge.confidence(),
                edge.reason(),
                nodes.containsKey(edge.fromKey()) ? EdgeNodeResponse.from(nodes.get(edge.fromKey())) : null,
                edge.toKey() != null && nodes.containsKey(edge.toKey()) ? EdgeNodeResponse.from(nodes.get(edge.toKey())) : null,
                candidates,
                edge.location() == null ? null : edge.location().filePath(),
                edge.location() == null ? null : edge.location().startLine(),
                overridable ? overrideService.findFor(workspaceId, edge.fromKey(), edge.kind(), nodes) : null,
                overridable);
    }

    /**
     * Bước LINK của một lần quét: ghi luật tự phát hiện của lần quét này rồi nối lại toàn bộ.
     *
     * @param detected luật tự phát hiện (proxy, context-path…), đã bỏ trùng
     */
    @Transactional
    public LinkSummary linkAfterScan(UUID workspaceId, List<LinkRule> detected) {
        workspaceService.updateDetectedLinkRules(workspaceId, detected);
        return relink(workspaceId);
    }

    /** Nối lại mọi lời gọi HTTP của Workspace theo IR và luật hiện tại (một transaction). */
    @Transactional
    public LinkSummary relink(UUID workspaceId) {
        List<LinkRule> rules = workspaceService.linkRules(workspaceId).effective();
        Map<String, IrEdge> before = new HashMap<>();
        graphQueries.edgesOfKind(workspaceId, EdgeKind.HTTP_REQUEST).stream()
                .map(IrEdgeRow::edge)
                .forEach(e -> before.put(e.fromKey(), e));
        Linker.Result result = linker.link(nodes(workspaceId, NodeKind.HTTP_CALL), nodes(workspaceId, NodeKind.API_ENDPOINT), rules);
        graphWriter.replaceLinks(workspaceId, result.edges(), result.externals());
        return summarize(result.edges(), before);
    }

    private List<IrNode> nodes(UUID workspaceId, NodeKind kind) {
        return graphQueries.nodesOfKind(workspaceId, kind).stream().map(IrNodeRow::node).toList();
    }

    private static LinkSummary summarize(List<IrEdge> edges, Map<String, IrEdge> before) {
        int certain = 0;
        int inferred = 0;
        int unresolved = 0;
        int external = 0;
        int changed = 0;
        for (IrEdge e : edges) {
            switch (e.confidence()) {
                case CERTAIN -> certain++;
                case UNRESOLVED -> unresolved++;
                default -> inferred++;
            }
            if (e.toKey() != null && e.toKey().startsWith(NodeKeys.EXTERNAL + ":")) {
                external++;
            }
            IrEdge old = before.get(e.fromKey());
            if (old == null || !Objects.equals(old.toKey(), e.toKey()) || old.confidence() != e.confidence()) {
                changed++;
            }
        }
        return new LinkSummary(edges.size(), certain, inferred, unresolved, external, changed);
    }
}
