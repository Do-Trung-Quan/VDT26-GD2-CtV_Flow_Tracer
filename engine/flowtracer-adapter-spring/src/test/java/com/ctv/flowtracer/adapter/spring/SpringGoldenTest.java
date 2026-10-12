package com.ctv.flowtracer.adapter.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test: phân tích {@code fixtures/spring-mini} rồi so với {@code fixtures/golden/spring-mini.ir.json}. Sinh lại
 * snapshot bằng {@code -Pgolden-update} (chỉ khi thay đổi IR có chủ đích).
 */
class SpringGoldenTest {

    private static final Path GOLDEN = Path.of("..", "..", "fixtures", "golden", "spring-mini.ir.json");

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

        assertThat(fragment.nodes().stream().filter(n -> n.kind() == NodeKind.API_ENDPOINT))
                .extracting(IrNode::name)
                .containsExactly(
                "ANY /shop-api/api/v1/orders/sync",
                "DELETE /shop-api/api/v1/categories/{id}",
                "GET /shop-api/api/v1/categories",
                "GET /shop-api/api/v1/categories/export",
                "GET /shop-api/api/v1/categories/{id}",
                "GET /shop-api/api/v1/orders",
                "GET /shop-api/api/v1/orders/${ExternalPaths.REPORT}",
                "GET /shop-api/api/v1/orders/${shop.order.api-token}",
                "GET /shop-api/api/v1/orders/${shop.order.env-path}",
                "GET /shop-api/api/v1/orders/${shop.order.missing-path}",
                "GET /shop-api/api/v1/orders/${shop.order.profile-path}",
                "GET /shop-api/api/v1/orders/archive",
                "GET /shop-api/api/v1/orders/exports",
                "GET /shop-api/api/v1/orders/tokens",
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
                .containsEntry("pathResolved", false)
                .containsEntry("pathIssue", "CONSTANT");
        // Thuộc tính cấu hình: cấu hình thắng mặc định trong code; biến môi trường có mặc định; còn lại KHÔNG đoán
        IrNode exports = node(fragment, "GET /shop-api/api/v1/orders/exports");
        assertThat(exports.attrs()).containsEntry("pathResolved", true);
        assertThat(exports.attrs().get("pathSources"))
                .isEqualTo(List.of("shop.order.export-path = /exports (src/main/resources/application.yml)"));
        assertThat(node(fragment, "GET /shop-api/api/v1/orders/archive").attrs())
                .containsEntry("pathResolved", true);
        assertUnresolved(fragment, "env-path", "biến môi trường");
        assertUnresolved(fragment, "missing-path", "không có trong file cấu hình mặc định");
        assertUnresolved(fragment, "profile-path", "file theo profile");
        assertUnresolved(fragment, "api-token", "nhạy cảm");
        assertThat(node(fragment, "GET /shop-api/api/v1/orders/tokens").attrs()).containsEntry("pathResolved", true);
        assertThat(json.writeValueAsString(fragment)).doesNotContain("khong-duoc-doc").doesNotContain("dev-only");

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

    private IrFragment analyze() {
        return new FixtureRunner("spring-mini").analyze();
    }

    private static void assertUnresolved(IrFragment fragment, String key, String reasonPart) {
        IrNode node = node(fragment, "GET /shop-api/api/v1/orders/${shop.order." + key + "}");
        assertThat(node.attrs()).containsEntry("pathResolved", false).containsEntry("pathIssue", "PROPERTY");
        assertThat((String) node.attrs().get("pathIssueReason")).contains(reasonPart);
    }

    private static IrNode node(IrFragment fragment, String name) {
        return fragment.nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
    }
}
