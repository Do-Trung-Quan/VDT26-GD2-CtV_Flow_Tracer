package com.ctv.flowtracer.adapter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationConfigReaderTest {

    private final ApplicationConfigReader reader = new ApplicationConfigReader();

    private ApplicationConfigReader.Result read(Map<String, String> files) {
        return reader.read(new ResolveContext(
                "s", files.keySet().stream().sorted().toList(), p -> Optional.ofNullable(files.get(p)), null, null));
    }

    @Test
    void nestedYamlAndPerModuleConfig() {
        var result = read(Map.of(
                "shop-api/src/main/resources/application.yml", """
                        server:
                          port: 8080
                          servlet:
                            context-path: "/shop/"   # chú thích
                        spring:
                          jackson:
                            property-naming-strategy: SNAKE_CASE
                        """,
                "src/main/resources/application.properties", "server.servlet.context-path=/root\n",
                "shop-api/src/main/resources/application-dev.yml", "server.servlet.context-path: /dev\n"));

        assertThat(result.forFile("shop-api/src/main/java/A.java")).hasValueSatisfying(c -> {
            assertThat(c.contextPath()).isEqualTo("/shop");
            assertThat(c.jsonNamingStrategy()).isEqualTo("SNAKE_CASE");
        });
        assertThat(result.forFile("src/main/java/B.java")).hasValueSatisfying(c -> assertThat(c.contextPath()).isEqualTo("/root"));
        assertThat(result.configs()).noneMatch(c -> c.configFile().contains("-dev"));
    }

    @Test
    void placeholderWithDefaultIsUsedWithoutDefaultIsReported() {
        assertThat(read(Map.of("src/main/resources/application.yml", "server.servlet.context-path: ${CTX:/api}\n"))
                        .configs().getFirst().contextPath())
                .isEqualTo("/api");

        var unresolved = read(Map.of("src/main/resources/application.yml", "server:\n  servlet:\n    context-path: ${CTX}\n"));
        assertThat(unresolved.configs().getFirst().contextPath()).isEmpty();
        assertThat(unresolved.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.severity()).isEqualTo(Diagnostic.Severity.WARNING);
            assertThat(d.message()).contains("${CTX}");
        });
    }

    @Test
    void otherKeysAreNeverKept() {
        var result = read(Map.of("src/main/resources/application.yml", "spring:\n  datasource:\n    password: bi-mat\n"));
        assertThat(result.configs()).singleElement().satisfies(c -> {
            assertThat(c.contextPath()).isEmpty();
            assertThat(c.toString()).doesNotContain("bi-mat");
        });
        assertThat(List.of(ApplicationConfigReader.normalizeContextPath("/"), ApplicationConfigReader.normalizeContextPath("api/")))
                .containsExactly("", "/api");
    }
}
