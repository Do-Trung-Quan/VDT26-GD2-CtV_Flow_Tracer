package com.ctv.flowtracer.app.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSourceReaderTest {

    @TempDir
    Path root;

    private LocalSourceReader reader() {
        return new LocalSourceReader(new FlowTracerProperties(root.toString(), "data", null, null, null));
    }

    @Test
    void listsDirectoriesAndFilesInsideRoot() throws Exception {
        Files.createDirectories(root.resolve("shop/web"));
        Files.writeString(root.resolve("shop/pom.xml"), "<project/>");

        var reader = reader();

        assertThat(reader.listDirs("shop")).extracting(SourceReader.Entry::relativePath).containsExactly("shop/web");
        assertThat(reader.listFiles("shop")).extracting(SourceReader.Entry::name).containsExactly("pom.xml");
        assertThat(reader.isDirectory("shop/web")).isTrue();
        assertThat(reader.isDirectory("shop/pom.xml")).isFalse();
    }

    @Test
    void rejectsParentSegmentsAndAbsolutePathsEvenWhenTheyStayInside() throws Exception {
        Files.createDirectories(root.resolve("shop"));
        var reader = reader();

        for (String bad : new String[] {"..", "../..", "shop/../..", "shop/..", "/etc", "C:/Windows", "shop\\..\\.."}) {
            assertThatThrownBy(() -> reader.isDirectory(bad))
                    .as(bad)
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.PATH_OUTSIDE_SOURCE_ROOT));
        }
        assertThat(reader.exists("../secret.txt")).isFalse();
    }
}
