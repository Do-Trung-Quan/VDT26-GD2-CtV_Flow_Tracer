package com.ctv.flowtracer.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** Web UI: file có thật → trả file; route SPA → index.html; API / file thiếu → 404. */
class SpaFallbackResolverTest {

    @TempDir
    Path dir;

    private final SpaFallbackResolver resolver = new SpaFallbackResolver();
    private Resource location;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        Files.writeString(dir.resolve("main-abc.js"), "//js");
        location = new FileSystemResource(dir.toString() + "/");
    }

    @Test
    void existingFileIsServedAsIs() throws Exception {
        assertThat(resolver.resolveIn("main-abc.js", location).getFilename()).isEqualTo("main-abc.js");
    }

    @Test
    void screenRoutesFallBackToIndexForDeepLinksAndReload() throws Exception {
        assertThat(resolver.resolveIn("w/3f0c9f6e-6a1c-4d36-9b53", location).getFilename()).isEqualTo("index.html");
        assertThat(resolver.resolveIn("settings/ai", location).getFilename()).isEqualTo("index.html");
    }

    @Test
    void apiPathsAndMissingFilesAreNotAnsweredWithIndex() throws Exception {
        assertThat(resolver.resolveIn("api/unknown", location)).isNull();
        assertThat(resolver.resolveIn("v3/api-docs", location)).isNull();
        assertThat(resolver.resolveIn("chunk-missing.js", location)).isNull();
    }

    @Test
    void noIndexMeansNothingToFallBackTo() throws Exception {
        Files.delete(dir.resolve("index.html"));
        assertThat(resolver.resolveIn("w/abc", location)).isNull();
    }
}
