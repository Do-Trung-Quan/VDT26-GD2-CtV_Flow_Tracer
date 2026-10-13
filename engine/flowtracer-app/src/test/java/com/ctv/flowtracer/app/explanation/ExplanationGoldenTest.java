package com.ctv.flowtracer.app.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.SpringNoiseFilters;
import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.core.explain.ConstraintsExtractor;
import com.ctv.flowtracer.core.explain.IoSchemaBuilder;
import com.ctv.flowtracer.core.explain.PurposeRule;
import com.ctv.flowtracer.core.explain.UiFlowResolver;
import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.core.linker.Linker;
import com.ctv.flowtracer.core.mock.FallbackMockGenerator;
import com.ctv.flowtracer.core.scenario.ScenarioBuilder;
import com.ctv.flowtracer.core.tracer.TraceRequest;
import com.ctv.flowtracer.core.tracer.Tracer;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.scenario.Scenario;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.trace.Trace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nội dung tab Diễn giải và Kịch bản dựng từ IR thật: phân tích {@code fixtures/spring-flow} bằng adapter Spring, nối với
 * Frontend {@code fixtures/angular-mini} (golden của sidecar) bằng Linker, rồi dựng đầu vào / đầu ra / ràng buộc / dữ liệu mẫu /
 * kịch bản và so với {@code fixtures/golden/spring-flow.explain.*}. Sinh lại bằng {@code -Pgolden-update}.
 */
class ExplanationGoldenTest {

    private static final Path FIXTURES = Path.of("..", "..", "fixtures");
    private static final Path GOLDEN = FIXTURES.resolve("golden");
    private static final String CREATE = "java:endpoint:POST /api/v1/categories";
    private static final String SAVE_BUTTON = "ng:element:admin-app/CategoryFormDialogComponent#app-button.submit";

    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final IrFragment backend = analyze();
    private final IrFragment linked = link(backend, frontend());
    private final Map<String, IrNode> types = new TreeMap<>();
    private final Function<String, Optional<IrNode>> typeLookup = key -> Optional.ofNullable(types.get(key));
    private final GraphIndex index = new GraphIndex(new Subgraph(linked.nodes(), linked.edges()));

    ExplanationGoldenTest() {
        linked.nodes().stream().filter(n -> n.kind() == NodeKind.DATA_TYPE).forEach(n -> types.put(n.key(), n));
    }

    private List<IrNode> endpoints() {
        return linked.nodes().stream().filter(n -> n.kind() == NodeKind.API_ENDPOINT).toList();
    }

    private IrNode node(String key) {
        return index.node(key).orElseThrow(() -> new AssertionError("Không có node " + key));
    }

    // ---------------- Diễn giải ----------------

    @Test
    void everyApiMatchesItsGoldenExplanation() throws Exception {
        assertThat(endpoints()).hasSize(6);
        for (IrNode endpoint : endpoints()) {
            String name = endpoint.name().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase();
            assertGolden("spring-flow.explain." + name + ".json", json.writeValueAsString(explain(endpoint)) + "\n");
        }
    }

    private Map<String, Object> explain(IrNode endpoint) {
        IoSchemaBuilder.IoSection io = new IoSchemaBuilder(typeLookup).build(endpoint, index);
        var sample = new FallbackMockGenerator().generate(endpoint, typeLookup);
        Map<String, Object> out = new LinkedHashMap<>();
        PurposeRule.Purpose purpose = new PurposeRule().forEndpoint(endpoint);
        out.put("purpose", Map.of("text", purpose.text(), "source", purpose.source().name()));
        out.put("input", io.input());
        out.put("output", io.output());
        out.put("errors", io.errors());
        out.put("constraints", new ConstraintsExtractor(typeLookup).extract(endpoint, index));
        out.put("mock", Map.of("request", sample.request(), "response", sample.response() == null ? "null" : sample.response()));
        return out;
    }

    @Test
    void createApiShowsValidationPermissionAndDuplicateRules() {
        IrNode create = node(CREATE);
        IoSchemaBuilder.IoSection io = new IoSchemaBuilder(typeLookup).build(create, index);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) io.input().get("body");
        assertThat(body.get("code")).isEqualTo("string — bắt buộc, tối đa 50 ký tự. Mã danh mục, không trùng với danh mục khác.");
        assertThat(body.get("title")).isEqualTo("string — bắt buộc, tối đa 255 ký tự");
        assertThat(body.get("brief")).isEqualTo("string — không bắt buộc, tối đa 1000 ký tự");
        assertThat(body.get("status")).isEqualTo("string (một trong: ACTIVE, INACTIVE) — không bắt buộc");
        // lớp bọc ApiResponse<T> được giữ, T mở thành dữ liệu thật
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) io.output().get("data");
        assertThat(io.output()).containsKey("message");
        assertThat(data.keySet()).containsExactly("id", "code", "title", "status");
        assertThat(io.errors()).containsExactly("mã danh mục đã tồn tại", "thiếu trường bắt buộc hoặc sai định dạng", "không đủ quyền");

        assertThat(new ConstraintsExtractor(typeLookup).extract(create, index)).containsExactly(
                "Cần quyền: hasAuthority('CATEGORY_MANAGE')",
                "Trường code: bắt buộc, tối đa 50 ký tự",
                "Trường title: bắt buộc, tối đa 255 ký tự",
                "Trường brief: tối đa 1000 ký tự",
                "Nếu categoryRepository.existsByCode(code) thì báo lỗi “Mã danh mục đã tồn tại”",
                "Cột code của bảng category không được trùng");
    }

    @Test
    void getApiListsQueryParametersAndNoBody() {
        IoSchemaBuilder.IoSection io = new IoSchemaBuilder(typeLookup).build(node("java:endpoint:GET /api/v1/categories"), index);

        assertThat(io.input()).containsOnlyKeys("query");
        assertThat(io.input().get("query")).isEqualTo(Map.of("keyword", "string — bắt buộc"));
    }

    @Test
    void mockDataAlwaysMatchesTheDtoSchema() {
        for (IrNode endpoint : endpoints()) {
            FallbackMockGenerator.Sample sample = new FallbackMockGenerator().generate(endpoint, typeLookup);
            assertThat(sample.problems()).as(endpoint.name()).isEmpty();
        }
        var create = new FallbackMockGenerator().generate(node(CREATE), typeLookup);
        assertThat(create.request()).isEqualTo(Map.of("code", "MA001", "title", "Tên mẫu", "brief", "Mô tả mẫu", "status", "ACTIVE"));
        // cùng schema → cùng dữ liệu
        assertThat(new FallbackMockGenerator().generate(node(CREATE), typeLookup)).isEqualTo(create);
    }

    @Test
    void purposeFallsBackFromOperationToJavadocToSkeleton() {
        PurposeRule rule = new PurposeRule();

        PurposeRule.Purpose operation = rule.forEndpoint(node(CREATE));
        assertThat(operation.source()).isEqualTo(PurposeRule.Source.OPERATION);
        assertThat(operation.text()).isEqualTo("Thêm mới danh mục");

        PurposeRule.Purpose skeleton = rule.forEndpoint(endpoints().stream()
                .filter(e -> e.attrs().get("summarySource") == null)
                .findFirst()
                .orElseThrow());
        assertThat(skeleton.source()).isEqualTo(PurposeRule.Source.SKELETON);
        assertThat(skeleton.text()).startsWith("API ").contains("thuộc nhóm").endsWith(".");
    }

    @Test
    void uiElementTakesItsIoFromTheApiItCalls() {
        IrNode button = node(SAVE_BUTTON);
        UiFlowResolver ui = UiFlowResolver.of(button, index);

        assertThat(ui.apis()).extracting(IrNode::key).contains(CREATE);
        PurposeRule.Purpose purpose = new PurposeRule().forElement(button, "Lưu", ui.apis(), ui.unresolved(), List.of());
        assertThat(purpose.source()).isEqualTo(PurposeRule.Source.SKELETON);
        assertThat(purpose.text()).startsWith("Nút “Lưu” dùng để gọi").contains("Thêm mới danh mục");

        IrNode openDialog = node("ng:element:admin-app/CategoryListComponent#id.btn-create");
        UiFlowResolver opens = UiFlowResolver.of(openDialog, index);
        assertThat(opens.apis()).isEmpty();
        assertThat(opens.opens()).extracting(IrNode::name).isNotEmpty();
    }

    // ---------------- Kịch bản ----------------

    @Test
    void scenarioHasExactlyOneStepPerArrowForEveryTrace() throws Exception {
        var tracer = new Tracer();
        var builder = new ScenarioBuilder();
        var starts = new ArrayList<>(endpoints().stream().map(IrNode::key).toList());
        starts.add(SAVE_BUTTON);
        for (String start : starts) {
            Trace trace = tracer.trace(new Subgraph(linked.nodes(), linked.edges()), new TraceRequest(start, 0, "scan-1", SpringNoiseFilters.DEFAULTS, List.of()));
            Scenario scenario = builder.build(trace);

            assertThat(scenario.items().stream().filter(i -> i.index() != null).map(Scenario.Item::index))
                    .as(start)
                    .containsExactlyElementsOf(trace.steps().stream().map(s -> s.index()).toList());
            assertThat(scenario.actors()).as(start).extracting(Scenario.Actor::name)
                    .containsExactlyElementsOf(trace.participants().stream().map(p -> p.label()).toList());
            assertThat(scenario.items()).as(start).allSatisfy(i -> assertThat(i.text()).isNotBlank().doesNotContain("null"));
        }
    }

    @Test
    void scenarioOfTheSaveButtonMatchesItsGolden() throws Exception {
        Trace trace = new Tracer().trace(
                new Subgraph(linked.nodes(), linked.edges()), new TraceRequest(SAVE_BUTTON, 0, "scan-1", SpringNoiseFilters.DEFAULTS, List.of()));
        Scenario scenario = new ScenarioBuilder().build(trace);

        assertThat(scenario.items().get(0).text()).isEqualTo("User gửi biểu mẫu bằng \"Lưu\" trên Frontend.");
        assertThat(scenario.items().get(1).text()).isEqualTo("Frontend gửi yêu cầu POST /api/v1/categories đến Controller Danh mục.");
        assertThat(scenario.items().get(2).text()).isEqualTo("Controller Danh mục chuyển tiếp yêu cầu create(request) đến Service Danh mục.");
        // nhánh lỗi: dòng điều kiện, rồi các bước thụt vào; phần còn lại của luồng là nhánh "ngược lại"
        var conditions = scenario.items().stream().filter(Scenario.Item::isCondition).toList();
        assertThat(conditions).extracting(Scenario.Item::text).containsExactly("Nếu categoryRepository.existsByCode(code):", "Ngược lại:");
        var block = trace.blocks().get(0);
        assertThat(scenario.items().stream().filter(i -> i.index() != null && i.index() >= block.fromStep() && i.index() <= block.toStep()))
                .isNotEmpty()
                .allSatisfy(i -> assertThat(i.depth()).isEqualTo(1));
        assertThat(scenario.items().stream().filter(i -> i.index() != null && i.index() < block.fromStep()))
                .allSatisfy(i -> assertThat(i.depth()).isZero());
        assertThat(scenario.actors().get(2).description()).isEqualTo("Tiếp nhận và điều phối yêu cầu danh mục ở Backend.");
        assertGolden("spring-flow.scenario.category-save.json", json.writeValueAsString(scenario) + "\n");
    }

    // ---------------- tiện ích ----------------

    private void assertGolden(String file, String actual) throws IOException {
        Path path = GOLDEN.resolve(file);
        String normalized = actual.replace("\r\n", "\n");
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(path, normalized, StandardCharsets.UTF_8);
        }
        assertThat(normalized).as(file).isEqualTo(Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private IrFragment frontend() {
        try {
            return json.readValue(GOLDEN.resolve("angular-mini.ir.json").toFile(), IrFragment.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Như bước LINK của lần quét: nối lời gọi HTTP của Frontend và Backend với API bằng luật tự phát hiện. */
    private static IrFragment link(IrFragment backend, IrFragment frontend) {
        var nodes = new ArrayList<IrNode>(backend.nodes().stream().filter(n -> n.kind() != NodeKind.EXTERNAL).toList());
        var edges = new ArrayList<IrEdge>(backend.edges().stream().filter(e -> e.kind() != EdgeKind.HTTP_REQUEST).toList());
        var rules = new ArrayList<>(backend.detectedLinkRules());
        nodes.addAll(frontend.nodes());
        edges.addAll(frontend.edges());
        rules.addAll(frontend.detectedLinkRules());
        Linker.Result result = new Linker().link(
                nodes.stream().filter(n -> n.kind() == NodeKind.HTTP_CALL).toList(),
                nodes.stream().filter(n -> n.kind() == NodeKind.API_ENDPOINT).toList(),
                rules);
        nodes.addAll(result.externals());
        edges.addAll(result.edges());
        return new IrFragment(backend.adapterId(), nodes, edges, rules, backend.diagnostics());
    }

    private static IrFragment analyze() {
        var adapter = new SpringStackAdapter();
        Path root = FIXTURES.resolve("spring-flow");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> allPaths = files.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith("target/"))
                    .sorted()
                    .toList();
            var extractCtx = new ExtractContext("fixture", allPaths, p -> load(root, p), null);
            List<FileFacts> facts = allPaths.stream()
                    .filter(adapter::analyzes)
                    .map(p -> adapter.extract(extractCtx, sourceFile(root, p)))
                    .toList();
            return adapter.resolve(new ResolveContext("fixture", allPaths, p -> load(root, p), null, null), facts);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<String> load(Path root, String relativePath) {
        try {
            return Optional.of(Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static SourceFile sourceFile(Path root, String relativePath) {
        String content = load(root, relativePath).orElseThrow();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
            return new SourceFile(relativePath, content, hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void elementWhoseCallIsUnresolvedStillSaysItCallsTheServer() {
        // Xuất Excel gọi một đường dẫn mà spring-flow không có: gọi máy chủ nhưng chưa biết API nào
        IrNode export = node("ng:element:admin-app/CategoryListComponent#button.click.onExport");
        UiFlowResolver ui = UiFlowResolver.of(export, index);

        assertThat(ui.apis()).isEmpty();
        assertThat(ui.unresolved()).extracting(IrNode::name).singleElement().asString().startsWith("GET ");
        PurposeRule.Purpose purpose = new PurposeRule().forElement(export, "Xuất Excel", ui.apis(), ui.unresolved(), List.of());
        assertThat(purpose.text()).startsWith("Nút “Xuất Excel” gọi máy chủ nhưng 1 lời gọi chưa xác định được API đích (GET ")
                .contains("hãy chốt tay liên kết")
                .doesNotContain("không gọi máy chủ");
    }
}
