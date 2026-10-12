package com.ctv.flowtracer.app.graph.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.graph.dto.response.ApiModuleResponse;
import com.ctv.flowtracer.app.graph.dto.response.EndpointNodeResponse;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TreeBuilderTest {

    private static final UUID API = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private static IrNodeRow endpoint(UUID source, String method, String path, String module, Map<String, Object> extra) {
        var attrs = new TreeMap<String, Object>(extra);
        attrs.put("httpMethod", method);
        attrs.put("path", path);
        attrs.put("module", module);
        attrs.put("moduleSource", "TAG");
        var node = new IrNode(NodeKeys.javaEndpoint(method, path), NodeKind.API_ENDPOINT, method + " " + path,
                (String) extra.get("summary"), SourceLocation.of("A.java", 10), attrs);
        return new IrNodeRow(UUID.randomUUID(), UUID.randomUUID(), source, null, node);
    }

    @Test
    void groupsByModuleAndSourceAndSortsEndpoints() {
        var tree = TreeBuilder.backend(List.of(
                endpoint(API, "POST", "/categories", "Danh mục", Map.of("summary", "Tạo", "summarySource", "OPERATION")),
                endpoint(API, "GET", "/categories", "Danh mục", Map.of()),
                endpoint(API, "DELETE", "/categories/{id}", "Danh mục", Map.of("deprecated", true)),
                endpoint(ADMIN, "GET", "/categories", "Danh mục", Map.of()),
                endpoint(API, "GET", "/orders/${X}", "Đơn hàng", Map.of("pathResolved", false, "pathIssue", "CONSTANT", "pathIssueReason", "không tìm thấy giá trị của hằng số X")),
                endpoint(API, "GET", "/orders/${shop.p}", "Đơn hàng", Map.of("pathResolved", false, "pathIssue", "PROPERTY", "pathIssueReason", "khóa shop.p lấy từ biến môi trường lúc chạy"))),
                Map.of(API, "shop-api", ADMIN, "shop-admin"));

        assertThat(tree.moduleCount()).isEqualTo(3);
        assertThat(tree.endpointCount()).isEqualTo(6);
        ApiModuleResponse category = tree.modules().stream()
                .filter(m -> m.name().equals("Danh mục") && m.sourceId().equals(API))
                .findFirst()
                .orElseThrow();
        assertThat(category.sourceName()).isEqualTo("shop-api");
        assertThat(category.endpoints()).extracting(e -> e.httpMethod() + " " + e.path())
                .containsExactly("GET /categories", "POST /categories", "DELETE /categories/{id}");
        assertThat(category.endpoints().get(1).summarySource()).isEqualTo("OPERATION");
        assertThat(category.endpoints().get(2).deprecated()).isTrue();

        List<EndpointNodeResponse> orders = tree.modules().stream()
                .filter(m -> m.name().equals("Đơn hàng"))
                .findFirst()
                .orElseThrow()
                .endpoints();
        assertThat(orders.get(0).warnings()).containsExactly("Đường dẫn chưa giải được: không tìm thấy giá trị của hằng số X");
        assertThat(orders.get(1).warnings())
                .containsExactly("Đường dẫn chưa giải được: khóa shop.p lấy từ biến môi trường lúc chạy");
    }

    @Test
    void emptyWhenNothingScanned() {
        var tree = TreeBuilder.backend(List.of(), Map.of());
        assertThat(tree.moduleCount()).isZero();
        assertThat(tree.modules()).isEmpty();
    }
}
