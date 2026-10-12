package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Chạy adapter Spring trên một thư mục fixture (như Engine làm với một Source), dùng chung cho các test golden. */
final class FixtureRunner {

    static final Path FIXTURES = Path.of("..", "..", "fixtures");

    private final Path root;
    private final SpringStackAdapter adapter = new SpringStackAdapter();

    FixtureRunner(String fixtureName) {
        this.root = FIXTURES.resolve(fixtureName);
    }

    IrFragment analyze() {
        List<String> allPaths;
        try (Stream<Path> files = Files.walk(root)) {
            allPaths = files.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith("target/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var extractCtx = new ExtractContext("fixture", allPaths, this::load, null);
        List<FileFacts> facts = allPaths.stream()
                .filter(adapter::analyzes)
                .map(p -> adapter.extract(extractCtx, sourceFile(p)))
                .toList();
        return adapter.resolve(new ResolveContext("fixture", allPaths, this::load, null, null), facts);
    }

    private Optional<String> load(String relativePath) {
        try {
            return Optional.of(Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private SourceFile sourceFile(String relativePath) {
        String content = load(relativePath).orElseThrow();
        return new SourceFile(relativePath, content, sha256(content));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
