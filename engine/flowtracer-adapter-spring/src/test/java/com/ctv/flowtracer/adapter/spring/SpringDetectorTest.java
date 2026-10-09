package com.ctv.flowtracer.adapter.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpringDetectorTest {

    private final SpringDetector detector = new SpringDetector();

    private DetectResult detect(Map<String, String> files) {
        List<String> top = files.keySet().stream().filter(p -> !p.contains("/")).sorted().toList();
        return detector.detect(new DetectContext(top, p -> Optional.ofNullable(files.get(p))));
    }

    @Test
    void pomWithStarterParent() {
        var r = detect(Map.of("pom.xml", """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.3</version>
                  </parent>
                  <artifactId>shop-api</artifactId>
                </project>
                """));
        assertThat(r.score()).isEqualTo(SpringDetector.SCORE_DIRECT);
        assertThat(r.stackName()).isEqualTo("Spring Boot");
        assertThat(r.version()).isEqualTo("3.3.3");
    }

    @Test
    void pomWithBomAndProperty() {
        var r = detect(Map.of("pom.xml", """
                <project>
                  <properties><boot.version>3.2.1</boot.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-dependencies</artifactId>
                      <version>${boot.version}</version>
                    </dependency>
                  </dependencies></dependencyManagement>
                </project>
                """));
        assertThat(r.version()).isEqualTo("3.2.1");
    }

    @Test
    void multiModuleRootWithoutBootMatchesThroughModule() {
        var r = detect(Map.of(
                "pom.xml", "<project><modules><module>shop-core</module><module>shop-api</module></modules></project>",
                "shop-core/pom.xml", "<project><artifactId>shop-core</artifactId></project>",
                "shop-api/pom.xml", "<project><dependencies><dependency>"
                        + "<artifactId>spring-boot-starter-web</artifactId></dependency></dependencies></project>"));
        assertThat(r.score()).isEqualTo(SpringDetector.SCORE_VIA_MODULE);
        assertThat(r.reason()).contains("shop-api/pom.xml");
    }

    @Test
    void plainJavaLibraryIsNotSpring() {
        var r = detect(Map.of("pom.xml", "<project><!-- spring-boot later --><artifactId>lib</artifactId></project>"));
        assertThat(r.matched()).isFalse();
    }

    @Test
    void gradlePluginVersion() {
        var r = detect(Map.of("build.gradle.kts", """
                plugins {
                    id("org.springframework.boot") version "3.4.0"
                }
                """));
        assertThat(r.score()).isEqualTo(SpringDetector.SCORE_DIRECT);
        assertThat(r.version()).isEqualTo("3.4.0");
    }

    @Test
    void gradleMultiProjectThroughSettings() {
        var r = detect(Map.of(
                "settings.gradle", "rootProject.name = 'shop'\ninclude 'shop-api', ':shop-core'\n",
                "shop-api/build.gradle", "plugins { id 'org.springframework.boot' version '3.3.5' }"));
        assertThat(r.score()).isEqualTo(SpringDetector.SCORE_VIA_MODULE);
        assertThat(r.version()).isEqualTo("3.3.5");
    }

    @Test
    void moduleOutsideCandidateIsIgnored() {
        assertThat(SpringDetector.modules("<modules><module>../other</module><module>api</module></modules>"))
                .containsExactly("api");
    }
}
