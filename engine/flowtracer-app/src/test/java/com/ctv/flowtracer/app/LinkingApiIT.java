package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Workspace hai nguồn — Frontend {@code fixtures/angular-mini} (sidecar thật) và Backend {@code fixtures/spring-flow} — quét
 * xong thì bước LINK nối lời gọi HTTP với API: luồng End-to-End từ nút Lưu (D1), chi tiết liên kết (E1), chốt tay giữ qua quét
 * lại (E3–E5), luật liên kết nối lại ngay (E6–E7). Bỏ qua nếu chưa build sidecar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "flowtracer.source-root=../..",
    "flowtracer.data-dir=target/test-data",
    "flowtracer.sidecar.cli-path=../../analyzer-node/dist/cli.js"
})
class LinkingApiIT {

    private static final Path SIDECAR_CLI = Path.of("..", "..", "analyzer-node", "dist", "cli.js");
    private static final String SAVE = "ng:element:admin-app/CategoryFormDialogComponent#app-button.submit";
    private static final String EXPORT = "ng:element:admin-app/CategoryListComponent#button.click.onExport";
    private static final String EXPORT_CALL = "ng:http:admin-app/HttpCategoryRepository#exportExcel:0";
    private static final String LIST_API = "java:endpoint:GET /api/v1/categories";

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
    void linksFrontendToBackendAndKeepsManualDecisions() throws Exception {
        Res created = call("POST", "/api/workspaces", "{\"name\":\"Linking IT\",\"sources\":["
                + "{\"rootPath\":\"fixtures/angular-mini\"},{\"rootPath\":\"fixtures/spring-flow\"}]}");
        assertThat(created.status).isEqualTo(201);
        String ws = created.data().get("id").asString();
        JsonNode run = scan(ws, "IMPORT");
        assertThat(names(run.get("stats").get("stageDurations"), "stage")).contains("PERSIST", "LINK", "FINALIZE");

        // --- D1: nút Lưu của hộp thoại → luồng End-to-End ---
        JsonNode save = trace(ws, SAVE);
        assertThat(save.get("uiOnly").asBoolean()).isFalse();
        assertThat(names(save.get("participants"), "label")).containsExactly(
                "User", "Frontend", "Controller Danh mục", "Service Danh mục", "Repository Danh mục", "Bảng category");
        JsonNode httpStep = save.get("steps").get(1);
        assertThat(httpStep.get("kind").asString()).isEqualTo("HTTP_REQUEST");
        assertThat(httpStep.get("label").asString()).isEqualTo("POST /api/v1/categories");
        assertThat(httpStep.get("confidence").asString()).isEqualTo("INFERRED");

        // --- E1: bấm mũi tên "Suy luận" → lý do (hằng số + luật proxy) ---
        JsonNode edge = call("GET", "/api/workspaces/" + ws + "/edges/" + httpStep.get("edgeRef").asString(), null).data();
        assertThat(edge.get("kind").asString()).isEqualTo("HTTP_REQUEST");
        assertThat(edge.get("reason").asString()).contains("CATEGORY_BASE").contains("^/gateway");
        assertThat(edge.get("to").get("name").asString()).isEqualTo("POST /api/v1/categories");
        assertThat(edge.get("overridable").asBoolean()).isTrue();
        assertThat(edge.get("override").isNull()).isTrue();
        assertThat(call("GET", "/api/workspaces/" + ws + "/edges/00000000-0000-0000-0000-000000000000", null).status).isEqualTo(404);

        // --- E6: luật tự phát hiện từ proxy.conf.json ---
        JsonNode rules = call("GET", "/api/workspaces/" + ws + "/link-rules", null).data();
        assertThat(names(rules.get("detected"), "pattern")).contains("^/gateway");
        assertThat(rules.get("manual")).isEmpty();

        // --- Lời gọi không khớp API nào: chưa xác định, không đoán ---
        JsonNode export = trace(ws, EXPORT);
        JsonNode exportStep = export.get("steps").get(1);
        assertThat(exportStep.get("confidence").asString()).isEqualTo("UNRESOLVED");
        assertThat(names(export.get("participants"), "label")).contains("API chưa xác định");

        // --- E4: kiểm tra dữ liệu ---
        assertThat(call("POST", "/api/workspaces/" + ws + "/link-overrides",
                "{\"fromNodeKey\":\"" + EXPORT_CALL + "\",\"edgeKind\":\"HTTP_REQUEST\",\"decision\":\"PIN\"}").status).isEqualTo(400);
        assertThat(call("POST", "/api/workspaces/" + ws + "/link-overrides",
                "{\"fromNodeKey\":\"" + EXPORT_CALL + "\",\"toNodeKey\":\"" + SAVE + "\",\"edgeKind\":\"HTTP_REQUEST\",\"decision\":\"PIN\"}").status)
                .as("đích phải là API").isEqualTo(400);
        assertThat(call("POST", "/api/workspaces/" + ws + "/link-overrides",
                "{\"fromNodeKey\":\"ng:http:không-có\",\"toNodeKey\":\"" + LIST_API + "\",\"edgeKind\":\"HTTP_REQUEST\",\"decision\":\"PIN\"}").status)
                .isEqualTo(404);

        // --- E4: chốt tay liên kết chưa xác định → diagram đổi ngay ---
        Res pinned = call("POST", "/api/workspaces/" + ws + "/link-overrides",
                "{\"fromNodeKey\":\"" + EXPORT_CALL + "\",\"toNodeKey\":\"" + LIST_API
                        + "\",\"edgeKind\":\"HTTP_REQUEST\",\"decision\":\"PIN\",\"note\":\"Xuất Excel dùng chung API danh sách\"}");
        assertThat(pinned.status).isEqualTo(201);
        assertThat(pinned.body.get("message").asString()).isEqualTo("Đã chốt tay liên kết");
        assertThat(pinned.data().get("toNodeKey").asString()).isEqualTo(LIST_API);
        assertThat(pinned.data().get("toName").asString()).as("tên thân thiện của API").isEqualTo("Tìm kiếm danh mục");
        assertPinned(trace(ws, EXPORT));

        // --- Quét lại: chốt tay vẫn áp dụng, vẫn có trong danh sách (E3) ---
        scan(ws, "RESCAN");
        assertPinned(trace(ws, EXPORT));
        JsonNode overrides = call("GET", "/api/workspaces/" + ws + "/link-overrides", null).data();
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).get("stale").asBoolean()).isFalse();
        assertThat(overrides.get(0).get("note").asString()).isEqualTo("Xuất Excel dùng chung API danh sách");
        // liên kết được ghi lại ở mỗi lần quét: lấy edgeRef mới từ diagram
        String newRef = trace(ws, EXPORT).get("steps").get(1).get("edgeRef").asString();
        JsonNode detail = call("GET", "/api/workspaces/" + ws + "/edges/" + newRef, null).data();
        assertThat(detail.get("confidence").asString()).as("mức tin cậy tự động không đổi").isEqualTo("UNRESOLVED");
        assertThat(detail.get("override").get("decision").asString()).isEqualTo("PIN");

        // --- E5: bỏ chốt tay → quay về kết quả tự động ---
        String overrideId = overrides.get(0).get("id").asString();
        assertThat(call("DELETE", "/api/workspaces/" + ws + "/link-overrides/" + overrideId, null).status).isEqualTo(200);
        assertThat(trace(ws, EXPORT).get("steps").get(1).get("confidence").asString()).isEqualTo("UNRESOLVED");
        assertThat(call("DELETE", "/api/workspaces/" + ws + "/link-overrides/" + overrideId, null).status).isEqualTo(404);

        // --- E7: luật thủ công → nối lại ngay, báo số liên kết đổi ---
        assertThat(call("PUT", "/api/workspaces/" + ws + "/link-rules",
                "{\"manual\":[{\"type\":\"REWRITE\",\"pattern\":\"^/(gateway\"}]}").status).isEqualTo(400);
        Res saved = call("PUT", "/api/workspaces/" + ws + "/link-rules",
                "{\"manual\":[{\"type\":\"REWRITE\",\"pattern\":\"^/gateway(/api/v1/categories)/export$\",\"replacement\":\"$1\","
                        + "\"description\":\"Xuất Excel dùng API danh sách\"}]}");
        assertThat(saved.status).isEqualTo(200);
        assertThat(saved.body.get("message").asString()).isEqualTo("Đã nối lại 1 liên kết");
        assertThat(saved.data().get("relink").get("changed").asInt()).isEqualTo(1);
        assertThat(names(saved.data().get("manual"), "origin")).containsExactly("MANUAL");
        JsonNode relinked = trace(ws, EXPORT).get("steps").get(1);
        assertThat(relinked.get("label").asString()).isEqualTo("GET /api/v1/categories");
        assertThat(relinked.get("confidence").asString()).isEqualTo("INFERRED");
    }

    private static void assertPinned(JsonNode trace) {
        JsonNode step = trace.get("steps").get(1);
        assertThat(step.get("label").asString()).isEqualTo("GET /api/v1/categories");
        assertThat(step.get("pinned").asBoolean()).isTrue();
        assertThat(step.get("confidence").asString()).isEqualTo("CERTAIN");
        assertThat(names(trace.get("participants"), "label")).contains("Controller Danh mục", "Bảng category");
    }

    // ---------------- tiện ích ----------------

    private JsonNode trace(String ws, String startKey) throws Exception {
        Res res = call("POST", "/api/workspaces/" + ws + "/traces", "{\"startKey\":\"" + startKey + "\"}");
        assertThat(res.status).as(String.valueOf(res.body)).isEqualTo(200);
        return res.data();
    }

    private JsonNode scan(String ws, String trigger) throws Exception {
        String runId = call("POST", "/api/workspaces/" + ws + "/scans", "{\"trigger\":\"" + trigger + "\"}").data().get("id").asString();
        for (int i = 0; i < 900; i++) {
            JsonNode run = call("GET", "/api/scans/" + runId, null).data();
            String status = run.get("status").asString();
            if (List.of("SUCCEEDED", "PARTIAL").contains(status)) {
                assertThat(status).as(run.toString()).isEqualTo("SUCCEEDED");
                return run;
            }
            assertThat(status).as(run.toString()).isIn("PENDING", "RUNNING");
            Thread.sleep(100);
        }
        throw new AssertionError("Lần quét không kết thúc trong 90 giây");
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
}
