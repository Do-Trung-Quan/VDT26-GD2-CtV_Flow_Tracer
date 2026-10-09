package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.graph.dto.response.BackendTreeResponse;
import com.ctv.flowtracer.app.graph.repository.IrEdgeRepository;
import com.ctv.flowtracer.app.graph.repository.IrNodeRepository;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceRole;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
