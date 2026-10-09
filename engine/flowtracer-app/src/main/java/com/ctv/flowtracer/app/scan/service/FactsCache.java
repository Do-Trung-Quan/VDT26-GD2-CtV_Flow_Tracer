package com.ctv.flowtracer.app.scan.service;

import com.ctv.flowtracer.app.config.FlowTracerProperties;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache dữ kiện pha 1 trên ổ đĩa, theo mã băm nội dung file: {@code <data-dir>/facts/<workspaceId>/<adapter>-v<phiên bản>/<hash>.json}.
 * File không đổi thì lần quét sau dùng lại, không đọc lại. Ghi trong thư mục dữ liệu riêng của tool — KHÔNG ghi vào mã
 * nguồn người dùng. Lỗi đọc/ghi cache chỉ làm mất cache, không làm hỏng lần quét.
 */
@Component
public class FactsCache {

    private static final Logger log = LoggerFactory.getLogger(FactsCache.class);
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final Path root;
    private final JsonMapper json;

    public FactsCache(FlowTracerProperties properties, JsonMapper json) {
        this.root = Path.of(properties.dataDir()).toAbsolutePath().normalize().resolve("facts");
        this.json = json;
    }

    public Optional<FileFacts> get(UUID workspaceId, StackAdapter adapter, String contentHash) {
        Path file = fileFor(workspaceId, adapter, contentHash);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(file.toFile(), adapter.factsType()));
        } catch (RuntimeException e) {
            log.warn("Bỏ qua cache dữ kiện hỏng {}: {}", file.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID workspaceId, StackAdapter adapter, String contentHash, FileFacts facts) {
        Path file = fileFor(workspaceId, adapter, contentHash);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), contentHash, ".tmp");
            json.writeValue(tmp.toFile(), facts);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Không ghi được cache dữ kiện {}: {}", file.getFileName(), e.getMessage());
        }
    }

    /** Xóa toàn bộ cache dữ kiện của một Workspace (khi xóa Workspace). */
    public void evictWorkspace(UUID workspaceId) {
        Path dir = root.resolve(workspaceId.toString());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FactsCache::deleteQuietly);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileFor(UUID workspaceId, StackAdapter adapter, String contentHash) {
        if (!SHA256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("Mã băm không hợp lệ: " + contentHash);
        }
        return root.resolve(workspaceId.toString())
                .resolve(adapter.id() + "-v" + adapter.factsVersion())
                .resolve(contentHash + ".json");
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Không xóa được {}: {}", path, e.getMessage());
        }
    }
}
