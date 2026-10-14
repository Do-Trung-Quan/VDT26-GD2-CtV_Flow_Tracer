package com.ctv.flowtracer.app.explanation.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.explanation.dto.response.ApiIoResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ExplanationResponse;
import com.ctv.flowtracer.app.explanation.dto.response.MockDataResponse;
import com.ctv.flowtracer.app.explanation.dto.response.PurposeResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ScenarioResponse;
import com.ctv.flowtracer.app.graph.dto.response.NodeRelationResponse;
import com.ctv.flowtracer.app.graph.model.ContainerStats;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.graph.service.NodeDetailService;
import com.ctv.flowtracer.app.linking.service.LinkOverrideService;
import com.ctv.flowtracer.app.tracing.model.CachedTrace;
import com.ctv.flowtracer.app.tracing.service.TracingService;
import com.ctv.flowtracer.core.explain.ConstraintsExtractor;
import com.ctv.flowtracer.core.explain.IoSchemaBuilder;
import com.ctv.flowtracer.core.explain.PurposeRule;
import com.ctv.flowtracer.core.explain.UiFlowResolver;
import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.core.mock.FallbackMockGenerator;
import com.ctv.flowtracer.core.scenario.ScenarioBuilder;
import com.ctv.flowtracer.core.tracer.OverrideApplier;
import com.ctv.flowtracer.core.tracer.OverrideRule;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tab Diễn giải, dữ liệu mẫu và tab Kịch bản (API F1–F3), phần TẤT ĐỊNH — chạy được khi {@code flowtracer.ai.provider=none}.
 * Mọi nội dung suy từ IR đã quét (đã áp liên kết chốt tay, như dò vết): mục đích theo {@code @Operation} → Javadoc → câu khung;
 * đầu vào / đầu ra từ DTO; ràng buộc rút từ code; dữ liệu mẫu từ cấu trúc DTO; kịch bản từ luồng đã dò.
 */
@Service
public class ExplanationService {

    /** Độ sâu nạp đồ thị con khi diễn giải (như độ sâu dò vết mặc định). */
    private static final int FLOW_DEPTH = 12;

    private final GraphQueryService graphQueries;
    private final NodeDetailService nodeDetails;
    private final LinkOverrideService overrideService;
    private final TracingService tracingService;
    private final ExplanationAiService ai;
    private final PurposeRule purposeRule = new PurposeRule();
    private final ScenarioBuilder scenarioBuilder = new ScenarioBuilder();

    public ExplanationService(
            GraphQueryService graphQueries,
            NodeDetailService nodeDetails,
            LinkOverrideService overrideService,
            TracingService tracingService,
            ExplanationAiService ai) {
        this.ai = ai;
        this.graphQueries = graphQueries;
        this.nodeDetails = nodeDetails;
        this.overrideService = overrideService;
        this.tracingService = tracingService;
    }

    /**
     * F1 — nội dung tab Diễn giải.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code NODE_NOT_FOUND}; {@code EXPLANATION_UNSUPPORTED} nếu loại thành
     *     phần này không có tab Diễn giải
     */
    @Transactional(readOnly = true)
    public ExplanationResponse explain(UUID workspaceId, String nodeKey) {
        return ai.applyPurpose(workspaceId, deterministicExplain(workspaceId, nodeKey));
    }

    /** F1 chỉ phần tất định (chưa áp nội dung AI) — dùng để dựng đầu vào cho AI và làm phương án dự phòng. */
    @Transactional(readOnly = true)
    public ExplanationResponse deterministicExplain(UUID workspaceId, String nodeKey) {
        IrNodeRow row = graphQueries.requireNode(workspaceId, nodeKey);
        IrNode node = row.node();
        return switch (node.kind()) {
            case UI_APP, UI_SCREEN, UI_TAB, UI_DIALOG, UI_FORM -> container(workspaceId, row);
            case API_ENDPOINT, UI_ELEMENT -> flow(workspaceId, row);
            default -> throw new AppException(
                    ErrorCode.EXPLANATION_UNSUPPORTED, "Chỉ diễn giải được API, phần tử giao diện và thành phần chứa chúng");
        };
    }

    /**
     * F2 — dữ liệu mẫu của một API.
     *
     * @param apiKey API cần lấy khi thành phần là phần tử giao diện gọi nhiều API; bỏ trống thì lấy API đầu tiên
     * @throws AppException {@code NODE_NOT_FOUND}; {@code EXPLANATION_UNSUPPORTED} nếu thành phần không gọi API
     */
    @Transactional(readOnly = true)
    public MockDataResponse mockData(UUID workspaceId, String nodeKey, String apiKey) {
        MockContext m = mockContext(workspaceId, nodeKey, apiKey);
        var base = new MockDataResponse(m.api().key(), m.sample().request(), m.sample().response(), "RULE", m.sample().problems(), null);
        return ai.applyMock(workspaceId, m.api(), m.schemas(), base);
    }

    /** Dữ liệu để sinh dữ liệu mẫu của một API: node API, mẫu tất định và schema của request / response. */
    public record MockContext(IrNode api, FallbackMockGenerator.Sample sample, FallbackMockGenerator.Schemas schemas) {}

    @Transactional(readOnly = true)
    public MockContext mockContext(UUID workspaceId, String nodeKey, String apiKey) {
        IrNodeRow row = graphQueries.requireNode(workspaceId, nodeKey);
        FlowContext ctx = flowOf(workspaceId, row);
        IrNode api = ctx.apis().stream()
                .filter(a -> apiKey == null || apiKey.isBlank() || a.key().equals(apiKey))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.EXPLANATION_UNSUPPORTED, "Thành phần này không gọi API nào để sinh dữ liệu mẫu"));
        var generator = new FallbackMockGenerator();
        return new MockContext(api, generator.generate(api, ctx.types()), generator.schemas(api, ctx.types()));
    }

    /**
     * F3 — nội dung tab Kịch bản của luồng đã dò.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code TRACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public ScenarioResponse scenario(UUID workspaceId, String traceId) {
        ScenarioContext c = scenarioContext(workspaceId, traceId);
        return ai.applyScenario(workspaceId, c.framework(), ScenarioResponse.of(traceId, c.title(), c.framework()));
    }

    /** Những gì một phần tử giao diện gọi tới (đã áp liên kết chốt tay): API, lời gọi chưa xác định đích, hộp thoại và màn hình mở. */
    public record ElementFlow(List<IrNode> apis, List<IrNode> unresolved, List<IrNode> opens, List<IrNode> navigates) {}

    @Transactional(readOnly = true)
    public ElementFlow elementFlow(UUID workspaceId, IrNodeRow row) {
        UiFlowResolver ui = flowOf(workspaceId, row).ui();
        return new ElementFlow(ui.apis(), ui.unresolved(), ui.opens(), ui.navigates());
    }

    /** Khung kịch bản tất định của một luồng đã dò cùng tên chức năng. */
    public record ScenarioContext(String title, com.ctv.flowtracer.ir.scenario.Scenario framework) {}

    @Transactional(readOnly = true)
    public ScenarioContext scenarioContext(UUID workspaceId, String traceId) {
        CachedTrace cached = tracingService.cachedTrace(workspaceId, traceId);
        String title = cached.startDisplayName() != null && !cached.startDisplayName().isBlank() ? cached.startDisplayName() : cached.startName();
        return new ScenarioContext(title, scenarioBuilder.build(cached.trace()));
    }

    // ---------------- thành phần chứa ----------------

    private ExplanationResponse container(UUID workspaceId, IrNodeRow row) {
        IrNode node = row.node();
        ContainerStats stats = nodeDetails.containerStats(workspaceId, row);
        String label = label(node);
        PurposeRule.Purpose purpose = purposeRule.forContainer(typeLabel(node.kind()), label, stats.elements(), stats.callingApi(), stats.dialogs());
        NodeDetailService.Relations relations = nodeDetails.relations(workspaceId, row, null);
        return new ExplanationResponse(
                node.key(), node.kind(), node.name(), node.displayName(), true, purpose(purpose), List.of(), List.of(),
                relations.trail(), List.of(), 0, List.of(), null);
    }

    // ---------------- API và phần tử giao diện ----------------

    /** Đồ thị con đã áp chốt tay, kiểu dữ liệu và danh sách API của một thành phần. */
    private record FlowContext(Subgraph graph, List<IrNode> apis, Function<String, Optional<IrNode>> types, UiFlowResolver ui) {}

    private FlowContext flowOf(UUID workspaceId, IrNodeRow row) {
        List<OverrideRule> overrides = overrideService.rules(workspaceId);
        Subgraph loaded = graphQueries.subgraphFrom(workspaceId, row.node().key(), FLOW_DEPTH, LinkOverrideService.pinTargets(overrides));
        Subgraph graph = new OverrideApplier().apply(loaded, overrides);
        GraphIndex index = new GraphIndex(graph);
        UiFlowResolver ui = null;
        List<IrNode> apis;
        if (row.node().kind() == NodeKind.UI_ELEMENT) {
            ui = UiFlowResolver.of(row.node(), index);
            apis = ui.apis();
        } else if (row.node().kind() == NodeKind.API_ENDPOINT) {
            apis = List.of(row.node());
        } else {
            throw new AppException(ErrorCode.EXPLANATION_UNSUPPORTED, "Thành phần này không gọi API");
        }
        Map<String, IrNode> types = graphQueries.dataTypes(workspaceId, apis);
        return new FlowContext(graph, apis, key -> Optional.ofNullable(types.get(key)), ui);
    }

    private ExplanationResponse flow(UUID workspaceId, IrNodeRow row) {
        IrNode node = row.node();
        FlowContext ctx = flowOf(workspaceId, row);
        GraphIndex index = new GraphIndex(ctx.graph());
        var io = new IoSchemaBuilder(ctx.types());
        var constraints = new ConstraintsExtractor(ctx.types());
        List<ApiIoResponse> apis = ctx.apis().stream()
                .map(api -> {
                    IoSchemaBuilder.IoSection section = io.build(api, index);
                    return new ApiIoResponse(
                            api.key(),
                            String.valueOf(api.attrs().getOrDefault("httpMethod", "")),
                            String.valueOf(api.attrs().getOrDefault("path", api.name())),
                            api.displayName(),
                            section.input(),
                            section.output(),
                            section.errors(),
                            constraints.extract(api, index));
                })
                .toList();
        PurposeRule.Purpose purpose;
        if (node.kind() == NodeKind.API_ENDPOINT) {
            purpose = purposeRule.forEndpoint(node);
        } else {
            List<String> opens = java.util.stream.Stream.concat(ctx.ui().opens().stream(), ctx.ui().navigates().stream())
                    .map(n -> (n.kind() == NodeKind.UI_DIALOG ? "hộp thoại " : "màn hình ") + label(n))
                    .toList();
            purpose = purposeRule.forElement(node, label(node), ctx.apis(), ctx.ui().unresolved(), opens);
        }
        List<String> unresolved = ctx.ui() == null ? List.of() : ctx.ui().unresolved().stream().map(IrNode::name).toList();
        NodeDetailService.Relations relations = nodeDetails.relations(workspaceId, row, ctx.graph());
        return new ExplanationResponse(
                node.key(), node.kind(), node.name(), node.displayName(), false, purpose(purpose), apis, unresolved,
                relations.trail(), relations.calledFrom(), relations.calledFromTotal(), relations.callsTo(), null);
    }

    private static PurposeResponse purpose(PurposeRule.Purpose p) {
        return new PurposeResponse(p.text(), p.source().name(), p.source().label());
    }

    private static String label(IrNode node) {
        return node.displayName() != null && !node.displayName().isBlank() ? node.displayName() : node.name();
    }

    private static String typeLabel(NodeKind kind) {
        return switch (kind) {
            case UI_APP -> "Ứng dụng";
            case UI_SCREEN -> "Màn hình";
            case UI_TAB -> "Tab";
            case UI_DIALOG -> "Hộp thoại";
            case UI_FORM -> "Biểu mẫu";
            default -> kind.name();
        };
    }
}
