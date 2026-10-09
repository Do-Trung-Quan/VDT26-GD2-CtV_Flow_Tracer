package com.ctv.flowtracer.core.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.core.detection.FolderDetection.ProjectRoot;
import com.ctv.flowtracer.core.detection.FolderDetection.Status;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Mini fixture: cây thư mục giả trong bộ nhớ, đường dẫn tương đối SOURCE_ROOT. */
class FolderDetectorTest {

    private static final String BOOT_POM = "<artifactId>spring-boot-starter-parent</artifactId>";
    private static final String AGGREGATOR_POM = "<modules><module>shop-api</module></modules>";

    private final FolderDetector detector = new FolderDetector();
    private final Map<String, String> files = new TreeMap<>();

    @Test
    void unrelatedFolderIsNotFound() {
        files.put("notes/todo.txt", "");
        files.put("notes/img/a.png", "");

        var r = detect("notes");

        assertThat(r.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(r.roots()).isEmpty();
        assertThat(r.suggestedRoot()).isNull();
    }

    @Test
    void folderWithBackendAndFrontendIsSplitIntoTwoRoots() {
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/backend/src/main/java/App.java", "");
        files.put("shop/web/angular.json", "{}");
        files.put("shop/README.md", "");

        var r = detect("shop");

        assertThat(r.status()).isEqualTo(Status.VALID);
        assertThat(r.roots()).extracting(ProjectRoot::path, ProjectRoot::role)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("shop/backend", SourceRole.BACKEND),
                        org.assertj.core.groups.Tuple.tuple("shop/web", SourceRole.FRONTEND));
    }

    @Test
    void subFolderOfBackendSuggestsBackendRoot() {
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/backend/src/main/java/com/shop/App.java", "");

        var r = detect("shop/backend/src/main/java");

        assertThat(r.status()).isEqualTo(Status.INSIDE_PROJECT);
        assertThat(r.suggestedRoot().path()).isEqualTo("shop/backend");
        assertThat(r.roots()).isEmpty();
    }

    @Test
    void mavenMultiModuleKeepsOnlyTopPom() {
        files.put("shop/pom.xml", AGGREGATOR_POM);
        files.put("shop/shop-api/pom.xml", BOOT_POM);
        files.put("shop/shop-core/pom.xml", "<artifactId>lib</artifactId>");

        var r = detect("shop");

        assertThat(r.status()).isEqualTo(Status.VALID);
        assertThat(r.roots()).extracting(ProjectRoot::path).containsExactly("shop");
    }

    @Test
    void selectingModuleOfMultiModuleProjectSuggestsTopPom() {
        files.put("shop/pom.xml", AGGREGATOR_POM);
        files.put("shop/shop-api/pom.xml", BOOT_POM);

        var r = detect("shop/shop-api");

        assertThat(r.status()).isEqualTo(Status.INSIDE_PROJECT);
        assertThat(r.suggestedRoot().path()).isEqualTo("shop");
    }

    @Test
    void frontendInsideBackendRepoIsKeptAsSecondRoot() {
        files.put("shop/pom.xml", BOOT_POM);
        files.put("shop/frontend/angular.json", "{}");

        var r = detect("shop");

        assertThat(r.roots()).extracting(ProjectRoot::path).containsExactly("shop", "shop/frontend");
    }

    @Test
    void frontendFolderInsideBackendRepoIsValidOnItsOwn() {
        files.put("shop/pom.xml", BOOT_POM);
        files.put("shop/frontend/angular.json", "{}");

        var r = detect("shop/frontend");

        assertThat(r.status()).isEqualTo(Status.VALID);
        assertThat(r.roots()).extracting(ProjectRoot::adapterId).containsExactly("fe");
    }

    @Test
    void rootsDeeperThanMaxDepthAreNotFound() {
        files.put("a/b/c/d/e/f/pom.xml", BOOT_POM);
        assertThat(detect("a").status()).isEqualTo(Status.NOT_FOUND);

        files.clear();
        files.put("a/b/c/d/e/pom.xml", BOOT_POM);
        assertThat(detect("a").roots()).extracting(ProjectRoot::path).containsExactly("a/b/c/d/e");
    }

    @Test
    void skippedFoldersAreIgnored() {
        files.put("shop/node_modules/lib/angular.json", "{}");
        files.put("shop/.cache/angular.json", "{}");
        files.put("shop/target/classes/pom.xml", BOOT_POM);

        assertThat(detect("shop").status()).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    void phpFolderInsideBackendDocsIsOnlyHinted() {
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/backend/src/App.java", "");
        IntStream.range(0, 12).forEach(i -> files.put("shop/backend/docs/old-shop/page" + i + ".php", ""));
        files.put("shop/backend/docs/old-shop/readme.txt", "");

        var r = detect("shop");

        assertThat(r.status()).isEqualTo(Status.VALID);
        assertThat(r.roots()).extracting(ProjectRoot::path).containsExactly("shop/backend");
        assertThat(r.legacyHints()).singleElement().satisfies(h -> {
            assertThat(h.path()).isEqualTo("shop/backend/docs/old-shop");
            assertThat(h.language()).isEqualTo("PHP");
            assertThat(h.fileCount()).isEqualTo(12);
        });
    }

    @Test
    void deepPhpWithTemplatesIsHinted() {
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/backend/src/App.java", "");
        String app = "shop/backend/docs/php_project/old-shop";
        files.put(app + "/index.php", "");
        IntStream.range(0, 12).forEach(i -> files.put(app + "/modules/m" + (i % 4) + "/action" + i + ".php", ""));
        IntStream.range(0, 20).forEach(i -> files.put(app + "/modules/m" + (i % 4) + "/view" + i + ".tpl", ""));
        IntStream.range(0, 3).forEach(i -> files.put("shop/backend/docs/modules/m" + i + "/api.md", ""));

        var r = detect("shop");

        assertThat(r.truncated()).isFalse();
        assertThat(r.legacyHints()).singleElement().satisfies(h -> {
            assertThat(h.path()).isEqualTo(app);
            assertThat(h.fileCount()).isEqualTo(33);
        });
    }

    @Test
    void templatesWithoutEnoughPhpAreNotHinted() {
        IntStream.range(0, 30).forEach(i -> files.put("tools/presets/p" + i + ".tpl", ""));

        assertThat(detect("tools").legacyHints()).isEmpty();
    }

    @Test
    void phpOnlyFolderIsNotFoundButHinted() {
        files.put("old-shop/index.php", "");
        IntStream.range(0, 10).forEach(i -> files.put("old-shop/modules/m" + i + ".php", ""));

        var r = detect("old-shop");

        assertThat(r.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(r.legacyHints()).extracting(FolderDetection.LegacyHint::path).containsExactly("old-shop");
    }

    @Test
    void failingAdapterIsReportedWithoutBreakingOthers() {
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/web/angular.json", "{}");

        var r = detector.detect("shop", List.of(be(), failing()), access(), Map.of());

        assertThat(r.roots()).extracting(ProjectRoot::path).containsExactly("shop/backend");
        assertThat(r.failedAdapters()).containsExactly("fe");
    }

    @Test
    void resultIsDeterministic() {
        files.put("shop/web/angular.json", "{}");
        files.put("shop/backend/pom.xml", BOOT_POM);
        files.put("shop/admin/angular.json", "{}");

        assertThat(detect("shop")).isEqualTo(detect("shop"));
        assertThat(detect("shop").roots()).extracting(ProjectRoot::path)
                .containsExactly("shop/admin", "shop/backend", "shop/web");
    }

    @Test
    void pathHelpers() {
        assertThat(FolderDetector.normalize("\\shop\\web\\")).isEqualTo("shop/web");
        assertThat(FolderDetector.normalize(".")).isEmpty();
        assertThat(FolderDetector.isStrictlyInside("shop/web", "shop")).isTrue();
        assertThat(FolderDetector.isStrictlyInside("shop-web", "shop")).isFalse();
        assertThat(FolderDetector.isStrictlyInside("shop", "shop")).isFalse();
        assertThat(FolderDetector.isStrictlyInside("shop", "")).isTrue();
    }

    // ---------------- cây thư mục giả và adapter giả ----------------

    private FolderDetection detect(String dir) {
        return detector.detect(dir, List.of(be(), fe()), access(), Map.of("PHP", List.of("php", "tpl")));
    }

    private FolderAccess access() {
        return new FolderAccess(
                dir -> children(dir, true), dir -> children(dir, false), p -> Optional.ofNullable(files.get(p)));
    }

    private List<String> children(String dir, boolean directories) {
        String prefix = dir.isEmpty() ? "" : dir + "/";
        var names = new java.util.TreeSet<String>();
        for (String path : files.keySet()) {
            if (path.startsWith(prefix)) {
                String rest = path.substring(prefix.length());
                int slash = rest.indexOf('/');
                if (directories && slash > 0) {
                    names.add(rest.substring(0, slash));
                } else if (!directories && slash < 0) {
                    names.add(rest);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** "be": pom.xml nhắc tới spring-boot, hoặc là pom gộp có module như vậy. */
    private static StackAdapter be() {
        return new FakeAdapter("be", SourceRole.BACKEND, "pom.xml", ctx -> {
            String pom = ctx.loader().load("pom.xml").orElse("");
            boolean boot = pom.contains("spring-boot")
                    || (pom.contains("<module>shop-api</module>")
                            && ctx.loader().load("shop-api/pom.xml").orElse("").contains("spring-boot"));
            return boot ? new DetectResult(0.9, "Spring Boot", null, "pom") : DetectResult.none();
        });
    }

    private static StackAdapter fe() {
        return new FakeAdapter("fe", SourceRole.FRONTEND, "angular.json", ctx -> new DetectResult(0.9, "Angular", null, "angular.json"));
    }

    private static StackAdapter failing() {
        return new FakeAdapter("fe", SourceRole.FRONTEND, "angular.json", ctx -> {
            throw new IllegalStateException("sidecar không chạy");
        });
    }

    private record FakeAdapter(String id, SourceRole role, String marker, java.util.function.Function<DetectContext, DetectResult> fn)
            implements StackAdapter {
        @Override
        public Set<SourceRole> roles() {
            return Set.of(role);
        }

        @Override
        public List<String> rootMarkers() {
            return List.of(marker);
        }

        @Override
        public DetectResult detect(DetectContext ctx) {
            return fn.apply(ctx);
        }

        @Override
        public Class<? extends FileFacts> factsType() {
            return FileFacts.class;
        }

        @Override
        public FileFacts extract(ExtractContext ctx, SourceFile file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IrFragment resolve(ResolveContext ctx, List<FileFacts> facts) {
            throw new UnsupportedOperationException();
        }
    }
}
