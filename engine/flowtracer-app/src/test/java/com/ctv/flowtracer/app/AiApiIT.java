package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ctv.flowtracer.app.ai.provider.LlmProvider;
import com.ctv.flowtracer.app.ai.provider.LlmRequest;
import com.ctv.flowtracer.app.ai.provider.LlmResult;
import com.ctv.flowtracer.app.common.web.NodeKeyCodec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Giai đoạn 7 với một provider GIẢ (không gọi mạng): cổng AI, các mục đích AI (mục đích + ràng buộc + lỗi, kịch bản, đặt tên phần tử,
 * phân tích liên kết), cache, bật / tắt mục đích, thống kê. Bỏ qua nếu chưa build sidecar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "flowtracer.source-root=../..",
    "flowtracer.data-dir=target/test-data-ai",
    "flowtracer.ai.startup-check=false",
    "flowtracer.sidecar.cli-path=../../analyzer-node/dist/cli.js"
})
class AiApiIT {

    private static final Path SIDECAR_CLI = Path.of("..", "..", "analyzer-node", "dist", "cli.js");
    private static final String CREATE_API = "java:endpoint:POST /api/v1/categories";
    private static final String SAVE = "ng:element:admin-app/CategoryFormDialogComponent#app-button.submit";
    private static final String EXPORT = "ng:element:admin-app/CategoryListComponent#button.click.onExport";
    private static final String SCREEN = "ng:screen:admin-app/categories";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    /** Provider giả: đọc yêu cầu trong prompt và trả JSON hợp lệ theo từng mục đích. */
    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        LlmProvider fakeProvider() {
            return new LlmProvider() {
                @Override
                public String providerId() {
                    return "fake";
                }

                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public LlmResult complete(LlmRequest r) {
                    String u = r.user();
                    String reply;
                    if (u.contains("Hãy giải thích")) {
                        reply = purposeReply(u);
                    } else if (u.contains("Các bước gốc")) {
                        reply = scenarioReply(u);
                    } else if (u.contains("Danh sách thành phần")) {
                        reply = namingReply(u);
                    } else if (u.contains("Các ứng viên đích")) {
                        reply = adviceReply(u);
                    } else if (u.contains("JSON Schema của dữ liệu gửi lên")) {
                        reply = "{\"request\": {\"khong\": \"khop\"}, \"response\": 5}"; // cố ý sai schema
                    } else {
                        reply = "OK";
                    }
                    return new LlmResult(reply, 120, 60, "fake-model");
                }
            };
        }

        static String purposeReply(String u) {
            String words = "Thành phần này dùng để thêm mới một danh mục vào hệ thống. ".repeat(6);
            var apis = new StringBuilder();
            Matcher key = Pattern.compile("(?m)^- mã: (a\\d+)").matcher(u);
            while (key.find()) {
                if (apis.length() > 0) {
                    apis.append(',');
                }
                apis.append("{\"api\": \"").append(key.group(1)).append("\", \"constraints\": [{\"id\": \"c1\", \"text\": \"Ràng buộc đã được viết lại\"},"
                        + "{\"id\": \"c99\", \"text\": \"id không có thật phải bị bỏ\"}], \"errors\": [{\"id\": \"e1\", \"text\": \"Lỗi đã được viết lại\"}]}");
            }
            return "{\"purpose\": \"" + words + "\", \"apis\": [" + apis + "]}";
        }

        static String scenarioReply(String u) {
            var items = new StringBuilder();
            Matcher m = Pattern.compile("(?m)^(\\d+): (.+)$").matcher(u.substring(u.indexOf("Các bước gốc")));
            while (m.find()) {
                if (items.length() > 0) {
                    items.append(',');
                }
                items.append("{\"index\": ").append(m.group(1)).append(", \"text\": \"Kể lại: ")
                        .append(m.group(2).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"}");
            }
            return "{\"items\": [" + items + "]}";
        }

        static String namingReply(String u) {
            var names = new StringBuilder();
            Matcher m = Pattern.compile("(?m)^(e\\d+) \\|").matcher(u);
            while (m.find()) {
                if (names.length() > 0) {
                    names.append(',');
                }
                names.append("{\"id\": \"").append(m.group(1)).append("\", \"label\": \"Tên chuẩn ").append(m.group(1)).append("\"}");
            }
            return "{\"names\": [" + names + "]}";
        }

        static String adviceReply(String u) {
            var analyses = new StringBuilder();
            Matcher m = Pattern.compile("(?m)^(c\\d+) \\|").matcher(u);
            while (m.find()) {
                if (analyses.length() > 0) {
                    analyses.append(',');
                }
                analyses.append("{\"id\": \"").append(m.group(1)).append("\", \"advice\": \"SHOULD\", \"reason\": \"Khớp phương thức và đường dẫn\"}");
            }
            boolean hasExtra = Pattern.compile("(?m)^x1 \\|").matcher(u).find();
            return "{\"analyses\": [" + analyses + "], \"extra\": "
                    + (hasExtra ? "{\"id\": \"x1\", \"reason\": \"Cùng phương thức, đường dẫn gần giống\"}" : "null") + "}";
        }
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void requireSidecar() {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
    }

    @Test
    void aiContentFlowsThroughTheGatewayWithCacheSwitchesAndFallbacks() throws Exception {
        String ws = scannedWorkspace();

        // ---- I1 / I3: provider giả sẵn sàng
        JsonNode settings = call("GET", "/api/settings/ai", null).data();
        assertThat(settings.get("provider").asString()).isEqualTo("fake");
        assertThat(settings.get("status").asString()).isEqualTo("READY");
        assertThat(settings.toString()).doesNotContain("API_KEY=").doesNotContain("apiKey");
        assertThat(texts(settings.get("purposes"), "purpose")).contains("PURPOSE_TEXT", "SCENARIO_POLISH", "MOCK_DATA", "EDGE_RESOLUTION", "ELEMENT_NAMING");
        assertThat(call("POST", "/api/settings/ai/test", null).data().get("ok").asBoolean()).isTrue();

        // ---- F1: chưa có bản AI → nội dung tất định + trạng thái MISSING
        JsonNode before = call("GET", explain(ws, CREATE_API), null).data();
        assertThat(before.get("ai").get("state").asString()).isEqualTo("MISSING");
        assertThat(before.get("purpose").get("source").asString()).isEqualTo("OPERATION");
        List<String> rawConstraints = texts(before.get("apis").get(0).get("constraints"));

        // ---- F4: sinh mục đích → F1 dùng bản AI; id không có thật bị bỏ, id còn lại giữ câu tất định
        JsonNode generated = regenerate(ws, "PURPOSE_TEXT", CREATE_API, null, true).data();
        assertThat(generated.get("state").asString()).isEqualTo("GENERATED");
        JsonNode after = call("GET", explain(ws, CREATE_API), null).data();
        assertThat(after.get("ai").get("state").asString()).isEqualTo("APPLIED");
        assertThat(after.get("purpose").get("source").asString()).isEqualTo("AI");
        assertThat(after.get("purpose").get("text").asString()).startsWith("Thành phần này dùng để thêm mới");
        List<String> aiConstraints = texts(after.get("apis").get(0).get("constraints"));
        assertThat(aiConstraints.getFirst()).as(after.toString()).isEqualTo("Ràng buộc đã được viết lại");
        assertThat(aiConstraints).hasSameSizeAs(rawConstraints).doesNotContain("id không có thật phải bị bỏ");
        assertThat(aiConstraints.subList(1, aiConstraints.size())).isEqualTo(rawConstraints.subList(1, rawConstraints.size()));
        assertThat(texts(after.get("apis").get(0).get("errors")).getFirst()).isEqualTo("Lỗi đã được viết lại");

        // ---- cache: không sinh lại khi force = false
        assertThat(regenerate(ws, "PURPOSE_TEXT", CREATE_API, null, false).data().get("state").asString()).isEqualTo("CACHED");

        // ---- tắt mục đích → nội dung tất định, F4 trả 503
        assertThat(call("PATCH", "/api/settings/ai", "{\"purposes\":{\"PURPOSE_TEXT\":false}}").status).isEqualTo(200);
        JsonNode off = call("GET", explain(ws, CREATE_API), null).data();
        assertThat(off.get("ai").get("state").asString()).isEqualTo("OFF");
        assertThat(off.get("purpose").get("source").asString()).isEqualTo("OPERATION");
        Res blocked = regenerate(ws, "PURPOSE_TEXT", CREATE_API, null, true);
        assertThat(blocked.status).isEqualTo(503);
        assertThat(blocked.body.get("code").asString()).isEqualTo("AI_DISABLED");
        assertThat(call("PATCH", "/api/settings/ai", "{\"purposes\":{\"PURPOSE_TEXT\":true}}").status).isEqualTo(200);
        assertThat(call("PATCH", "/api/settings/ai", "{\"purposes\":{\"KHONG_CO\":true}}").status).isEqualTo(400);

        // ---- F3: kịch bản giữ nguyên số bước khi AI viết lại
        String traceId = call("POST", "/api/workspaces/" + ws + "/traces", "{\"startKey\":\"" + SAVE + "\"}").data().get("traceId").asString();
        JsonNode frameworkScenario = call("GET", "/api/workspaces/" + ws + "/traces/" + traceId + "/scenario", null).data();
        assertThat(frameworkScenario.get("ai").get("state").asString()).isEqualTo("MISSING");
        assertThat(regenerate(ws, "SCENARIO_POLISH", traceId, null, true).status).isEqualTo(200);
        JsonNode polished = call("GET", "/api/workspaces/" + ws + "/traces/" + traceId + "/scenario", null).data();
        assertThat(polished.get("source").asString()).isEqualTo("AI");
        assertThat(polished.get("items")).hasSameSizeAs(frameworkScenario.get("items"));
        for (int i = 0; i < polished.get("items").size(); i++) {
            JsonNode p = polished.get("items").get(i);
            JsonNode f = frameworkScenario.get("items").get(i);
            assertThat(p.path("index").asInt(-1)).isEqualTo(f.path("index").asInt(-1));
            if (p.hasNonNull("index")) {
                assertThat(p.get("text").asString()).isEqualTo("Kể lại: " + f.get("text").asString());
            }
        }

        // ---- F2: AI trả dữ liệu mẫu sai schema → bị loại, vẫn dùng bộ sinh dự phòng
        Res badMock = regenerate(ws, "MOCK_DATA", CREATE_API, null, true);
        assertThat(badMock.status).isEqualTo(503);
        assertThat(badMock.body.get("code").asString()).isEqualTo("AI_UNAVAILABLE");
        JsonNode mock = call("GET", "/api/workspaces/" + ws + "/nodes/" + enc(CREATE_API) + "/mock-data", null).data();
        assertThat(mock.get("source").asString()).isEqualTo("RULE");

        // ---- ELEMENT_NAMING: tên AI xuất hiện trên cây Frontend, cây không đổi
        JsonNode treeBefore = call("GET", "/api/workspaces/" + ws + "/trees/frontend", null).data();
        JsonNode named = regenerate(ws, "ELEMENT_NAMING", SCREEN, null, true).data();
        assertThat(named.get("named").asInt()).isPositive();
        JsonNode treeAfter = call("GET", "/api/workspaces/" + ws + "/trees/frontend", null).data();
        assertThat(treeAfter.get("elementCount").asInt()).isEqualTo(treeBefore.get("elementCount").asInt());
        assertThat(collectAiLabels(treeAfter.get("apps"))).isNotEmpty().allMatch(l -> l.startsWith("Tên chuẩn e"));
        assertThat(collectAiLabels(treeBefore.get("apps"))).isEmpty();
        assertThat(regenerate(ws, "ELEMENT_NAMING", EXPORT, null, true).status).isEqualTo(422); // phần tử không phải thành phần chứa

        // ---- E2: AI giải thích ứng viên, ứng viên thêm phải là API có thật; liên kết KHÔNG đổi
        JsonNode trace = call("POST", "/api/workspaces/" + ws + "/traces", "{\"startKey\":\"" + EXPORT + "\"}").data();
        String edgeRef = null;
        for (JsonNode step : trace.get("steps")) {
            if ("UNRESOLVED".equals(step.get("confidence").asString()) && step.hasNonNull("edgeRef")) {
                edgeRef = step.get("edgeRef").asString();
                break;
            }
        }
        assertThat(edgeRef).as("luồng Xuất Excel phải có một liên kết chưa xác định").isNotNull();
        String edgeBefore = call("GET", "/api/workspaces/" + ws + "/edges/" + edgeRef, null).data().toString();
        JsonNode advice = call("POST", "/api/workspaces/" + ws + "/edges/" + edgeRef + "/advice", "{}").data();
        assertThat(advice.get("extra").get("node").get("kind").asString()).isEqualTo("API_ENDPOINT");
        assertThat(advice.get("extra").get("node").get("name").asString()).startsWith("GET ");
        // AI "Nên chọn" mà không có bằng chứng trong chuỗi gọi / mã thì bị hạ xuống "Chưa chắc"
        advice.get("analyses").forEach(x -> assertThat(x.get("advice").asString()).isNotEqualTo("SHOULD"));
        assertThat(call("GET", "/api/workspaces/" + ws + "/edges/" + edgeRef, null).data().toString()).isEqualTo(edgeBefore);

        // ---- I6: thống kê khớp các lần gọi ở trên
        JsonNode usage = call("GET", "/api/ai/usage", null).data();
        assertThat(usage.get("calls").asLong()).isGreaterThanOrEqualTo(5);
        assertThat(usage.get("inputTokens").asLong()).isPositive();
        assertThat(texts(usage.get("byPurpose"), "purpose")).contains("PURPOSE_TEXT", "SCENARIO_POLISH", "ELEMENT_NAMING", "EDGE_RESOLUTION");
        assertThat(usage.get("tokensToday").asLong()).isPositive();
    }

    // ---------------- tiện ích ----------------

    private static List<String> collectAiLabels(JsonNode nodes) {
        var out = new java.util.ArrayList<String>();
        for (JsonNode n : nodes) {
            if (n.hasNonNull("aiLabel")) {
                out.add(n.get("aiLabel").asString());
            }
            out.addAll(collectAiLabels(n.get("children")));
        }
        return out;
    }

    private String explain(String ws, String nodeKey) {
        return "/api/workspaces/" + ws + "/nodes/" + enc(nodeKey) + "/explanation";
    }

    private Res regenerate(String ws, String purpose, String target, String apiKey, boolean force) throws Exception {
        return call("POST", "/api/workspaces/" + ws + "/ai-artifacts/regenerate", "{\"purpose\":\"" + purpose + "\",\"targetKey\":\""
                + target.replace("\"", "\\\"") + "\"" + (apiKey == null ? "" : ",\"apiKey\":\"" + apiKey + "\"") + ",\"force\":" + force + "}");
    }

    private static String workspaceId;

    private synchronized String scannedWorkspace() throws Exception {
        if (workspaceId != null) {
            return workspaceId;
        }
        Res created = call("POST", "/api/workspaces", "{\"name\":\"AI IT\",\"sources\":["
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

    private static String enc(String nodeKey) {
        return URLEncoder.encode(NodeKeyCodec.encode(nodeKey), StandardCharsets.UTF_8);
    }

    private static List<String> texts(JsonNode array) {
        var out = new java.util.ArrayList<String>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static List<String> texts(JsonNode array, String field) {
        var out = new java.util.ArrayList<String>();
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
