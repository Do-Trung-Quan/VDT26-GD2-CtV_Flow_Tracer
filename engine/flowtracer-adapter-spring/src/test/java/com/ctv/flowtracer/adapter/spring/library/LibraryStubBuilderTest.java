package com.ctv.flowtracer.adapter.spring.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.LibraryArchive;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Lớp trong jar (không có mã nguồn) → mã rút gọn → bộ phân tích: kiểu trả về của API vẫn được mở đúng. Các lớp mẫu bên dưới
 * được trình biên dịch của bài kiểm thử biên dịch thành file {@code .class} thật, nên đây chính là định dạng jar của dự án.
 */
class LibraryStubBuilderTest {

    private static final String PKG = "com/ctv/flowtracer/adapter/spring/library/";

    private static byte[] jar(Class<?>... classes) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (Class<?> c : classes) {
                zip.putNextEntry(new ZipEntry(PKG + c.getSimpleName() + ".class"));
                try (var in = c.getResourceAsStream(c.getSimpleName() + ".class")) {
                    zip.write(in.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static SourceFile controller() {
        String code = """
                package app;
                import com.ctv.flowtracer.adapter.spring.library.SampleEnvelope;
                import com.ctv.flowtracer.adapter.spring.library.SamplePage;
                import org.springframework.web.bind.annotation.*;
                @RestController @RequestMapping("/api/items")
                public class ItemController {
                    @GetMapping public SampleEnvelope<SamplePage> list() { return null; }
                }
                """;
        return new SourceFile("src/main/java/app/ItemController.java", code, "h1");
    }

    @Test
    void rendersOnlyUsedClassesAndTheirReferencesWithJsonNamesConstraintsAndEnums() throws Exception {
        var archive = new LibraryArchive("libs/core.jar", jar(SampleEnvelope.class, SamplePage.class, SampleStatus.class, SampleUnused.class));

        List<SourceFile> stubs = new LibraryStubBuilder().build(List.of(archive), List.of(controller()));

        assertThat(stubs).extracting(SourceFile::relativePath).containsExactly(
                "libs/core.jar!/com/ctv/flowtracer/adapter/spring/library/SampleEnvelope.java",
                "libs/core.jar!/com/ctv/flowtracer/adapter/spring/library/SamplePage.java",
                "libs/core.jar!/com/ctv/flowtracer/adapter/spring/library/SampleStatus.java");
        String envelope = stubs.get(0).content();
        assertThat(envelope).contains("public class SampleEnvelope<T> {")
                .contains("private int errorCode;")
                .contains("private String message;")
                .contains("private T data;")
                .contains("@JsonProperty(\"page_size\") private int pageSize;")
                .doesNotContain("static");
        String page = stubs.get(1).content();
        assertThat(page).contains("@Size(max = 50) private String title;").contains("private SampleStatus status;")
                .contains("private List<SamplePage> children;");
        assertThat(stubs.get(2).content()).contains("public enum SampleStatus {").contains("ACTIVE, INACTIVE;");
        assertThat(stubs).allSatisfy(s -> assertThat(s.contentHash()).hasSize(64));
    }

    @Test
    void classesThatHaveRealSourceAreNotStubbed() throws Exception {
        var archive = new LibraryArchive("libs/core.jar", jar(SampleEnvelope.class));
        var real = new SourceFile("src/main/java/com/ctv/flowtracer/adapter/spring/library/SampleEnvelope.java",
                "package com.ctv.flowtracer.adapter.spring.library; public class SampleEnvelope<T> {}", "h2");

        assertThat(new LibraryStubBuilder().build(List.of(archive), List.of(controller(), real))).isEmpty();
    }

    @Test
    void brokenArchivesAndEmptyInputAreIgnored() {
        assertThat(new LibraryStubBuilder().build(List.of(new LibraryArchive("libs/bad.jar", new byte[] {1, 2, 3})), List.of(controller()))).isEmpty();
        assertThat(new LibraryStubBuilder().build(List.of(), List.of(controller()))).isEmpty();
    }

    @Test
    void theEndpointResponseIsOpenedThroughTheLibraryEnvelope() throws Exception {
        var archive = new LibraryArchive("libs/core.jar", jar(SampleEnvelope.class, SamplePage.class, SampleStatus.class));
        var adapter = new SpringStackAdapter();
        var all = new TreeMap<String, String>();
        List<SourceFile> files = new ArrayList<>();
        files.add(controller());
        files.addAll(adapter.libraryFiles(List.of(archive), List.of(controller())));
        files.forEach(f -> all.put(f.relativePath(), f.content()));
        List<String> paths = List.copyOf(all.keySet());
        var extractCtx = new ExtractContext("t", paths, p -> Optional.ofNullable(all.get(p)), null);
        List<FileFacts> facts = files.stream().filter(f -> adapter.analyzes(f.relativePath())).map(f -> adapter.extract(extractCtx, f)).toList();

        IrFragment fragment = adapter.resolve(new ResolveContext("t", paths, p -> Optional.ofNullable(all.get(p)), null, null), facts);

        IrNode envelope = type(fragment, "SampleEnvelope");
        assertThat(fields(envelope)).extracting(f -> f.get("name")).containsExactly("errorCode", "message", "page_size", "data");
        IrNode page = type(fragment, "SamplePage");
        assertThat(fields(page)).extracting(f -> f.get("name")).containsExactly("title", "status", "children");
        assertThat(fields(page).get(1).get("type").toString()).contains("enum").contains("ACTIVE").contains("INACTIVE"); // enum của thư viện được mở thành danh sách giá trị
        IrNode endpoint = fragment.nodes().stream().filter(n -> n.kind() == NodeKind.API_ENDPOINT).findFirst().orElseThrow();
        assertThat(endpoint.attrs().get("responseTypeExpr").toString()).contains("SampleEnvelope").contains("SamplePage");
    }

    @Test
    void classesWithBehaviourAndInterfacesAreNotStubbedSoLibraryCallsStayExternal() throws Exception {
        var archive = new LibraryArchive("libs/core.jar", jar(SampleHelper.class, SampleRepo.class, SampleEnvelope.class));
        var code = new SourceFile("src/main/java/app/Impl.java", """
                package app;
                import com.ctv.flowtracer.adapter.spring.library.SampleHelper;
                import com.ctv.flowtracer.adapter.spring.library.SampleRepo;
                import com.ctv.flowtracer.adapter.spring.library.SampleEnvelope;
                class Impl { SampleHelper helper; SampleRepo repo; SampleEnvelope<String> out; }
                """, "h4");

        List<SourceFile> stubs = new LibraryStubBuilder().build(List.of(archive), List.of(code));

        assertThat(stubs).extracting(SourceFile::relativePath).containsExactly("libs/core.jar!/com/ctv/flowtracer/adapter/spring/library/SampleEnvelope.java");
    }

    @Test
    void springDataPageGetsAStubEvenWithoutAnyJar() {
        var code = new SourceFile("src/main/java/app/C.java", """
                package app;
                import org.springframework.data.domain.Page;
                class C { Page<String> list() { return null; } }
                """, "h3");

        List<SourceFile> stubs = new LibraryStubBuilder().build(List.of(), List.of(code));

        assertThat(stubs).hasSize(1);
        assertThat(stubs.get(0).relativePath()).isEqualTo("(framework)!/org/springframework/data/domain/Page.java");
        assertThat(stubs.get(0).content()).contains("class Page<T>").contains("private java.util.List<T> content;").contains("totalElements");
        assertThat(new LibraryStubBuilder().build(List.of(), List.of(controller()))).isEmpty(); // không import Page thì không dựng
    }

    private static IrNode type(IrFragment f, String simpleName) {
        return f.nodes().stream()
                .filter(n -> n.kind() == NodeKind.DATA_TYPE && n.name().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có kiểu " + simpleName));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(IrNode type) {
        return (List<Map<String, Object>>) type.attrs().get("fields");
    }
}

/** Annotation giả cùng tên với annotation thật để kiểm tra việc giữ lại annotation tên JSON / ràng buộc. */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface JsonProperty {
    String value();
}

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface Size {
    int max();
}

class SampleEnvelope<T> {
    private static final String IGNORED = "x";
    private int errorCode;
    private String message;
    @JsonProperty("page_size")
    private int pageSize;
    private T data;
}

class SamplePage {
    @Size(max = 50)
    private String title;
    private SampleStatus status;
    private java.util.List<SamplePage> children;
}

enum SampleStatus {
    ACTIVE,
    INACTIVE
}

/** Không ai tham chiếu tới: không được dựng. */
class SampleUnused {
    private String x;
}

/** Có hàm nghiệp vụ: không phải lớp dữ liệu. */
class SampleHelper {
    private String name;

    public int execute(String query) {
        return query.length();
    }
}

interface SampleRepo {
    void run();
}
