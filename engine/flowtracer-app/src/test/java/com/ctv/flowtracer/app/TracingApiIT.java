package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Dò vết (API D1) và xuất diagram (D2) trên CSDL thật: quét {@code fixtures/spring-flow} bằng adapter Spring, rồi dò từng
 * API qua HTTP. Kiểm tra IR đã lưu và nạp lại từ PostgreSQL cho ra đúng luồng (cạnh, ứng viên, mức tin cậy, mã cạnh).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {"flowtracer.source-root=../..", "flowtracer.data-dir=target/test-data"})
class TracingApiIT {

    private static final String FIXTURE = "fixtures/spring-flow";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private static String workspaceId;

    @BeforeAll
    static void noop() {
        workspaceId = null;
    }

    @Test
    void tracesEveryApiOfTheScannedFixture() throws Exception {
        String ws = scannedWorkspace();

        // --- D1: luồng thêm mới (đối chiếu Phụ lục A: 16 bước, một khối alt) ---
        long started = System.nanoTime();
        Res created = trace(ws, "{\"startKey\":\"java:endpoint:POST /api/v1/categories\"}");
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertThat(created.status).isEqualTo(200);
        JsonNode trace = created.data();
        assertThat(millis).as("NFR-04: dò vết ≤ 2 giây").isLessThan(2_000);
        assertThat(names(trace.get("participants"), "label"))
                .containsExactly("Client", "Controller Danh mục", "Service Danh mục", "Repository Danh mục", "Bảng category");
        assertThat(trace.get("steps")).hasSize(16);
        assertThat(trace.get("steps").get(0).get("label").asString()).isEqualTo("POST /api/v1/categories");
        assertThat(trace.get("steps").get(0).get("edgeRef").asString()).matches("[0-9a-f-]{36}");
        assertThat(trace.get("steps").get(8).get("error").asBoolean()).isTrue();
        JsonNode alt = trace.get("blocks").get(0);
        assertThat(alt.get("type").asString()).isEqualTo("ALT");
        assertThat(alt.get("elseBranches").get(0).get("fromStep").asInt()).isEqualTo(10);
        assertThat(trace.get("startName").asString()).isEqualTo("POST /api/v1/categories");
        assertThat(trace.get("startDisplayName").asString()).isEqualTo("Thêm mới danh mục");

        // --- cùng đầu vào → cùng trace_id và cùng kết quả (từ bộ nhớ đệm) ---
        JsonNode again = trace(ws, "{\"startKey\":\"java:endpoint:POST /api/v1/categories\",\"maxDepth\":12}").data();
        assertThat(again.get("traceId").asString()).isEqualTo(trace.get("traceId").asString());
        assertThat(again.get("steps")).isEqualTo(trace.get("steps"));

        // --- lời gọi chưa xác định: nét UNRESOLVED, cảnh báo có số bước, không đi sâu ---
        JsonNode delete = trace(ws, "{\"startKey\":\"java:endpoint:DELETE /api/v1/categories/{id}\"}").data();
        JsonNode unresolved = null;
        for (JsonNode step : delete.get("steps")) {
            if ("UNRESOLVED".equals(step.get("confidence").asString()) && !step.get("returning").asBoolean()) {
                unresolved = step;
            }
        }
        assertThat(unresolved).isNotNull();
        assertThat(unresolved.get("reason").asString()).contains("2 lớp hiện thực");
        assertThat(delete.get("warnings").get(0).asString())
                .isEqualTo("1 lời gọi chưa xác định được đích (xem bước " + unresolved.get("index").asInt() + ")");
        assertThat(names(delete.get("participants"), "label")).contains("Service ProductQuery").doesNotContain("Repository Product");

        // --- D2: xuất diagram ---
        String traceId = trace.get("traceId").asString();
        Res puml = call("GET", "/api/workspaces/" + ws + "/traces/" + traceId + "/export?format=plantuml", null);
        assertThat(puml.status).isEqualTo(200);
        assertThat(puml.data().get("fileName").asString()).isEqualTo("POST_api_v1_categories.puml");
        assertThat(puml.data().get("content").asString()).startsWith("@startuml POST_api_v1_categories").contains("alt ").contains("else ");
        Res svg = call("GET", "/api/workspaces/" + ws + "/traces/" + traceId + "/export?format=SVG", null);
        assertThat(svg.status).isEqualTo(200);
        assertThat(svg.data().get("mediaType").asString()).isEqualTo("image/svg+xml");
        assertThat(svg.data().get("content").asString()).startsWith("<svg");

        // --- lỗi ---
        assertThat(call("GET", "/api/workspaces/" + ws + "/traces/" + traceId + "/export?format=pdf", null).status).isEqualTo(400);
        Res expired = call("GET", "/api/workspaces/" + ws + "/traces/khong-co/export?format=svg", null);
        assertThat(expired.status).isEqualTo(404);
        assertThat(expired.body.get("code").asString()).isEqualTo("TRACE_NOT_FOUND");
        Res missing = trace(ws, "{\"startKey\":\"java:endpoint:GET /khong-co\"}");
        assertThat(missing.status).isEqualTo(404);
        assertThat(missing.body.get("code").asString()).isEqualTo("NODE_NOT_FOUND");
        Res notApi = trace(ws, "{\"startKey\":\"java:table:category\"}");
        assertThat(notApi.status).isEqualTo(422);
        assertThat(notApi.body.get("code").asString()).isEqualTo("TRACE_START_UNSUPPORTED");
        assertThat(trace(ws, "{\"startKey\":\"\"}").status).isEqualTo(400);
        assertThat(trace(ws, "{\"startKey\":\"java:endpoint:POST /api/v1/categories\",\"maxDepth\":0}").status).isEqualTo(400);
        assertThat(call("POST", "/api/workspaces/00000000-0000-0000-0000-000000000000/traces", "{\"startKey\":\"x\"}").status)
                .isEqualTo(404);
    }

    @Test
    void depthLimitCutsTheFlowWithAWarning() throws Exception {
        String ws = scannedWorkspace();

        JsonNode shallow = trace(ws, "{\"startKey\":\"java:endpoint:POST /api/v1/categories\",\"maxDepth\":1}").data();

        assertThat(shallow.get("steps").size()).isLessThan(16);
        assertThat(shallow.get("warnings").toString()).contains("vượt độ sâu dò vết");
    }

    // ---------------- tiện ích ----------------

    /** Tạo Workspace trên fixture và quét một lần (dùng lại cho các test trong lớp). */
    private synchronized String scannedWorkspace() throws Exception {
        if (workspaceId != null) {
            return workspaceId;
        }
        Res created = call("POST", "/api/workspaces", "{\"name\":\"Flow IT\",\"sources\":[{\"rootPath\":\"" + FIXTURE + "\"}]}");
        assertThat(created.status).isEqualTo(201);
        String id = created.data().get("id").asString();
        Res scan = call("POST", "/api/workspaces/" + id + "/scans", "{\"trigger\":\"IMPORT\"}");
        String runId = scan.data().get("id").asString();
        for (int i = 0; i < 600; i++) {
            String status = call("GET", "/api/scans/" + runId, null).data().get("status").asString();
            if (List.of("SUCCEEDED", "PARTIAL").contains(status)) {
                workspaceId = id;
                return id;
            }
            assertThat(status).isIn("PENDING", "RUNNING");
            Thread.sleep(100);
        }
        throw new AssertionError("Lần quét không kết thúc trong 60 giây");
    }

    private Res trace(String ws, String body) throws Exception {
        return call("POST", "/api/workspaces/" + ws + "/traces", body);
    }

    private static List<String> names(JsonNode array, String field) {
        var out = new ArrayList<String>();
        array.forEach(n -> out.add(n.get(field).asString()));
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

    @SuppressWarnings("unused")
    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
