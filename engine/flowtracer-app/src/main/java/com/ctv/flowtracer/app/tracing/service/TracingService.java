package com.ctv.flowtracer.app.tracing.service;

import com.ctv.flowtracer.app.linking.service.LinkOverrideService;
import com.ctv.flowtracer.core.tracer.OverrideRule;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.tracing.dto.request.CreateTraceRequest;
import com.ctv.flowtracer.app.tracing.dto.response.TraceExportResponse;
import com.ctv.flowtracer.app.tracing.dto.response.TraceResponse;
import com.ctv.flowtracer.app.tracing.model.CachedTrace;
import com.ctv.flowtracer.app.tracing.model.ExportFormat;
import com.ctv.flowtracer.app.workspace.model.TraceSettings;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.diagram.PlantUmlExporter;
import com.ctv.flowtracer.core.diagram.SequenceViewModelBuilder;
import com.ctv.flowtracer.core.diagram.SvgExporter;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.core.tracer.TraceIds;
import com.ctv.flowtracer.core.tracer.TraceRequest;
import com.ctv.flowtracer.core.tracer.Tracer;
import com.ctv.flowtracer.core.tracer.UnsupportedTraceStartException;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.trace.Trace;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dò vết luồng (API D1) và xuất diagram (API D2). Kết quả tính từ IR đã quét, không gọi AI, không lưu CSDL — giữ trong
 * {@link TraceCache} theo {@code trace_id}.
 */
@Service
public class TracingService {

    private final WorkspaceService workspaceService;
    private final GraphQueryService graphQueryService;
    private final LinkOverrideService overrideService;
    private final TraceCache cache;
    private final Tracer tracer = new Tracer();

    public TracingService(
            WorkspaceService workspaceService,
            GraphQueryService graphQueryService,
            LinkOverrideService overrideService,
            TraceCache cache) {
        this.workspaceService = workspaceService;
        this.graphQueryService = graphQueryService;
        this.overrideService = overrideService;
        this.cache = cache;
    }

    /**
     * D1 — dò vết từ một API Backend hoặc từ một phần tử giao diện (End-to-End nếu có gọi API, chỉ giao diện nếu không). Áp
     * liên kết chốt tay và bộ lọc nhiễu của Workspace.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code NODE_NOT_FOUND} nếu chưa quét hoặc không có node;
     *     {@code TRACE_START_UNSUPPORTED} nếu node xuất phát không phải API Backend / phần tử giao diện
     */
    public TraceResponse trace(UUID workspaceId, CreateTraceRequest request) {
        TraceSettings settings = workspaceService.traceSettings(workspaceId);
        int maxDepth = request.maxDepth() != null
                ? request.maxDepth()
                : settings.defaultMaxDepth() != null ? settings.defaultMaxDepth() : TraceRequest.DEFAULT_MAX_DEPTH;
        List<OverrideRule> overrides = overrideService.rules(workspaceId);
        // Liên kết đổi khi quét lại (lần quét mới) hoặc khi sửa luật (nối lại ngay, không quét): cả hai nằm trong khóa
        String linkState = settings.lastScanRunId() + "/" + workspaceService.linkRules(workspaceId).effective().stream()
                .map(r -> r.type() + ":" + r.pattern() + ">" + r.replacement())
                .collect(Collectors.joining(","));
        var traceRequest = new TraceRequest(request.startKey(), maxDepth, linkState, settings.noiseFilters(), overrides);
        String traceId = TraceIds.of(traceRequest);

        CachedTrace cached = cache.get(workspaceId, traceId).orElse(null);
        if (cached == null) {
            cached = compute(workspaceId, traceRequest);
            cache.put(workspaceId, traceId, cached);
        }
        return TraceResponse.from(cached.trace(), cached.startName(), cached.startDisplayName(), cached.maxDepth());
    }

    private CachedTrace compute(UUID workspaceId, TraceRequest traceRequest) {
        Map<String, Set<String>> pinTargets = new HashMap<>();
        traceRequest.overrides().stream()
                .filter(o -> o.decision() == OverrideRule.Decision.PIN)
                .forEach(o -> pinTargets.computeIfAbsent(o.fromKey(), k -> new HashSet<>()).add(o.toKey()));
        Subgraph graph = graphQueryService.subgraphFrom(workspaceId, traceRequest.startKey(), traceRequest.maxDepth(), pinTargets);
        IrNode start = graph.nodes().stream()
                .filter(n -> n.key().equals(traceRequest.startKey()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NODE_NOT_FOUND, "Không tìm thấy thành phần cần dò vết"));
        if (start.kind() != NodeKind.API_ENDPOINT && start.kind() != NodeKind.UI_ELEMENT) {
            throw new AppException(
                    ErrorCode.TRACE_START_UNSUPPORTED,
                    "Chỉ dò vết được từ một API Backend hoặc một phần tử giao diện (nút, ô nhập…)");
        }
        Trace trace;
        try {
            trace = tracer.trace(graph, traceRequest);
        } catch (UnsupportedTraceStartException e) {
            throw new AppException(ErrorCode.TRACE_START_UNSUPPORTED, e.getMessage());
        }
        return new CachedTrace(trace, start.name(), start.displayName(), traceRequest.maxDepth());
    }

    /**
     * D2 — xuất diagram của một kết quả dò vết đã tính.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code TRACE_NOT_FOUND} nếu kết quả đã hết hạn trong bộ nhớ đệm
     *     (dò lại bằng D1 sẽ ra cùng {@code trace_id})
     */
    public TraceExportResponse export(UUID workspaceId, String traceId, ExportFormat format) {
        workspaceService.requireExists(workspaceId);
        CachedTrace cached = cache.get(workspaceId, traceId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.TRACE_NOT_FOUND, "Kết quả dò vết không còn trong bộ nhớ đệm; hãy dò vết lại rồi xuất"));
        Trace trace = cached.trace();
        String content = format == ExportFormat.SVG
                ? new SvgExporter().export(new SequenceViewModelBuilder().build(trace))
                : new PlantUmlExporter().export(trace);
        return new TraceExportResponse(format, fileName(cached) + "." + format.extension(), format.mediaType(), content);
    }

    /** Tên file từ tên thành phần xuất phát: ký tự không phải chữ/số thành dấu gạch dưới. */
    private static String fileName(CachedTrace cached) {
        String name = cached.startName().replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        return name.isEmpty() ? "luong" : name;
    }
}
