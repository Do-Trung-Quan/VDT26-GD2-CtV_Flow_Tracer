package com.ctv.flowtracer.app.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.SpringNoiseFilters;
import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.core.diagram.PlantUmlExporter;
import com.ctv.flowtracer.core.diagram.SequenceViewModelBuilder;
import com.ctv.flowtracer.core.diagram.SvgExporter;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.core.tracer.TraceRequest;
import com.ctv.flowtracer.core.tracer.Tracer;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden trace: phân tích {@code fixtures/spring-flow} bằng adapter Spring thật, dò vết từng API bằng Tracer thật, rồi so
 * với {@code fixtures/golden/spring-flow.trace.*}. Sinh lại bằng {@code -Pgolden-update}.
 */
class TracerGoldenTest {

    private static final Path FIXTURES = Path.of("..", "..", "fixtures");
    private static final Path GOLDEN = FIXTURES.resolve("golden");

    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final SpringStackAdapter adapter = new SpringStackAdapter();
    private final IrFragment fragment = analyze();
    private final Tracer tracer = new Tracer();

    @Test
    void everyApiMatchesItsGoldenTraceAndDiagram() throws Exception {
        List<IrNode> endpoints = fragment.nodes().stream().filter(n -> n.kind() == NodeKind.API_ENDPOINT).toList();
        assertThat(endpoints).hasSize(6);
        for (IrNode endpoint : endpoints) {
            Trace trace = trace(endpoint.key());
            String name = endpoint.name().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase();
            assertGolden("spring-flow.trace." + name + ".json", json.writeValueAsString(trace) + "\n");
            assertGolden("spring-flow.trace." + name + ".puml", new PlantUmlExporter().export(trace));
        }
    }

    @Test
    void createCategoryFollowsTheAppendixAShape() {
        Trace trace = trace("java:endpoint:POST /api/v1/categories");

        assertThat(trace.participants()).extracting(p -> p.id() + ":" + p.label()).containsExactly(
                "user:Client",
                "c1:Controller Danh mục",
                "s1:Service Danh mục",
                "r1:Repository Danh mục",
                "t1:Bảng category");
        assertThat(trace.steps()).extracting(s -> s.index() + " " + s.from() + (s.returning() ? " --> " : " -> ") + s.to() + " " + s.label())
                .containsExactly(
                        "1 user -> c1 POST /api/v1/categories",
                        "2 c1 -> s1 create(request)",
                        "3 s1 -> s1 checkPermission(), normalize()",
                        "4 s1 -> r1 existsByCode(code)",
                        "5 r1 -> t1 SELECT",
                        "6 t1 --> r1 kết quả",
                        "7 r1 --> s1 true / false",
                        "8 s1 --> c1 CATEGORY_CODE_EXISTS",
                        "9 c1 --> user BusinessException",
                        "10 s1 -> r1 save(entity)",
                        "11 r1 -> t1 INSERT/UPDATE",
                        "12 t1 --> r1 OK",
                        "13 r1 --> s1 Category",
                        "14 s1 -> s1 toResponse()",
                        "15 s1 --> c1 CategoryResponse",
                        "16 c1 --> user 200 ApiResponse<CategoryResponse>");
        // Nhánh lỗi là khối alt: "kiểm tra rồi ném lỗi" → phần còn lại là nhánh else
        assertThat(trace.blocks()).singleElement().satisfies(b -> {
            assertThat(b.type()).isEqualTo(TraceBlock.Type.ALT);
            assertThat(b.label()).isEqualTo("categoryRepository.existsByCode(code)");
            assertThat(b.fromStep()).isEqualTo(8);
            assertThat(b.toStep()).isEqualTo(16);
            assertThat(b.elseBranches()).singleElement().satisfies(e -> assertThat(e.fromStep()).isEqualTo(10));
        });
        assertThat(trace.steps().stream().filter(TraceStep::error)).extracting(TraceStep::index).containsExactly(8, 9);
        assertThat(trace.warnings()).isEmpty();
    }

    @Test
    void unresolvedCallIsShownAsUnresolvedAndWarned() {
        Trace trace = trace("java:endpoint:DELETE /api/v1/categories/{id}");

        TraceStep unresolved = trace.steps().stream()
                .filter(s -> s.confidence() == Confidence.UNRESOLVED && !s.returning())
                .findFirst()
                .orElseThrow();
        assertThat(unresolved.label()).isEqualTo("countByCategoryId(categoryId)");
        assertThat(unresolved.reason()).contains("2 lớp hiện thực");
        TraceStep back = trace.steps().get(unresolved.index());
        assertThat(back.returning()).isTrue();
        assertThat(back.label()).isEqualTo("(chưa xác định)");
        assertThat(trace.warnings()).anyMatch(w -> w.startsWith("1 lời gọi chưa xác định được đích (xem bước " + unresolved.index()));
        // Không đi sâu vào ứng viên: không có bước nào của ProductRepository
        assertThat(trace.participants()).noneMatch(p -> p.label().contains("Repository Product"));
    }

    @Test
    void notificationSendersAreUtilityClassesAndEntityChecksAreKept() {
        Trace trace = trace("java:endpoint:PUT /api/v1/orders/{id}/confirm");

        // EmailSender / SmsSender không thuộc Controller/Service/Repository và không phải entity → không vẽ
        assertThat(trace.steps()).extracting(TraceStep::label).noneMatch(l -> l.contains("send"));
        // Hàm của entity Order vẫn là bước tự xử lý, và điều kiện lỗi phụ thuộc nó nên vẫn có nhánh alt
        assertThat(trace.steps()).extracting(TraceStep::label).contains("isPending()", "ORDER_NOT_PENDING");
        // Hàm kế thừa từ thư viện không tìm thấy: bước "chưa xác định" kèm cảnh báo, không đoán
        TraceStep record = trace.steps().stream().filter(s -> s.label().equals("record(…)")).findFirst().orElseThrow();
        assertThat(record.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(trace.warnings()).anyMatch(w -> w.startsWith("1 lời gọi chưa xác định được đích (xem bước " + record.index()));
        assertThat(trace.steps()).noneMatch(TraceStep::pinned);
    }

    @Test
    void serviceMethodNamedLikeAGetterIsStillBusinessAndShowsItsTable() {
        Trace trace = trace("java:endpoint:GET /api/v1/products/options");

        assertThat(trace.steps()).extracting(TraceStep::label).contains("getOptions()", "search(keyword, withBrand)");
        assertThat(trace.participants()).extracting(p -> p.label()).contains("Bảng product", "Bảng product_brand");
    }

    @Test
    void sameInputGivesSameTraceAndDiagram() throws Exception {
        String key = "java:endpoint:POST /api/v1/categories";
        Trace a = trace(key);
        Trace b = new Tracer().trace(subgraph(analyze()), request(key));

        assertThat(json.writeValueAsString(b)).isEqualTo(json.writeValueAsString(a));
        assertThat(new PlantUmlExporter().export(b)).isEqualTo(new PlantUmlExporter().export(a));
        String svg = new SvgExporter().export(new SequenceViewModelBuilder().build(a));
        assertThat(svg).startsWith("<svg").contains("POST /api/v1/categories").endsWith("</svg>\n");
    }

    @Test
    void traceOfAnAverageApiIsFarBelowTheTwoSecondTarget() {
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            trace("java:endpoint:POST /api/v1/categories");
        }
        assertThat((System.nanoTime() - start) / 50 / 1_000_000).as("ms/trace").isLessThan(100);
    }

    // ---------------- tiện ích ----------------

    private Trace trace(String startKey) {
        return tracer.trace(subgraph(fragment), request(startKey));
    }

    private static TraceRequest request(String startKey) {
        return new TraceRequest(startKey, 0, "scan-1", SpringNoiseFilters.DEFAULTS, List.of());
    }

    private static Subgraph subgraph(IrFragment fragment) {
        return new Subgraph(fragment.nodes(), fragment.edges());
    }

    private void assertGolden(String file, String actual) throws IOException {
        Path path = GOLDEN.resolve(file);
        String normalized = actual.replace("\r\n", "\n");
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(path, normalized, StandardCharsets.UTF_8);
        }
        assertThat(normalized).as(file).isEqualTo(Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private IrFragment analyze() {
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
}
