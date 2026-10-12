package com.ctv.flowtracer.app.graph.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.graph.dto.response.ApiModuleResponse;
import com.ctv.flowtracer.app.graph.dto.response.EndpointNodeResponse;
import com.ctv.flowtracer.app.graph.dto.response.UiTreeNodeResponse;
import com.ctv.flowtracer.app.graph.model.ContainsRow;
import com.ctv.flowtracer.app.graph.model.UiLinkRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.List;
import java.util.Set;
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

    private static IrNodeRow ui(UUID source, String key, NodeKind kind, String name, String displayName, Map<String, Object> attrs) {
        var node = new IrNode(key, kind, name, displayName, SourceLocation.of("a.html", 3), attrs);
        return new IrNodeRow(UUID.randomUUID(), UUID.randomUUID(), source, null, node);
    }

    @Test
    void frontendTreeFollowsContainsOrderAndFlagsElementsThatCallApi() {
        var web = UUID.randomUUID();
        var nodes = List.of(
                ui(web, "ng:app:admin", NodeKind.UI_APP, "admin", null, Map.of()),
                ui(web, "ng:screen:admin/categories", NodeKind.UI_SCREEN, "CategoryList", "Danh mục", Map.of("routePath", "/categories")),
                ui(web, "ng:dialog:admin/Form", NodeKind.UI_DIALOG, "Form", "Thêm danh mục", Map.of()),
                ui(web, "ng:element:admin/List#create", NodeKind.UI_ELEMENT, "openCreate", "Thêm mới", Map.of("elementType", "button")),
                ui(web, "ng:element:admin/List#delete", NodeKind.UI_ELEMENT, "onDelete", "Xóa", Map.of("elementType", "button")),
                ui(web, "ng:element:admin/Form#save", NodeKind.UI_ELEMENT, "save", "Lưu", Map.of("elementType", "button")));
        var contains = List.of(
                new ContainsRow("ng:app:admin", "ng:screen:admin/categories", 0),
                new ContainsRow("ng:screen:admin/categories", "ng:element:admin/List#create", 0),
                new ContainsRow("ng:screen:admin/categories", "ng:element:admin/List#delete", 1),
                new ContainsRow("ng:screen:admin/categories", "ng:dialog:admin/Form", 2),
                new ContainsRow("ng:dialog:admin/Form", "ng:element:admin/Form#save", 0),
                new ContainsRow("ng:dialog:admin/Form", "ng:dialog:admin/Form", 1)); // vòng tự chứa: bị cắt

        var tree = TreeBuilder.frontend(nodes, contains, Set.of("ng:element:admin/List#delete", "ng:element:admin/Form#save", "ng:screen:admin/categories"), List.of(), Map.of(web, "shop-web"));

        assertThat(tree.appCount()).isEqualTo(1);
        assertThat(tree.screenCount()).isEqualTo(1);
        assertThat(tree.elementCount()).isEqualTo(3);
        var app = tree.apps().getFirst();
        assertThat(app.sourceName()).isEqualTo("shop-web");
        var screen = app.children().getFirst();
        assertThat(screen).extracting(UiTreeNodeResponse::displayName, UiTreeNodeResponse::routePath)
                .containsExactly("Danh mục", "/categories");
        assertThat(screen.children()).extracting(UiTreeNodeResponse::displayName)
                .containsExactly("Thêm mới", "Xóa", "Thêm danh mục");
        assertThat(screen.children().get(0).callsApi()).isFalse();
        assertThat(screen.children().get(1).callsApi()).isTrue();
        assertThat(screen.children().get(0).elementType()).isEqualTo("button");
        assertThat(screen.callsApi()).as("màn hình tải dữ liệu khi mở").isTrue();
        assertThat(screen.children().get(2).callsApi()).as("hộp thoại không gọi API khi mở").isFalse();
        assertThat(app.callsApi()).isFalse();
        var dialog = screen.children().get(2);
        assertThat(dialog.children()).extracting(UiTreeNodeResponse::displayName).containsExactly("Lưu"); // không lặp vô hạn
    }

    @Test
    void elementWithoutLabelGetsATechnicalNameFromTheCode() {
        var web = UUID.randomUUID();
        var nodes = List.of(
                ui(web, "ng:app:a", NodeKind.UI_APP, "a", null, Map.of()),
                ui(web, "ng:element:a/X#1", NodeKind.UI_ELEMENT, "onSave", null, Map.of("elementType", "button", "handlers", List.of("onSave", "close"), "tag", "button")),
                ui(web, "ng:element:a/X#2", NodeKind.UI_ELEMENT, "input", null, Map.of("elementType", "input", "control", "password", "handlers", List.of("onChange"), "tag", "shared-input")),
                ui(web, "ng:element:a/X#3", NodeKind.UI_ELEMENT, "shared-input", null, Map.of("elementType", "input", "i18nKey", "col_name", "tag", "shared-input")),
                ui(web, "ng:element:a/X#4", NodeKind.UI_ELEMENT, "button", null, Map.of("elementType", "button", "tag", "button")),
                ui(web, "ng:element:a/X#5", NodeKind.UI_ELEMENT, "onDelete", "Xóa", Map.of("elementType", "button", "handlers", List.of("onDelete"))));
        var contains = new java.util.ArrayList<ContainsRow>();
        for (int i = 1; i <= 5; i++) {
            contains.add(new ContainsRow("ng:app:a", "ng:element:a/X#" + i, i));
        }

        var kids = TreeBuilder.frontend(nodes, contains, Set.of(), List.of(), Map.of(web, "w")).apps().getFirst().children();

        assertThat(kids).extracting(UiTreeNodeResponse::fallbackName).containsExactly("onSave", "password", "col_name", "button", null);
        assertThat(kids.get(4).displayName()).isEqualTo("Xóa");
    }

    @Test
    void elementThatOpensADialogOrOpensAScreenWithApiLeadsToApi() {
        var web = UUID.randomUUID();
        var nodes = List.of(
                ui(web, "ng:app:a", NodeKind.UI_APP, "a", null, Map.of()),
                ui(web, "ng:screen:a/list", NodeKind.UI_SCREEN, "List", null, Map.of("routePath", "/list")),
                ui(web, "ng:screen:a/static", NodeKind.UI_SCREEN, "Static", null, Map.of("routePath", "/static")),
                ui(web, "ng:screen:a/other", NodeKind.UI_SCREEN, "Other", null, Map.of("routePath", "/other")),
                ui(web, "ng:element:a/L#add", NodeKind.UI_ELEMENT, "add", "Thêm mới", Map.of("elementType", "button")),
                ui(web, "ng:element:a/L#info", NodeKind.UI_ELEMENT, "info", "Thông tin", Map.of("elementType", "button")),
                ui(web, "ng:element:a/L#toStatic", NodeKind.UI_ELEMENT, "toStatic", "Trang tĩnh", Map.of("elementType", "link")),
                ui(web, "ng:element:a/L#toOther", NodeKind.UI_ELEMENT, "toOther", "Trang khác", Map.of("elementType", "link")),
                ui(web, "ng:element:a/L#plain", NodeKind.UI_ELEMENT, "plain", "Đóng", Map.of("elementType", "button")),
                ui(web, "ng:dialog:a/Form", NodeKind.UI_DIALOG, "Form", "Thêm", Map.of()),
                ui(web, "ng:dialog:a/Info", NodeKind.UI_DIALOG, "Info", "Hộp thông tin", Map.of()),
                ui(web, "ng:element:a/Form#save", NodeKind.UI_ELEMENT, "save", "Lưu", Map.of("elementType", "button")),
                ui(web, "ng:element:a/Info#close", NodeKind.UI_ELEMENT, "close", "Đóng", Map.of("elementType", "button")),
                ui(web, "ng:element:a/O#load", NodeKind.UI_ELEMENT, "load", "Tải", Map.of("elementType", "button")));
        var contains = List.of(
                new ContainsRow("ng:app:a", "ng:screen:a/list", 0),
                new ContainsRow("ng:app:a", "ng:screen:a/static", 1),
                new ContainsRow("ng:app:a", "ng:screen:a/other", 2),
                new ContainsRow("ng:screen:a/list", "ng:element:a/L#add", 0),
                new ContainsRow("ng:screen:a/list", "ng:element:a/L#info", 1),
                new ContainsRow("ng:screen:a/list", "ng:element:a/L#toStatic", 2),
                new ContainsRow("ng:screen:a/list", "ng:element:a/L#toOther", 3),
                new ContainsRow("ng:screen:a/list", "ng:element:a/L#plain", 4),
                new ContainsRow("ng:screen:a/list", "ng:dialog:a/Form", 5),
                new ContainsRow("ng:screen:a/list", "ng:dialog:a/Info", 6),
                new ContainsRow("ng:dialog:a/Form", "ng:element:a/Form#save", 0),
                new ContainsRow("ng:dialog:a/Info", "ng:element:a/Info#close", 0),
                new ContainsRow("ng:screen:a/other", "ng:element:a/O#load", 0));
        var links = List.of(
                new UiLinkRow("ng:element:a/L#add", "ng:dialog:a/Form"), // hộp thoại có nút Lưu gọi API
                new UiLinkRow("ng:element:a/L#info", "ng:dialog:a/Info"), // hộp thoại chỉ có nút Đóng
                new UiLinkRow("ng:element:a/L#toStatic", "ng:screen:a/static"), // màn hình không có API
                new UiLinkRow("ng:element:a/L#toOther", "ng:screen:a/other")); // màn hình có phần tử gọi API
        var callingApi = Set.of("ng:element:a/Form#save", "ng:element:a/O#load");

        var list = TreeBuilder.frontend(nodes, contains, callingApi, links, Map.of(web, "w")).apps().getFirst().children().getFirst();

        var byLabel = new java.util.HashMap<String, UiTreeNodeResponse>();
        list.children().forEach(c -> byLabel.put(c.displayName(), c));
        assertThat(byLabel.get("Thêm mới").leadsToApi()).as("mở hộp thoại có API").isTrue();
        assertThat(byLabel.get("Thông tin").leadsToApi()).as("mở hộp thoại chỉ có nút Đóng").isFalse();
        assertThat(byLabel.get("Trang tĩnh").leadsToApi()).as("tới màn hình không có API").isFalse();
        assertThat(byLabel.get("Trang khác").leadsToApi()).as("tới màn hình có API").isTrue();
        assertThat(byLabel.get("Đóng").leadsToApi()).as("không dẫn tới đâu").isFalse();
        assertThat(list.leadsToApi()).as("chỉ phần tử có cờ này").isNull();
    }

    @Test
    void sameDialogUnderTwoScreensAppearsUnderBoth() {
        var web = UUID.randomUUID();
        var nodes = List.of(
                ui(web, "ng:app:a", NodeKind.UI_APP, "a", null, Map.of()),
                ui(web, "ng:screen:a/x", NodeKind.UI_SCREEN, "X", null, Map.of("routePath", "/x")),
                ui(web, "ng:screen:a/y", NodeKind.UI_SCREEN, "Y", null, Map.of("routePath", "/y")),
                ui(web, "ng:dialog:a/D", NodeKind.UI_DIALOG, "D", null, Map.of()));
        var contains = List.of(
                new ContainsRow("ng:app:a", "ng:screen:a/x", 0),
                new ContainsRow("ng:app:a", "ng:screen:a/y", 1),
                new ContainsRow("ng:screen:a/x", "ng:dialog:a/D", 0),
                new ContainsRow("ng:screen:a/y", "ng:dialog:a/D", 0));

        var tree = TreeBuilder.frontend(nodes, contains, Set.of(), List.of(), Map.of(web, "w"));

        assertThat(tree.apps().getFirst().children()).allSatisfy(s -> assertThat(s.children()).hasSize(1));
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
