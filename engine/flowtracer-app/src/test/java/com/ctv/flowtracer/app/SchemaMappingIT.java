package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.scan.repository.ScanRunRepository;
import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.workspace.repository.SourceRepository;
import com.ctv.flowtracer.app.workspace.repository.WorkspaceRepository;
import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import com.ctv.flowtracer.app.workspace.entity.WorkspaceSettings;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.SourceRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Kiểm tra migration Flyway + ánh xạ entity (ddl-auto=validate) + đọc/ghi JSONB và enum PostgreSQL trên CSDL thật.
 * Tự bỏ qua khi máy không có Docker.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {"flowtracer.source-root=.", "flowtracer.data-dir=target/test-data"})
class SchemaMappingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    WorkspaceRepository workspaces;

    @Autowired
    SourceRepository sources;

    @Autowired
    ScanRunRepository scanRuns;

    @Autowired
    TransactionTemplate tx;

    @Test
    void persistsAndReadsBackJsonbAndEnums() {
        var rule = new LinkRule(LinkRule.Type.REWRITE, "^/gateway", "", LinkRule.Origin.MANUAL, "thử");
        var ws = new Workspace("Shop", "Workspace thử");
        ws.setSettings(new WorkspaceSettings(List.of(rule), List.of(), List.of(), 10, List.of("docs/**/*.md")));
        var saved = workspaces.save(ws);

        var src = new Source(saved, "Backend", SourceRole.BACKEND, "shop/backend");
        src.setExcludeGlobs(List.of("docs/legacy/**"));
        sources.save(src);

        var run = new ScanRun(saved.getId(), ScanTrigger.IMPORT, Instant.now());
        run.transitionTo(ScanStatus.RUNNING, Instant.now());
        run.setStats(Map.of("files", 3));
        scanRuns.save(run);

        tx.executeWithoutResult(s -> {
            var loaded = workspaces.findByName("Shop").orElseThrow();
            assertThat(loaded.getSettings().manualLinkRules()).containsExactly(rule);
            assertThat(loaded.getSettings().defaultMaxDepth()).isEqualTo(10);
            var loadedSources = sources.findByWorkspaceIdOrderByName(loaded.getId());
            assertThat(loadedSources).singleElement().satisfies(x -> {
                assertThat(x.getRole()).isEqualTo(SourceRole.BACKEND);
                assertThat(x.getExcludeGlobs()).containsExactly("docs/legacy/**");
            });
        });
        assertThat(scanRuns.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(ScanStatus.RUNNING);
    }
}
