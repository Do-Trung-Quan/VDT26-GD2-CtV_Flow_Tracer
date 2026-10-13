package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.model.SourceGraphWriteResult;
import com.ctv.flowtracer.app.graph.repository.IrEdgeRepository;
import com.ctv.flowtracer.app.graph.repository.IrNodeRepository;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi IR — service công khai cho package {@code scan}. Phải chạy TRONG transaction của bước lưu của lần quét, để lỗi
 * giữa chừng thì toàn bộ IR cũ được giữ nguyên.
 */
@Service
public class GraphWriteService {

    private final IrNodeRepository nodes;
    private final IrEdgeRepository edges;

    public GraphWriteService(IrNodeRepository nodes, IrEdgeRepository edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    /**
     * Thay IR của một Source bằng kết quả phân tích mới: upsert node theo key (giữ id cũ), xóa node không còn, thay toàn
     * bộ edge xuất phát từ Source. Node có key đang thuộc Source khác của Workspace bị bỏ qua (báo trong kết quả).
     *
     * @param fileIdsByPath đường dẫn file (tương đối trong Source) → id trong bảng {@code source_files}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SourceGraphWriteResult replaceSourceGraph(
            UUID workspaceId, UUID sourceId, IrFragment fragment, Map<String, UUID> fileIdsByPath) {
        List<String> newKeys = fragment.nodes().stream().map(IrNode::key).toList();
        Set<String> skipped = new TreeSet<>(nodes.findKeysOwnedByOtherSources(workspaceId, sourceId, newKeys));

        Set<String> kept = new HashSet<>(newKeys);
        kept.removeAll(skipped);
        List<String> removed = nodes.findKeysBySource(sourceId).stream().filter(k -> !kept.contains(k)).toList();
        nodes.deleteBySourceAndKeys(sourceId, removed);

        List<IrNodeRow> rows = fragment.nodes().stream()
                .filter(n -> !skipped.contains(n.key()))
                .map(n -> new IrNodeRow(null, workspaceId, sourceId, fileId(filePathOf(n), fileIdsByPath), n))
                .toList();
        nodes.upsertAll(rows);

        Set<String> endpointKeys = new TreeSet<>();
        fragment.edges().forEach(e -> {
            endpointKeys.add(e.fromKey());
            if (e.toKey() != null) {
                endpointKeys.add(e.toKey());
            }
        });
        Map<String, UUID> ids = nodes.findIdsByKeys(workspaceId, endpointKeys);
        var edgeRows = new ArrayList<IrEdgeRow>();
        int skippedEdges = 0;
        for (IrEdge e : fragment.edges()) {
            UUID from = ids.get(e.fromKey());
            if (from == null || skipped.contains(e.fromKey())) {
                skippedEdges++;
                continue;
            }
            UUID to = e.toKey() == null ? null : ids.get(e.toKey());
            String file = e.location() == null ? null : e.location().filePath();
            edgeRows.add(new IrEdgeRow(null, workspaceId, from, to, fileId(file, fileIdsByPath), e));
        }
        edges.replaceForSource(sourceId, edgeRows);
        return new SourceGraphWriteResult(rows.size(), removed.size(), edgeRows.size(), List.copyOf(skipped), skippedEdges);
    }

    /**
     * Thay toàn bộ liên kết của Workspace bằng kết quả mới của Linker: xóa mọi edge {@code HTTP_REQUEST} và node
     * {@code EXTERNAL} cũ, ghi node {@code EXTERNAL} mới (thuộc Source của lời gọi đầu tiên trỏ tới nó) rồi ghi edge. Edge
     * có node đi không còn trong IR bị bỏ qua.
     *
     * @return số edge đã ghi
     */
    @Transactional
    public int replaceLinks(UUID workspaceId, List<IrEdge> links, List<IrNode> externals) {
        edges.deleteByWorkspaceAndKind(workspaceId, EdgeKind.HTTP_REQUEST);
        nodes.deleteByWorkspaceAndKind(workspaceId, NodeKind.EXTERNAL);

        Set<String> callKeys = new TreeSet<>();
        links.forEach(e -> callKeys.add(e.fromKey()));
        Map<String, IrNodeRow> calls = new HashMap<>();
        nodes.findByKeys(workspaceId, callKeys).forEach(r -> calls.put(r.node().key(), r));

        Map<String, UUID> externalSource = new HashMap<>();
        for (IrEdge e : links) {
            IrNodeRow call = calls.get(e.fromKey());
            if (call != null && e.toKey() != null) {
                externalSource.putIfAbsent(e.toKey(), call.sourceId());
            }
        }
        List<IrNodeRow> externalRows = externals.stream()
                .filter(n -> externalSource.containsKey(n.key()))
                .map(n -> new IrNodeRow(null, workspaceId, externalSource.get(n.key()), null, n))
                .toList();
        nodes.upsertAll(externalRows);

        Set<String> targetKeys = new TreeSet<>();
        links.forEach(e -> {
            if (e.toKey() != null) {
                targetKeys.add(e.toKey());
            }
        });
        Map<String, UUID> targetIds = nodes.findIdsByKeys(workspaceId, targetKeys);
        var rows = new ArrayList<IrEdgeRow>();
        for (IrEdge e : links) {
            IrNodeRow call = calls.get(e.fromKey());
            if (call == null) {
                continue;
            }
            UUID to = e.toKey() == null ? null : targetIds.get(e.toKey());
            if (e.toKey() != null && to == null) {
                continue;
            }
            rows.add(new IrEdgeRow(null, workspaceId, call.id(), to, call.fileId(), e));
        }
        edges.insertAll(rows);
        return rows.size();
    }

    private static String filePathOf(IrNode node) {
        return node.location() == null ? null : node.location().filePath();
    }

    private static UUID fileId(String filePath, Map<String, UUID> fileIdsByPath) {
        return filePath == null ? null : fileIdsByPath.get(filePath);
    }
}
