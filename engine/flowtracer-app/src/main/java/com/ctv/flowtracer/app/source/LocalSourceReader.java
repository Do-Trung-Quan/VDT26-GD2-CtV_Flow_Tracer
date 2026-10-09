package com.ctv.flowtracer.app.source;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Hiện thực chỉ đọc trên hệ thống file cục bộ. Chặn path traversal ({@code ..}) và symlink trỏ ra ngoài
 * {@code source-root}.
 */
@Component
public class LocalSourceReader implements SourceReader {

    private static final int DIR_CACHE_LIMIT = 50_000;

    private final Path root;
    /** Thư mục (tuyệt đối) → đường dẫn thật có nằm trong root không. */
    private final Map<Path, Boolean> realDirInside = new ConcurrentHashMap<>();

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
    public List<Entry> listFiles(String relativeDir) {
        Path dir = resolveSafe(relativeDir);
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .map(p -> new Entry(relativize(p), p.getFileName().toString(), false, sizeOf(p)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isDirectory(String relativePath) {
        return Files.isDirectory(resolveSafe(relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public List<Entry> walkFiles(String relativeRoot, BiPredicate<String, Long> accept) {
        return walkFiles(relativeRoot, dir -> true, accept);
    }

    @Override
    public List<Entry> walkFiles(String relativeRoot, Predicate<String> acceptDirectory, BiPredicate<String, Long> accept) {
        Path base = resolveSafe(relativeRoot);
        List<Entry> result = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String inSource = base.relativize(dir).toString().replace('\\', '/');
                    return acceptDirectory.test(inSource) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String inSource = base.relativize(file).toString().replace('\\', '/');
                        if (accept.test(inSource, attrs.size())) {
                            result.add(new Entry(relativize(file), file.getFileName().toString(), false, attrs.size()));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE; // thư mục / file không đọc được: bỏ qua, không làm hỏng cả lần duyệt
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
        // Chặn ngay '..' và đường dẫn tuyệt đối — kể cả khi không thoát ra ngoài (vd source-root là gốc ổ đĩa)
        if (rel.startsWith("/") || rel.contains(":") || Arrays.asList(rel.split("/")).contains("..")) {
            throw outside(relativePath);
        }
        Path candidate = root.resolve(rel).normalize();
        if (!candidate.startsWith(root)) {
            throw outside(relativePath);
        }
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !realPathInside(candidate)) {
            throw outside(relativePath);
        }
        return candidate;
    }

    /**
     * Đường dẫn thật (sau khi giải symlink) còn nằm trong root. File thường chỉ cần kiểm tra thư mục chứa nó; kết quả
     * theo thư mục được nhớ lại vì {@code toRealPath()} rất chậm trên Windows khi đọc hàng trăm file.
     */
    private boolean realPathInside(Path candidate) {
        if (Files.isSymbolicLink(candidate)) {
            return realPath(candidate).startsWith(root);
        }
        Path dir = Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS) ? candidate : candidate.getParent();
        if (dir == null || dir.equals(root)) {
            return true;
        }
        if (realDirInside.size() > DIR_CACHE_LIMIT) {
            realDirInside.clear();
        }
        return realDirInside.computeIfAbsent(dir, d -> realPath(d).startsWith(root));
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
