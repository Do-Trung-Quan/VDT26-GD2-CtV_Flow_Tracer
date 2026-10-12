package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Quét Frontend Angular qua sidecar thật ({@code fixtures/angular-mini}) rồi đọc lại qua API: cây thành phần Frontend (C2),
 * tìm kiếm (C3) và dò vết luồng chỉ có giao diện (D1). Bỏ qua nếu chưa build sidecar ({@code npm --prefix analyzer-node run build}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "flowtracer.source-root=../..",
    "flowtracer.data-dir=target/test-data",
    "flowtracer.sidecar.cli-path=../../analyzer-node/dist/cli.js"
})
class AngularScanIT {

    private static final String FIXTURE = "fixtures/angular-mini";
    private static final Path SIDECAR_CLI = Path.of("..", "..", "analyzer-node", "dist", "cli.js");
    private static final String CREATE = "ng:element:admin-app/CategoryListComponent#id.btn-create";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private static String workspaceId;

    @BeforeAll
    static void requireSidecar() {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
        workspaceId = null;
    }

    @Test
    void scansAngularSourceAndServesTreeSearchAndUiOnlyTrace() throws Exception {
        String ws = scannedWorkspace();

        // --- C2: cây thành phần Frontend ---
        JsonNode tree = call("GET", "/api/workspaces/" + ws + "/trees/frontend", null).data();
        assertThat(tree.get("appCount").asInt()).isEqualTo(1);
        assertThat(tree.get("screenCount").asInt()).isEqualTo(3);
        JsonNode app = tree.get("apps").get(0);
        assertThat(app.get("name").asString()).isEqualTo("admin-app");
        assertThat(app.get("sourceName").asString()).isNotBlank();
        JsonNode categories = child(app, "Danh mục");
        assertThat(categories.get("routePath").asString()).isEqualTo("/categories");
        assertThat(names(categories.get("children"), "displayName"))
                .containsExactly("Tìm theo mã hoặc tên", "Thêm mới", "Xuất Excel", null, "Tải về", "Sửa", "Xóa", "Thêm danh mục");
        JsonNode refresh = categories.get("children").get(3);
        assertThat(refresh.get("fallbackName").asString()).as("nút chỉ có icon: tên kỹ thuật từ hàm xử lý").isEqualTo("onRefresh");
        assertThat(refresh.get("callsApi").asBoolean()).as("biến cục bộ → getRepository() → repository → HTTP").isTrue();
        assertThat(child(categories, "Thêm mới").get("fallbackName").isNull()).isTrue();
        assertThat(child(categories, "Thêm mới").get("leadsToApi").asBoolean()).as("mở hộp thoại có nút Lưu gọi API").isTrue();
        assertThat(child(categories, "Sửa").get("callsApi").asBoolean()).as("Sửa chỉ mở hộp thoại; tải lại sau khi đóng không tính").isFalse();
        assertThat(child(categories, "Sửa").get("leadsToApi").asBoolean()).isTrue();
        assertThat(child(categories, "Xóa").get("leadsToApi").asBoolean()).isFalse();
        assertThat(categories.get("leadsToApi").isNull()).isTrue();
        assertThat(categories.get("callsApi").asBoolean()).as("ngOnInit tải danh sách khi mở màn hình").isTrue();
        assertThat(child(app, "Đơn hàng").get("callsApi").asBoolean()).as("khởi tạo field gọi API khi mở").isTrue();
        assertThat(child(app, "Sản phẩm").get("callsApi").asBoolean()).as("màn hình chỉ có thao tác giao diện").isFalse();
        JsonNode create = child(categories, "Thêm mới");
        assertThat(create.get("nodeKey").asString()).isEqualTo(CREATE);
        assertThat(create.get("elementType").asString()).isEqualTo("button");
        assertThat(create.get("callsApi").asBoolean()).as("nút Thêm mới chỉ mở hộp thoại").isFalse();
        assertThat(child(categories, "Xóa").get("callsApi").asBoolean()).as("nút Xóa → facade → repository → HTTP").isTrue();
        JsonNode dialog = child(categories, "Thêm danh mục");
        assertThat(dialog.get("kind").asString()).isEqualTo("UI_DIALOG");
        JsonNode form = dialog.get("children").get(0);
        assertThat(form.get("kind").asString()).isEqualTo("UI_FORM");
        assertThat(names(form.get("children"), "displayName")).containsExactly("Mã danh mục", "Tên danh mục", "Lưu", "Hủy");
        assertThat(child(form, "Lưu").get("callsApi").asBoolean()).isTrue();
        JsonNode products = child(app, "Sản phẩm");
        assertThat(names(products.get("children"), "kind")).containsExactly("UI_TAB", "UI_TAB");

        // --- Quét lại không đổi gì: giữ nguyên IR ---
        assertThat(call("GET", "/api/workspaces/" + ws + "/trees/frontend", null).data()).isEqualTo(tree);

        // --- C3: tìm kiếm nhóm theo loại ---
        JsonNode search = call("GET", "/api/workspaces/" + ws + "/search?q=" + enc("thêm"), null).data();
        assertThat(names(search.get("groups"), "category")).contains("SCREEN", "ELEMENT");
        JsonNode elements = group(search, "ELEMENT");
        assertThat(names(elements.get("hits"), "displayName")).contains("Thêm mới");
        assertThat(search.get("total").asInt()).isGreaterThanOrEqualTo(2);
        JsonNode narrowed = call("GET", "/api/workspaces/" + ws + "/search?q=" + enc("thêm") + "&kinds=SCREEN", null).data();
        assertThat(names(narrowed.get("groups"), "category")).containsExactly("SCREEN");
        assertThat(call("GET", "/api/workspaces/" + ws + "/search?q=", null).status).isEqualTo(400);
        assertThat(call("GET", "/api/workspaces/" + ws + "/search?q=a&limit=0", null).status).isEqualTo(400);
        assertThat(call("GET", "/api/workspaces/00000000-0000-0000-0000-000000000000/search?q=a", null).status).isEqualTo(404);

        // --- D1: luồng chỉ có giao diện ---
        JsonNode ui = call("POST", "/api/workspaces/" + ws + "/traces", "{\"startKey\":\"" + CREATE + "\"}").data();
        assertThat(ui.get("uiOnly").asBoolean()).isTrue();
        assertThat(names(ui.get("participants"), "label")).containsExactly("User", "Frontend");
        assertThat(names(ui.get("steps"), "label")).containsExactly("Bấm \"Thêm mới\"", "hộp thoại Thêm danh mục");
        assertThat(ui.get("steps").get(0).get("edgeRef").asString()).matches("[0-9a-f-]{36}");
        JsonNode filter = call("POST", "/api/workspaces/" + ws + "/traces",
                "{\"startKey\":\"ng:element:admin-app/ProductListComponent#button.click.toggleFilter\"}").data();
        assertThat(filter.get("steps").get(1).get("label").asString()).isEqualTo("giao diện đã cập nhật");

        // --- nút gọi API chưa dò được (cần nối FE–BE ở giai đoạn sau) ---
        var remove = call("POST", "/api/workspaces/" + ws + "/traces",
                "{\"startKey\":\"ng:element:admin-app/CategoryListComponent#button.click.onDelete\"}");
        assertThat(remove.status).isEqualTo(422);
        assertThat(remove.body.get("code").asString()).isEqualTo("TRACE_START_UNSUPPORTED");
    }

    // ---------------- tiện ích ----------------

    private synchronized String scannedWorkspace() throws Exception {
        if (workspaceId != null) {
            return workspaceId;
        }
        Res created = call("POST", "/api/workspaces", "{\"name\":\"Angular IT\",\"sources\":[{\"rootPath\":\"" + FIXTURE + "\"}]}");
        assertThat(created.status).isEqualTo(201);
        assertThat(created.data().get("sources").get(0).get("role").asString()).isEqualTo("FRONTEND");
        String id = created.data().get("id").asString();
        String runId = call("POST", "/api/workspaces/" + id + "/scans", "{\"trigger\":\"IMPORT\"}").data().get("id").asString();
        for (int i = 0; i < 600; i++) {
            JsonNode run = call("GET", "/api/scans/" + runId, null).data();
            String status = run.get("status").asString();
            if (List.of("SUCCEEDED", "PARTIAL").contains(status)) {
                assertThat(status).as(run.toString()).isEqualTo("SUCCEEDED");
                workspaceId = id;
                return id;
            }
            assertThat(status).as(run.toString()).isIn("PENDING", "RUNNING");
            Thread.sleep(100);
        }
        throw new AssertionError("Lần quét không kết thúc trong 60 giây");
    }

    private static JsonNode child(JsonNode parent, String displayName) {
        for (JsonNode c : parent.get("children")) {
            if (c.hasNonNull("displayName") && displayName.equals(c.get("displayName").asString())) {
                return c;
            }
        }
        throw new AssertionError("Không có '" + displayName + "' trong " + names(parent.get("children"), "displayName"));
    }

    private static JsonNode group(JsonNode search, String category) {
        for (JsonNode g : search.get("groups")) {
            if (category.equals(g.get("category").asString())) {
                return g;
            }
        }
        throw new AssertionError("Không có nhóm " + category);
    }

    private static List<String> names(JsonNode array, String field) {
        var out = new ArrayList<String>();
        array.forEach(n -> out.add(n.get(field).isNull() ? null : n.get(field).asString()));
        return out;
    }

    private record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.get("data");
        }
    }

    private Res call(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Res(res.statusCode(), res.body().isEmpty() ? null : json.readTree(res.body()));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
