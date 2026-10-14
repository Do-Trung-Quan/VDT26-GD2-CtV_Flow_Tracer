package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ctv.flowtracer.app.common.web.NodeKeyCodec;
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
 * Giai đoạn 6 trên Workspace hai nguồn (Frontend {@code angular-mini} + Backend {@code spring-flow}): tab Diễn giải (F1), dữ liệu
 * mẫu (F2), kịch bản (F3), chi tiết thành phần (C4), đoạn mã (C5), đối chiếu OpenAPI (C6) — tất cả khi AI tắt. Bỏ qua nếu chưa build
 * sidecar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "flowtracer.source-root=../..",
    "flowtracer.data-dir=target/test-data",
    "flowtracer.sidecar.cli-path=../../analyzer-node/dist/cli.js"
})
class ExplanationApiIT {

    private static final Path SIDECAR_CLI = Path.of("..", "..", "analyzer-node", "dist", "cli.js");
    private static final String CREATE_API = "java:endpoint:POST /api/v1/categories";
    private static final String SAVE = "ng:element:admin-app/CategoryFormDialogComponent#app-button.submit";
    private static final String OPEN_DIALOG = "ng:element:admin-app/CategoryListComponent#id.btn-create";
    private static final String SCREEN = "ng:screen:admin-app/categories";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void requireSidecar() {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
    }

    @Test
    void explainsApisElementsAndContainersWithoutAi() throws Exception {
        String ws = scannedWorkspace();

        // ---- F1: API có @Operation, @PreAuthorize, Bean Validation
        JsonNode api = get(ws, CREATE_API, "explanation").data();
        assertThat(api.get("container").asBoolean()).isFalse();
        assertThat(api.get("purpose").get("source").asString()).isEqualTo("OPERATION");
        assertThat(api.get("purpose").get("text").asString()).isEqualTo("Thêm mới danh mục");
        JsonNode io = api.get("apis").get(0);
        assertThat(io.get("path").asString()).isEqualTo("/api/v1/categories");
        assertThat(io.get("input").get("body").get("code").asString()).startsWith("string — bắt buộc, tối đa 50 ký tự");
        assertThat(io.get("output").get("data").has("title")).isTrue();
        assertThat(texts(io.get("errors"))).contains("mã danh mục đã tồn tại", "không đủ quyền");
        assertThat(texts(io.get("constraints"))).contains("Cần quyền: hasAuthority('CATEGORY_MANAGE')", "Cột code của bảng category không được trùng");
        // Được gọi từ: các nút giao diện; Gọi tới: bảng dữ liệu
        assertThat(texts(api.get("calledFrom"), "displayName")).contains("Lưu");
        // giao diện đặt nhãn như trên cây Frontend: loại phần tử, và màn hình / hộp thoại chứa nó kèm loại
        assertThat(texts(api.get("calledFrom"), "elementType")).contains("button");
        assertThat(texts(api.get("calledFrom"), "context")).anyMatch(c -> c != null && (c.startsWith("Hộp thoại ") || c.startsWith("Màn hình ")));
        assertThat(api.get("calledFromTotal").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(texts(api.get("callsTo"), "name")).contains("category");

        // ---- F1: nút Lưu lấy đầu vào / đầu ra theo API nó gọi; "Được gọi từ" là chuỗi thành phần chứa
        JsonNode save = get(ws, SAVE, "explanation").data();
        assertThat(save.get("apis").get(0).get("apiKey").asString()).isEqualTo(CREATE_API);
        assertThat(save.get("purpose").get("source").asString()).isEqualTo("SKELETON");
        assertThat(save.get("purpose").get("text").asString()).startsWith("Nút “Lưu” dùng để gọi các API:").contains("Thêm mới danh mục").contains("Tìm kiếm danh mục");
        assertThat(save.get("apis")).as("sau khi tạo, facade tải lại danh sách nên nút gọi 2 API").hasSize(2);
        assertThat(texts(save.get("trail"), "kind")).containsExactly("UI_APP", "UI_SCREEN", "UI_DIALOG", "UI_FORM");

        // ---- F1: nút chỉ mở hộp thoại — không có đầu vào / đầu ra
        JsonNode open = get(ws, OPEN_DIALOG, "explanation").data();
        assertThat(open.get("apis")).isEmpty();
        assertThat(open.get("purpose").get("text").asString()).contains("chỉ thay đổi giao diện: mở hộp thoại").contains("không gọi máy chủ");

        // ---- F1: gọi API nhưng chưa xác định được đích — không được nói "không gọi máy chủ"
        JsonNode export = get(ws, "ng:element:admin-app/CategoryListComponent#button.click.onExport", "explanation").data();
        assertThat(export.get("apis")).isEmpty();
        assertThat(texts(export.get("unresolvedCalls"))).singleElement().asString().startsWith("GET ");
        assertThat(export.get("purpose").get("text").asString()).contains("gọi máy chủ nhưng").doesNotContain("không gọi máy chủ");

        // ---- F1: thành phần chứa chỉ có mô tả ngắn
        JsonNode screen = get(ws, SCREEN, "explanation").data();
        assertThat(screen.get("container").asBoolean()).isTrue();
        assertThat(screen.get("apis")).isEmpty();
        assertThat(screen.get("purpose").get("text").asString()).startsWith("Màn hình Danh mục gồm").contains("phần tử tương tác").contains("hộp thoại");

        // ---- F1: loại không diễn giải được
        assertThat(call("GET", "/api/workspaces/" + ws + "/nodes/" + enc("java:table:category") + "/explanation", null).status).as("bảng không có tab Diễn giải").isEqualTo(422);
        assertThat(call("GET", "/api/workspaces/" + ws + "/nodes/" + enc("ng:nope:x") + "/explanation", null).status).isEqualTo(404);
    }

    @Test
    void mockDataFollowsTheDtoStructureAndIsStable() throws Exception {
        String ws = scannedWorkspace();

        JsonNode mock = get(ws, CREATE_API, "mock-data").data();
        assertThat(mock.get("source").asString()).isEqualTo("RULE");
        assertThat(mock.get("problems")).isEmpty();
        assertThat(mock.get("request").get("code").asString()).isEqualTo("MA001");
        assertThat(mock.get("request").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(mock.get("response").get("data").get("title").asString()).isEqualTo("Tên mẫu");
        assertThat(get(ws, CREATE_API, "mock-data").data()).isEqualTo(mock);
        // phần tử giao diện: theo API nó gọi; chọn API bằng apiKey
        assertThat(get(ws, SAVE, "mock-data").data().get("apiKey").asString()).isEqualTo(CREATE_API);
        assertThat(call("GET", "/api/workspaces/" + ws + "/nodes/" + enc(SAVE) + "/mock-data?apiKey=" + URLEncoder.encode("java:endpoint:GET /x", StandardCharsets.UTF_8), null).status).isEqualTo(422);
        assertThat(call("GET", "/api/workspaces/" + ws + "/nodes/" + enc(OPEN_DIALOG) + "/mock-data", null).status).isEqualTo(422);
    }

    @Test
    void scenarioMatchesEveryArrowOfTheTrace() throws Exception {
        String ws = scannedWorkspace();

        JsonNode trace = call("POST", "/api/workspaces/" + ws + "/traces", "{\"startKey\":\"" + SAVE + "\"}").data();
        JsonNode scenario = call("GET", "/api/workspaces/" + ws + "/traces/" + trace.get("traceId").asString() + "/scenario", null).data();

        assertThat(scenario.get("source").asString()).isEqualTo("FRAMEWORK");
        assertThat(scenario.get("title").asString()).isEqualTo("Lưu");
        assertThat(texts(scenario.get("actors"), "name")).containsExactlyElementsOf(texts(trace.get("participants"), "label"));
        List<Integer> indexes = new ArrayList<>();
        scenario.get("items").forEach(i -> {
            if (i.hasNonNull("index")) {
                indexes.add(i.get("index").asInt());
            }
        });
        List<Integer> arrows = new ArrayList<>();
        trace.get("steps").forEach(s -> arrows.add(s.get("index").asInt()));
        assertThat(indexes).as("bước n ↔ mũi tên n").containsExactlyElementsOf(arrows);
        assertThat(scenario.get("items").get(1).get("text").asString()).startsWith("Frontend gửi yêu cầu POST /api/v1/categories đến Controller Danh mục");
        assertThat(texts(scenario.get("items"), "text")).contains("Nếu categoryRepository.existsByCode(code):", "Ngược lại:");
        assertThat(call("GET", "/api/workspaces/" + ws + "/traces/khong-co/scenario", null).status).isEqualTo(404);
    }

    @Test
    void nodeDetailAndSourceSnippets() throws Exception {
        String ws = scannedWorkspace();

        JsonNode detail = get(ws, SAVE, "").data();
        assertThat(detail.get("kind").asString()).isEqualTo("UI_ELEMENT");
        assertThat(texts(detail.get("callsTo"), "name")).contains("POST /api/v1/categories");
        assertThat(detail.get("sourceName").asString()).isNotBlank();
        assertThat(get(ws, CREATE_API, "").data().get("callsTo").toString()).contains("category");

        // ---- C5: popup Xem code chia tab theo lớp
        JsonNode tabs = get(ws, SAVE, "source?context_lines=2").data();
        List<String> titles = texts(tabs, "title");
        // mỗi hàm / khối một đoạn; một file có nhiều đoạn thì tiêu đề thêm " · tên hàm"
        for (String layer : List.of("Template", "Controller", "Service", "Repository")) {
            assertThat(titles).as("tab " + layer).anyMatch(t -> t.equals(layer) || t.startsWith(layer + " · "));
        }
        JsonNode service = tab(tabs, "Service");
        assertThat(service.get("code").asString()).contains("existsByCode");
        assertThat(service.get("filePath").asString()).endsWith("CategoryServiceImpl.java");
        assertThat(service.get("highlightLines")).isNotEmpty();
        int start = service.get("startLine").asInt();
        assertThat(service.get("highlightLines").get(0).asInt()).isGreaterThanOrEqualTo(start);
        assertThat(call("GET", "/api/workspaces/" + ws + "/nodes/" + enc(SAVE) + "/source?context_lines=99", null).status).isEqualTo(400);
    }

    @Test
    void openApiCheckComparesCodeWithSpec() throws Exception {
        String ws = scannedWorkspace();

        // file openapi.yaml trong fixture: khớp 3 API danh mục, thừa 1 API trong đặc tả, thiếu các API sản phẩm / đơn hàng
        JsonNode report = call("POST", "/api/workspaces/" + ws + "/openapi-check", "{}").data();
        assertThat(report.get("source").asString()).endsWith("openapi.yaml");
        assertThat(report.get("specVersion").asString()).isEqualTo("OpenAPI 3.0.3");
        assertThat(report.get("codeTotal").asInt()).isEqualTo(6);
        assertThat(report.get("specTotal").asInt()).isEqualTo(4);
        assertThat(texts(report.get("matched"), "path")).containsExactly("/api/v1/categories", "/api/v1/categories", "/api/v1/categories/{id}");
        assertThat(texts(report.get("onlyInSpec"), "path")).containsExactly("/api/v1/categories/export");
        assertThat(texts(report.get("onlyInCode"), "path")).containsExactly(
                "/api/v1/orders/{id}/confirm", "/api/v1/products", "/api/v1/products/options");

        // địa chỉ URL: đặc tả của chính Engine (OpenAPI 3) — không có API nào trùng với code của dự án phân tích
        JsonNode remote = call("POST", "/api/workspaces/" + ws + "/openapi-check", "{\"openapiUrl\":\"http://localhost:" + port + "/v3/api-docs\"}").data();
        assertThat(remote.get("specVersion").asString()).startsWith("OpenAPI 3");
        assertThat(remote.get("matched")).isEmpty();
        // không phải đặc tả → 422; địa chỉ sai dạng → 400
        assertThat(call("POST", "/api/workspaces/" + ws + "/openapi-check", "{\"openapiUrl\":\"http://localhost:" + port + "/api/health\"}").status).isEqualTo(422);
        assertThat(call("POST", "/api/workspaces/" + ws + "/openapi-check", "{\"openapiUrl\":\"ftp://x/y\"}").status).isEqualTo(400);
    }

    // ---------------- tiện ích ----------------

    private static String workspaceId;

    private synchronized String scannedWorkspace() throws Exception {
        if (workspaceId != null) {
            return workspaceId;
        }
        Res created = call("POST", "/api/workspaces", "{\"name\":\"Explanation IT\",\"sources\":["
                + "{\"rootPath\":\"fixtures/angular-mini\"},{\"rootPath\":\"fixtures/spring-flow\"}]}");
        assertThat(created.status).isEqualTo(201);
        String id = created.data().get("id").asString();
        String runId = call("POST", "/api/workspaces/" + id + "/scans", "{\"trigger\":\"IMPORT\"}").data().get("id").asString();
        for (int i = 0; i < 900; i++) {
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
        throw new AssertionError("Lần quét không kết thúc trong 90 giây");
    }

    /** {@code nodeKey} trên đường dẫn: mã hóa bằng NodeKeyCodec rồi mã hóa URL thêm một lần (key chứa '/'). */
    private static String enc(String nodeKey) {
        return URLEncoder.encode(NodeKeyCodec.encode(nodeKey), StandardCharsets.UTF_8);
    }

    private Res get(String ws, String nodeKey, String suffix) throws Exception {
        Res res = call("GET", "/api/workspaces/" + ws + "/nodes/" + enc(nodeKey) + (suffix.isEmpty() ? "" : "/" + suffix), null);
        assertThat(res.status).as(String.valueOf(res.body)).isEqualTo(200);
        return res;
    }

    private static JsonNode tab(JsonNode tabs, String title) {
        for (JsonNode t : tabs) {
            String name = t.get("title").asString();
            if (title.equals(name) || name.startsWith(title + " · ")) {
                return t;
            }
        }
        throw new AssertionError("Không có tab " + title + " trong " + texts(tabs, "title"));
    }

    private static List<String> texts(JsonNode array) {
        var out = new ArrayList<String>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static List<String> texts(JsonNode array, String field) {
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
}
