package com.ctv.flowtracer.app.source;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Hiện thực chỉ đọc trên hệ thống file cục bộ. Chặn path traversal ({@code ..}) và symlink trỏ ra ngoài
 * {@code source-root}.
 */
@Component
public class LocalSourceReader implements SourceReader {

    private final Path root;

    public LocalSourceReader(FlowTracerProperties properties) {
        try {
            this.root = Path.of(properties.sourceRoot()).toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("source-root không tồn tại: " + properties.sourceRoot(), e);
        }
    }

    @Override
    public List<Entry> listDirs(String relativeDir) {
        Path dir = resolveSafe(relativeDir);
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .map(p -> new Entry(relativize(p), p.getFileName().toString(), true, 0))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Entry> walkFiles(String relativeRoot, BiPredicate<String, Long> accept) {
        Path base = resolveSafe(relativeRoot);
        List<Entry> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base)) {
            paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(p -> {
                String inSource = base.relativize(p).toString().replace('\\', '/');
                long size = sizeOf(p);
                if (accept.test(inSource, size)) {
                    result.add(new Entry(relativize(p), p.getFileName().toString(), false, size));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort(Comparator.comparing(Entry::relativePath));
        return result;
    }

    @Override
    public byte[] readBytes(String relativePath) {
        try {
            return Files.readAllBytes(resolveSafe(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String readString(String relativePath) {
        return new String(readBytes(relativePath), StandardCharsets.UTF_8);
    }

    @Override
    public List<String> readLines(String relativePath, int fromLine, int toLine) {
        try (Stream<String> lines = Files.lines(resolveSafe(relativePath), StandardCharsets.UTF_8)) {
            int from = Math.max(1, fromLine);
            return lines.skip(from - 1L).limit(Math.max(0, toLine - from + 1L)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return Files.exists(resolveSafe(relativePath));
        } catch (AppException e) {
            return false;
        }
    }

    /** Chuẩn hóa và kiểm tra đường dẫn nằm trong root (kể cả sau khi giải symlink). */
    Path resolveSafe(String relativePath) {
        String rel = relativePath == null ? "" : relativePath.replace('\\', '/');
        Path candidate = root.resolve(rel).normalize();
        if (!candidate.startsWith(root)) {
            throw outside(relativePath);
        }
        try {
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root)) {
                throw outside(relativePath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return candidate;
    }

    private String relativize(Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static AppException outside(String path) {
        return new AppException(ErrorCode.PATH_OUTSIDE_SOURCE_ROOT, "Đường dẫn nằm ngoài thư mục mã nguồn được phép: " + path);
    }
}
