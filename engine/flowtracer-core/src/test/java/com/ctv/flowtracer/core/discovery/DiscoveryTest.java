package com.ctv.flowtracer.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryTest {

    @Test
    void sha256IsStable() {
        assertThat(ContentHash.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void defaultExcludesSkipBuildFolders() {
        var filter = new FileFilter(List.of(), List.of(), 1_000_000);
        assertThat(filter.accept("web/node_modules/x/index.js", 10)).isFalse();
        assertThat(filter.accept("node_modules/x/index.js", 10)).isFalse();
        assertThat(new FileFilter(List.of("src/**/*.java"), List.of(), 1_000).accept("src/a/B.java", 10)).isTrue();
        assertThat(new FileFilter(List.of("src/**/*.java"), List.of(), 1_000).accept("src/B.java", 10)).isTrue();
        assertThat(filter.accept("backend/target/classes/A.class", 10)).isFalse();
        assertThat(filter.accept("backend/src/main/java/A.java", 10)).isTrue();
        assertThat(filter.accept("backend/src/main/java/Big.java", 2_000_000)).isFalse();
    }
}
