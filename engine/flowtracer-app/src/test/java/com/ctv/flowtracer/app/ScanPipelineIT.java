package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.scan.adapter.AdapterRegistry;
import com.ctv.flowtracer.app.scan.adapter.SidecarRunner;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pipeline quét (API B1–B6, C1) với adapter giả "fake" trên thư mục tạm: quét lần đầu, quét lại khi thêm/sửa/xóa file,
 * quét lại không đổi, file lỗi → PARTIAL, hủy giữa chừng giữ IR cũ, quét chồng → 409.
 *
 * <p>File {@code *.fake}: mỗi dòng {@code GET /đường-dẫn} là một endpoint; dòng {@code BROKEN} là lỗi cú pháp; dòng
 * {@code SLOW} chặn bộ phân tích tới khi test thả ra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScanPipelineIT {

    private static final Path ROOT = createRoot();
    private static final Path PROJECT = ROOT.resolve("shop");

    static final CountDownLatch SLOW_STARTED = new CountDownLatch(1);
    static final CountDownLatch SLOW_RELEASE = new CountDownLatch(1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("flowtracer.source-root", ROOT::toString);
        registry.add("flowtracer.data-dir", () -> ROOT.resolveSibling(ROOT.getFileName() + "-data").toString());
    }

    @TestConfiguration
    static class FakeAdapterConfig {
        @Bean
        @Primary
        AdapterRegistry fakeAdapterRegistry(SidecarRunner runner, JsonMapper json) {
            return new AdapterRegistry(runner, json) {
                private final StackAdapter fake = new FakeAdapter();

                @Override
                public List<StackAdapter> all() {
                    return List.of(fake);
                }

                @Override
                public Optional<StackAdapter> byId(String id) {
                    return Optional.of(fake).filter(a -> a.id().equals(id));
                }
            };
        }
    }

    @Value("${local.server.port}")
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    private static String workspaceId;
    private static String sourceId;

    @Test
    @Order(1)
    void firstScanAnalyzesEveryFile() throws Exception {
        write("fake.project", "");
        write("app.cfg", "module=Fake");
        write("a.fake", "GET /api/a\nPOST /api/a");
        write("b.fake", "GET /api/b");
        write("c.fake", "GET /api/c");
        write("notes.txt", "không phải file của bộ phân tích");
        Files.createDirectories(PROJECT.resolve("node_modules/x"));
        write("node_modules/x/ignored.fake", "GET /api/ignored");

        var created = call("POST", "/api/workspaces", "{\"name\":\"Scan IT\",\"sources\":[{\"rootPath\":\"shop\"}]}");
        assertThat(created.status).isEqualTo(201);
        workspaceId = created.data().get("id").asString();
        sourceId = created.data().get("sources").get(0).get("id").asString();

        JsonNode run = scanAndWait("IMPORT");
        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        JsonNode stats = run.get("stats");
        assertThat(files(stats)).containsEntry("total", 3).containsEntry("added", 3).containsEntry("extracted", 3)
                .containsEntry("fromCache", 0).containsEntry("errors", 0);
        assertThat(stats.get("endpoints").asLong()).isEqualTo(4);
        assertThat(stats.get("noChanges").asBoolean()).isFalse();
        assertThat(stages(stats)).containsExactly("DISCOVER", "DIFF", "EXTRACT", "RESOLVE", "PERSIST", "FINALIZE");
        assertThat(stats.get("sources").get(0).get("endpoints").asInt()).isEqualTo(4);

        JsonNode tree = tree();
        assertThat(tree.get("endpointCount").asInt()).isEqualTo(4);
        assertThat(tree.get("modules").get(0).get("name").asString()).isEqualTo("Fake");

        var ws = call("GET", "/api/workspaces/" + workspaceId, null);
        assertThat(ws.data().get("lastScan").get("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    @Order(2)
    void rescanOnlyAnalyzesChangedFiles() throws Exception {
        write("b.fake", "GET /api/b\nDELETE /api/b");
        write("d.fake", "GET /api/d");
        Files.delete(PROJECT.resolve("c.fake"));

        JsonNode run = scanAndWait("RESCAN");
        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(files(run.get("stats"))).containsEntry("total", 3).containsEntry("added", 1).containsEntry("modified", 1)
                .containsEntry("removed", 1).containsEntry("unchanged", 1).containsEntry("extracted", 2)
                .containsEntry("fromCache", 1);
        assertThat(endpointPaths()).containsExactly(
                "DELETE /api/b", "GET /api/a", "GET /api/b", "GET /api/d", "POST /api/a");
    }

    @Test
    @Order(3)
    void rescanWithoutChangesSkipsAnalysis() throws Exception {
        JsonNode run = scanAndWait("RESCAN");
        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(run.get("errorSummary").asString()).isEqualTo("Không có thay đổi kể từ lần quét trước");
        JsonNode stats = run.get("stats");
        assertThat(stats.get("noChanges").asBoolean()).isTrue();
        assertThat(files(stats)).containsEntry("total", 3).containsEntry("unchanged", 3).containsEntry("extracted", 0);
        assertThat(stages(stats)).containsExactly("DISCOVER", "DIFF", "FINALIZE");
        assertThat(stats.get("endpoints").asLong()).isEqualTo(5);
    }

    @Test
    @Order(4)
    void changingOnlyAConfigFileRegroupsResultsWithoutReanalyzingSourceFiles() throws Exception {
        write("app.cfg", "module=Renamed");

        JsonNode run = scanAndWait("RESCAN");

        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        JsonNode stats = run.get("stats");
        assertThat(stats.get("noChanges").asBoolean()).isFalse();
        assertThat(stats.get("configChanges").asInt()).isEqualTo(1);
        assertThat(files(stats)).containsEntry("total", 3).containsEntry("added", 0).containsEntry("modified", 0)
                .containsEntry("unchanged", 3).containsEntry("extracted", 0).containsEntry("fromCache", 3);
        assertThat(stages(stats)).containsExactly("DISCOVER", "DIFF", "EXTRACT", "RESOLVE", "PERSIST", "FINALIZE");
        assertThat(tree().get("modules").get(0).get("name").asString()).isEqualTo("Renamed");

        // lần quét kế tiếp không còn thay đổi nào
        JsonNode again = scanAndWait("RESCAN");
        assertThat(again.get("stats").get("noChanges").asBoolean()).isTrue();
        assertThat(again.get("stats").get("configChanges").asInt()).isZero();
    }

    @Test
    @Order(5)
    void newAnalyzerVersionReanalyzesEverythingEvenWhenNoFileChanged() throws Exception {
        // Lần quét ghi trước khi có thông tin phiên bản (như dữ liệu của người dùng đã quét bằng bản cũ): coi là đã đổi
        jdbc.update("""
                UPDATE scan_runs SET stats = jsonb_set(stats, '{sources}',
                    (SELECT jsonb_agg(s - 'analysisVersion') FROM jsonb_array_elements(stats->'sources') s))
                WHERE workspace_id = ?::uuid AND stats IS NOT NULL
                """, workspaceId);
        JsonNode legacy = scanAndWait("RESCAN");
        assertThat(legacy.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(legacy.get("stats").get("noChanges").asBoolean()).isFalse();
        // phân tích lại toàn bộ nhưng nội dung file và phiên bản không đổi nên dữ kiện lấy từ cache
        assertThat(files(legacy.get("stats"))).containsEntry("extracted", 0).containsEntry("fromCache", 3);
        assertThat(scanAndWait("RESCAN").get("stats").get("noChanges").asBoolean()).isTrue();

        FakeAdapter.VERSION = "2";

        JsonNode run = scanAndWait("RESCAN");

        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(run.get("errorSummary").asString()).contains("Bộ phân tích đã được nâng cấp");
        JsonNode stats = run.get("stats");
        assertThat(stats.get("noChanges").asBoolean()).isFalse();
        assertThat(files(stats)).containsEntry("unchanged", 3).containsEntry("extracted", 3).containsEntry("fromCache", 0);
        assertThat(stages(stats)).containsExactly("DISCOVER", "DIFF", "EXTRACT", "RESOLVE", "PERSIST", "FINALIZE");

        // đã dựng lại bằng phiên bản mới: lần quét kế tiếp không còn gì để làm
        assertThat(scanAndWait("RESCAN").get("stats").get("noChanges").asBoolean()).isTrue();
    }

    @Test
    @Order(6)
    void brokenFileMakesScanPartialAndIsListed() throws Exception {
        write("e.fake", "GET /api/e\nBROKEN");

        JsonNode run = scanAndWait("RESCAN");
        assertThat(run.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(files(run.get("stats"))).containsEntry("errors", 1);
        assertThat(run.get("stats").get("diagnosticErrors").asInt()).isEqualTo(1);
        assertThat(run.get("stats").get("diagnostics").get(0).get("message").asString()).isEqualTo("Lỗi cú pháp ở dòng 2");
        assertThat(endpointPaths()).hasSize(5).doesNotContain("GET /api/e");

        var errors = call("GET", "/api/scans/" + run.get("id").asString() + "/errors", null);
        assertThat(errors.data().get("total").asInt()).isEqualTo(1);
        JsonNode item = errors.data().get("items").get(0);
        assertThat(item.get("filePath").asString()).isEqualTo("e.fake");
        assertThat(item.get("fullPath").asString()).isEqualTo("shop/e.fake");
        assertThat(item.get("reason").asString()).contains("dòng 2");
    }

    @Test
    @Order(7)
    void cancelKeepsPreviousResultAndBlocksConcurrentScan() throws Exception {
        List<String> before = endpointPaths();
        write("slow.fake", "GET /api/slow\nSLOW");
        try {
            var started = call("POST", "/api/workspaces/" + workspaceId + "/scans", "{\"trigger\":\"RESCAN\"}");
            assertThat(started.status).isEqualTo(202);
            String runId = started.data().get("id").asString();
            assertThat(SLOW_STARTED.await(20, TimeUnit.SECONDS)).isTrue();

            var second = call("POST", "/api/workspaces/" + workspaceId + "/scans", "{\"trigger\":\"RESCAN\"}");
            assertThat(second.status).isEqualTo(409);
            assertThat(second.body.get("code").asString()).isEqualTo("SCAN_IN_PROGRESS");

            var cancelled = call("POST", "/api/scans/" + runId + "/cancel", null);
            assertThat(cancelled.data().get("status").asString()).isEqualTo("CANCELLED");
            assertThat(endpointPaths()).isEqualTo(before);
        } finally {
            SLOW_RELEASE.countDown();
        }
        Files.delete(PROJECT.resolve("slow.fake"));
        JsonNode unchanged = scanAndWait("RESCAN");
        assertThat(unchanged.get("status").asString()).isEqualTo("PARTIAL");
        // không phân tích lại nhưng IR vẫn là kết quả lần trước → chẩn đoán mang sang, có căn cứ
        assertThat(unchanged.get("stats").get("noChanges").asBoolean()).isTrue();
        assertThat(unchanged.get("stats").get("diagnosticErrors").asInt()).isEqualTo(1);
        assertThat(unchanged.get("stats").get("diagnostics").get(0).get("filePath").asString()).isEqualTo("e.fake");

        var history = call("GET", "/api/workspaces/" + workspaceId + "/scans?page=1&limit=2", null);
        assertThat(history.data().get("total").asInt()).isEqualTo(12);
        assertThat(history.data().get("items").get(1).get("status").asString()).isEqualTo("CANCELLED");
    }

    @Test
    @Order(8)
    void deletingWorkspaceRemovesScanDataButNotSourceFiles() throws Exception {
        assertThat(call("DELETE", "/api/workspaces/" + workspaceId, null).status).isEqualTo(200);
        assertThat(Files.exists(PROJECT.resolve("a.fake"))).isTrue();
        assertThat(call("GET", "/api/workspaces/" + workspaceId + "/trees/backend", null).status).isEqualTo(404);
        assertThat(sourceId).isNotBlank();
    }

    // ---------------- tiện ích ----------------

    private JsonNode scanAndWait(String trigger) throws Exception {
        var started = call("POST", "/api/workspaces/" + workspaceId + "/scans", "{\"trigger\":\"" + trigger + "\"}");
        assertThat(started.status).as("bắt đầu quét").isEqualTo(202);
        String runId = started.data().get("id").asString();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode run = call("GET", "/api/scans/" + runId, null).data();
            if (List.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(run.get("status").asString())) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Lần quét không kết thúc trong 60 giây");
    }

    private JsonNode tree() throws Exception {
        return call("GET", "/api/workspaces/" + workspaceId + "/trees/backend", null).data();
    }

    private List<String> endpointPaths() throws Exception {
        var result = new ArrayList<String>();
        tree().get("modules").forEach(m -> m.get("endpoints")
                .forEach(e -> result.add(e.get("httpMethod").asString() + " " + e.get("path").asString())));
        return result.stream().sorted().toList();
    }

    private static List<String> stages(JsonNode stats) {
        var result = new ArrayList<String>();
        stats.get("stageDurations").forEach(d -> result.add(d.get("stage").asString()));
        return result;
    }

    private static Map<String, Integer> files(JsonNode stats) {
        var result = new java.util.TreeMap<String, Integer>();
        stats.get("files").properties().forEach(e -> result.put(e.getKey(), e.getValue().asInt()));
        return result;
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

    private static void write(String relative, String content) throws IOException {
        Path file = PROJECT.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("flowtracer-scan-it").toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------- adapter giả ----------------

    /** Dữ kiện của một file {@code .fake}. */
    public record FakeFacts(String relativePath, String contentHash, List<String> endpoints, Integer brokenLine)
            implements FileFacts {}

    static final class FakeAdapter implements StackAdapter {
        /** Phiên bản bộ phân tích; test đổi giá trị này để mô phỏng nâng cấp bộ phân tích. */
        static volatile String VERSION = "1";

        @Override
        public String factsVersion() {
            return VERSION;
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public Set<SourceRole> roles() {
            return Set.of(SourceRole.BACKEND);
        }

        @Override
        public List<String> rootMarkers() {
            return List.of("fake.project");
        }

        @Override
        public boolean analyzes(String relativePath) {
            return relativePath.endsWith(".fake");
        }

        @Override
        public boolean affectsAnalysis(String relativePath) {
            return relativePath.endsWith(".cfg");
        }

        @Override
        public Class<? extends FileFacts> factsType() {
            return FakeFacts.class;
        }

        @Override
        public DetectResult detect(DetectContext ctx) {
            return new DetectResult(0.9, "Fake", "1.0", "fake.project");
        }

        @Override
        public FileFacts extract(ExtractContext ctx, SourceFile file) {
            var endpoints = new ArrayList<String>();
            Integer broken = null;
            String[] lines = file.content().split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.equals("SLOW")) {
                    SLOW_STARTED.countDown();
                    try {
                        SLOW_RELEASE.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else if (line.equals("BROKEN")) {
                    broken = i + 1;
                } else if (!line.isEmpty()) {
                    endpoints.add(line);
                }
            }
            return new FakeFacts(file.relativePath(), file.contentHash(), endpoints, broken);
        }

        @Override
        public IrFragment resolve(ResolveContext ctx, List<FileFacts> facts) {
            var nodes = new ArrayList<IrNode>();
            var diagnostics = new ArrayList<Diagnostic>();
            String module = ctx.loader().load("app.cfg").map(c -> c.replace("module=", "").strip()).orElse("Fake");
            for (FileFacts f : facts) {
                FakeFacts fake = (FakeFacts) f;
                if (fake.brokenLine() != null) {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.ERROR, fake.relativePath(), fake.brokenLine(), "Lỗi cú pháp ở dòng " + fake.brokenLine()));
                    continue;
                }
                for (String e : fake.endpoints()) {
                    String[] parts = e.split(" ", 2);
                    nodes.add(new IrNode(
                            NodeKeys.javaEndpoint(parts[0], parts[1]),
                            NodeKind.API_ENDPOINT,
                            e,
                            null,
                            SourceLocation.of(fake.relativePath(), 1),
                            Map.of("httpMethod", parts[0], "path", parts[1], "module", module, "moduleSource", "CONTROLLER")));
                }
            }
            return new IrFragment(id(), nodes, List.of(), List.of(), diagnostics);
        }
    }
}
