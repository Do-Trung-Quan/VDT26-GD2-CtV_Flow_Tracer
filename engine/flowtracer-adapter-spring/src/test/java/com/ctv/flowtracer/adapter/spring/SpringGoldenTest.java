package com.ctv.flowtracer.adapter.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
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
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test: phân tích {@code fixtures/spring-mini} rồi so với {@code fixtures/golden/spring-mini.ir.json}. Sinh lại
 * snapshot bằng {@code -Pgolden-update} (chỉ khi thay đổi IR có chủ đích).
 */
class SpringGoldenTest {

    private static final Path FIXTURE = Path.of("..", "..", "fixtures", "spring-mini");
    private static final Path GOLDEN = Path.of("..", "..", "fixtures", "golden", "spring-mini.ir.json");

    private final SpringStackAdapter adapter = new SpringStackAdapter();
    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void matchesGoldenSnapshot() throws Exception {
        String actual = json.writeValueAsString(analyze()).replace("\r\n", "\n") + "\n";
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
        }
        assertThat(actual).isEqualTo(Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    void endpointsModulesAndDiagnostics() throws Exception {
        IrFragment fragment = analyze();

        assertThat(fragment.nodes()).extracting(IrNode::name).containsExactly(
                "ANY /shop-api/api/v1/orders/sync",
                "DELETE /shop-api/api/v1/categories/{id}",
                "GET /shop-api/api/v1/categories",
                "GET /shop-api/api/v1/categories/export",
                "GET /shop-api/api/v1/categories/{id}",
                "GET /shop-api/api/v1/orders",
                "GET /shop-api/api/v1/orders/${ExternalPaths.REPORT}",
                "POST /shop-api/api/v1/categories",
                "POST /shop-api/api/v1/categories/export",
                "PUT /shop-api/api/v1/orders/{id}/status");

        IrNode create = node(fragment, "POST /shop-api/api/v1/categories");
        assertThat(create.displayName()).isEqualTo("Tạo danh mục");
        assertThat(create.attrs())
                .containsEntry("module", "Danh mục")
                .containsEntry("moduleSource", "TAG")
                .containsEntry("requestType", "CategoryRequest")
                .containsEntry("responseType", "ApiResponse<CategoryResponse>")
                .containsEntry("summarySource", "OPERATION");

        IrNode get = node(fragment, "GET /shop-api/api/v1/categories/{id}");
        assertThat(get.displayName()).isEqualTo("Lấy chi tiết một danh mục.");
        assertThat(get.attrs()).containsEntry("summarySource", "JAVADOC");

        IrNode orders = node(fragment, "GET /shop-api/api/v1/orders");
        assertThat(orders.displayName()).isNull();
        assertThat(orders.attrs()).containsEntry("module", "Order").containsEntry("moduleSource", "CONTROLLER");

        assertThat(node(fragment, "GET /shop-api/api/v1/orders/${ExternalPaths.REPORT}").attrs())
                .containsEntry("pathResolved", false);

        assertThat(fragment.diagnostics()).extracting(Diagnostic::severity, Diagnostic::filePath)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                Diagnostic.Severity.ERROR, "src/main/java/com/example/shop/product/ProductController.java"),
                        org.assertj.core.groups.Tuple.tuple(
                                Diagnostic.Severity.WARNING, "src/main/java/com/example/shop/order/OrderController.java"));
        assertThat(fragment.diagnostics()).allMatch(d -> !d.message().contains("khong-duoc-doc"));
    }

    @Test
    void sameInputGivesSameOutput() throws Exception {
        assertThat(json.writeValueAsString(analyze())).isEqualTo(json.writeValueAsString(analyze()));
    }

    // ---------------- chạy adapter trên fixture ----------------

    private IrFragment analyze() throws IOException {
        List<String> allPaths;
        try (Stream<Path> files = Files.walk(FIXTURE)) {
            allPaths = files.filter(Files::isRegularFile)
                    .map(p -> FIXTURE.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        var extractCtx = new ExtractContext("spring-mini", allPaths, this::load, null);
        List<FileFacts> facts = allPaths.stream()
                .filter(adapter::analyzes)
                .map(p -> adapter.extract(extractCtx, sourceFile(p)))
                .toList();
        assertThat(facts).allMatch(f -> f instanceof JavaFileFacts);
        return adapter.resolve(new ResolveContext("spring-mini", allPaths, this::load, null, null), facts);
    }

    private Optional<String> load(String relativePath) {
        try {
            return Optional.of(Files.readString(FIXTURE.resolve(relativePath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private SourceFile sourceFile(String relativePath) {
        String content = load(relativePath).orElseThrow();
        return new SourceFile(relativePath, content, sha256(content));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static IrNode node(IrFragment fragment, String name) {
        return fragment.nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
    }
}
