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
 * API Workspace / nguồn mã / duyệt và nhận diện thư mục (A1–A10, I4–I5) trên CSDL thật. {@code SOURCE_ROOT} là gốc repo;
 * mini fixture ở {@code fixtures/detection}. Phần Angular cần đã build sidecar ({@code npm --prefix analyzer-node run build}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "flowtracer.source-root=../..",
    "flowtracer.data-dir=target/test-data",
    "flowtracer.sidecar.cli-path=../../analyzer-node/dist/cli.js"
})
class WorkspaceApiIT {

    private static final String FIXTURE = "fixtures/detection";
    private static final Path SIDECAR_CLI = Path.of("..", "..", "analyzer-node", "dist", "cli.js");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    // ---------------- A6 duyệt thư mục ----------------

    @Test
    void browseListsFoldersAndBlocksTraversal() throws Exception {
        var root = call("GET", "/api/fs/browse?path=" + enc(FIXTURE), null);
        assertThat(root.status).isEqualTo(200);
        assertThat(names(root.data().get("folders"), "name")).containsExactly("shop-fullstack", "shop-monorepo", "unrelated");
        assertThat(root.data().get("parentPath").asString()).isEqualTo("fixtures");

        assertThat(call("GET", "/api/fs/browse?path=" + enc("../.."), null).status).isEqualTo(400);
        assertThat(call("GET", "/api/fs/browse?path=" + enc(FIXTURE + "/../../.."), null).status).isEqualTo(400);
        assertThat(call("GET", "/api/fs/browse?path=" + enc(FIXTURE + "/khong-co"), null).status).isEqualTo(404);
    }

    // ---------------- A10 nhận diện thư mục ----------------

    @Test
    void unrelatedFolderIsNotFound() throws Exception {
        var r = detect(FIXTURE + "/unrelated");
        assertThat(r.get("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(r.get("roots").size()).isZero();
    }

    @Test
    void subFolderOfBackendSuggestsBackendRoot() throws Exception {
        var r = detect(FIXTURE + "/shop-monorepo/backend/src/main/java");
        assertThat(r.get("status").asString()).isEqualTo("INSIDE_PROJECT");
        assertThat(r.get("suggestedRoot").get("path").asString()).isEqualTo(FIXTURE + "/shop-monorepo/backend");
    }

    @Test
    void engineModuleOfThisRepoSuggestsMavenRoot() throws Exception {
        var r = detect("engine/flowtracer-app");
        assertThat(r.get("status").asString()).isEqualTo("INSIDE_PROJECT");
        assertThat(r.get("suggestedRoot").get("path").asString()).isEqualTo("engine");
    }

    @Test
    void monorepoIsSplitIntoBackendAndFrontend() throws Exception {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
        var r = detect(FIXTURE + "/shop-monorepo");
        assertThat(r.get("status").asString()).isEqualTo("VALID");
        assertThat(r.get("warnings").size()).isZero();
        var roots = r.get("roots");
        assertThat(names(roots, "path")).containsExactly(FIXTURE + "/shop-monorepo/backend", FIXTURE + "/shop-monorepo/web");
        assertThat(names(roots, "role")).containsExactly("BACKEND", "FRONTEND");
        assertThat(names(roots, "version")).containsExactly("3.3.3", "21.0.0");
    }

    @Test
    void thisRepoHasEngineAndWebRoots() throws Exception {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
        var r = detect("");
        assertThat(r.get("status").asString()).isEqualTo("VALID");
        assertThat(names(r.get("roots"), "path")).contains("engine", "web");
    }

    // ---------------- A1–A5, A7–A9, I4–I5 ----------------

    @Test
    void workspaceLifecycle() throws Exception {
        var created = call("POST", "/api/workspaces", """
                {"name":"Shop IT","description":"thử","sources":[{"rootPath":"%s/shop-monorepo/backend"}]}
                """.formatted(FIXTURE));
        assertThat(created.status).isEqualTo(201);
        String id = created.data().get("id").asString();
        var sources = created.data().get("sources");
        assertThat(names(sources, "role")).containsExactly("BACKEND");
        assertThat(sources.get(0).get("detectedStacks").get(0).get("stackName").asString()).isEqualTo("Spring Boot");

        // trùng tên → 409
        var dup = call("POST", "/api/workspaces", "{\"name\":\"Shop IT\"}");
        assertThat(dup.status).isEqualTo(409);
        assertThat(dup.body.get("code").asString()).isEqualTo("WORKSPACE_NAME_TAKEN");

        // thư mục không có dự án → 422
        var bad = call("POST", "/api/workspaces/" + id + "/sources", "{\"rootPath\":\"" + FIXTURE + "/unrelated\"}");
        assertThat(bad.status).isEqualTo(422);
        assertThat(bad.body.get("code").asString()).isEqualTo("NO_PROJECT_ROOT");

        // thêm lại cùng thư mục → 409
        var again = call("POST", "/api/workspaces/" + id + "/sources", "{\"rootPath\":\"" + FIXTURE + "/shop-monorepo/backend\"}");
        assertThat(again.status).isEqualTo(409);

        // sửa tên và thư mục loại trừ
        String sourceId = sources.get(0).get("id").asString();
        var patched = call("PATCH", "/api/workspaces/" + id + "/sources/" + sourceId,
                "{\"name\":\"API\",\"excludeGlobs\":[\"generated/**\"]}");
        assertThat(patched.status).isEqualTo(200);
        assertThat(patched.data().get("name").asString()).isEqualTo("API");
        assertThat(names(patched.data().get("excludeGlobs"), null)).containsExactly("generated/**");

        // sửa Workspace
        var renamed = call("PATCH", "/api/workspaces/" + id, "{\"name\":\"Shop IT 2\"}");
        assertThat(renamed.data().get("name").asString()).isEqualTo("Shop IT 2");

        // bộ lọc nhiễu
        var filters = call("PUT", "/api/workspaces/" + id + "/noise-filters",
                "{\"manual\":[{\"pattern\":\"*.audit.*\",\"description\":\"log audit\"},{\"pattern\":\"*.audit.*\"}]}");
        assertThat(filters.status).isEqualTo(200);
        assertThat(filters.data().get("defaults").size()).isPositive();
        assertThat(names(filters.data().get("manual"), "pattern")).containsExactly("*.audit.*");

        // danh sách
        var list = call("GET", "/api/workspaces", null);
        assertThat(names(list.data(), "name")).contains("Shop IT 2");

        // gỡ nguồn mã, xóa Workspace
        assertThat(call("DELETE", "/api/workspaces/" + id + "/sources/" + sourceId, null).status).isEqualTo(200);
        assertThat(call("DELETE", "/api/workspaces/" + id, null).status).isEqualTo(200);
        assertThat(call("GET", "/api/workspaces/" + id, null).status).isEqualTo(404);
        // mã nguồn không bị đụng tới
        assertThat(Files.isRegularFile(Path.of("..", "..", FIXTURE, "shop-monorepo", "backend", "pom.xml"))).isTrue();
    }

    @Test
    void frontendInsideBackendRepoBecomesNestedSource() throws Exception {
        assumeTrue(Files.isRegularFile(SIDECAR_CLI), "Chưa build sidecar");
        var created = call("POST", "/api/workspaces", """
                {"name":"Fullstack IT","sources":[{"rootPath":"%s/shop-fullstack"}]}
                """.formatted(FIXTURE));
        assertThat(created.status).isEqualTo(201);
        String id = created.data().get("id").asString();
        var sources = created.data().get("sources");
        assertThat(names(sources, "role")).containsExactly("BACKEND", "FRONTEND");
        var backend = sources.get(0);
        var frontend = sources.get(1);
        assertThat(names(backend.get("excludeGlobs"), null)).containsExactly("frontend/**");
        assertThat(frontend.get("parentSourceId").asString()).isEqualTo(backend.get("id").asString());

        // người dùng xóa hết luật loại trừ → luật của Source con vẫn được giữ
        String backendId = backend.get("id").asString();
        var patched = call("PATCH", "/api/workspaces/" + id + "/sources/" + backendId, "{\"excludeGlobs\":[]}");
        assertThat(names(patched.data().get("excludeGlobs"), null)).containsExactly("frontend/**");

        // gỡ Source con → gỡ luật loại trừ ở Source cha
        String frontendId = frontend.get("id").asString();
        assertThat(call("DELETE", "/api/workspaces/" + id + "/sources/" + frontendId, null).status).isEqualTo(200);
        var ws = call("GET", "/api/workspaces/" + id, null);
        assertThat(ws.data().get("sources").get(0).get("excludeGlobs").size()).isZero();
        assertThat(call("DELETE", "/api/workspaces/" + id, null).status).isEqualTo(200);
    }

    // ---------------- vỏ bọc ApiResponse và kiểm tra dữ liệu ----------------

    @Test
    void responsesAreWrappedAndInputIsValidated() throws Exception {
        var ok = call("GET", "/api/workspaces", null);
        assertThat(ok.body.get("status").asInt()).isEqualTo(200);
        assertThat(ok.body.get("code").asString()).isEqualTo("OK");
        assertThat(ok.data().isArray()).isTrue();

        var blank = call("POST", "/api/workspaces", "{\"name\":\"   \"}");
        assertThat(blank.status).isEqualTo(400);
        assertThat(blank.body.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(blank.body.get("data").isNull()).isTrue();
        assertThat(blank.body.get("errors").get("name").asString()).isEqualTo("Tên Workspace không được để trống");

        var traversal = call("POST", "/api/fs/detect", "{\"rootPath\":\"shop/../..\"}");
        assertThat(traversal.status).isEqualTo(400);
        assertThat(traversal.body.get("errors").has("rootPath")).isTrue();

        var badGlob = call("POST", "/api/workspaces", """
                {"name":"Glob IT","sources":[{"rootPath":"%s/shop-monorepo/backend","excludeGlobs":["/abs/**"]}]}
                """.formatted(FIXTURE));
        assertThat(badGlob.status).isEqualTo(400);
        assertThat(badGlob.body.get("errors").propertyNames()).anyMatch(k -> k.startsWith("sources[0].excludeGlobs"));

        var browseBad = call("GET", "/api/fs/browse?path=" + enc("C:/Windows"), null);
        assertThat(browseBad.status).isEqualTo(400);
        assertThat(browseBad.body.get("errors").has("path")).isTrue();
    }

    // ---------------- tiện ích ----------------

    private JsonNode detect(String path) throws Exception {
        var r = call("POST", "/api/fs/detect", "{\"rootPath\":\"" + path + "\"}");
        assertThat(r.status).as("detect %s", path).isEqualTo(200);
        return r.data();
    }

    private record Res(int status, JsonNode body) {
        /** Dữ liệu nghiệp vụ nằm trong {@code data} của {@code ApiResponse}. */
        JsonNode data() {
            return body.get("data");
        }
    }

    private Res call(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = res.body().isEmpty() ? null : json.readTree(res.body());
        return new Res(res.statusCode(), node);
    }

    private static List<String> names(JsonNode array, String field) {
        var out = new ArrayList<String>();
        array.forEach(n -> out.add(field == null ? n.asString() : n.get(field).asString()));
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
