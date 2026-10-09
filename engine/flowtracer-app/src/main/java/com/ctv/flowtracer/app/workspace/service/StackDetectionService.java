package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.scan.adapter.AdapterRegistry;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.app.workspace.dto.response.DetectedRootResponse;
import com.ctv.flowtracer.app.workspace.dto.response.DetectionResponse;
import com.ctv.flowtracer.app.workspace.dto.response.DetectionStatus;
import com.ctv.flowtracer.app.workspace.dto.response.LegacyHintResponse;
import com.ctv.flowtracer.core.detection.FolderAccess;
import com.ctv.flowtracer.core.detection.FolderDetection;
import com.ctv.flowtracer.core.detection.FolderDetector;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Nhận diện thư mục (API A10): không có gốc dự án → {@code NOT_FOUND}; nằm trong một dự án → gợi ý thư mục gốc; nhiều
 * gốc (BE + FE chung repo) → trả đủ các gốc để tách thành nhiều Source.
 */
@Service
public class StackDetectionService {

    private static final Logger log = LoggerFactory.getLogger(StackDetectionService.class);

    /**
     * Công nghệ chưa hỗ trợ → đuôi file (đuôi đầu là đuôi chính) — chỉ dùng để gợi ý; Source LEGACY làm ở Giai đoạn 9.
     */
    static final Map<String, List<String>> HINT_LANGUAGES = Map.of("PHP", List.of("php", "tpl"));

    /** File đặc trưng lớn hơn ngưỡng này thì không đọc. */
    private static final long MAX_MARKER_BYTES = 1024 * 1024;

    private final SourceReader reader;
    private final AdapterRegistry adapters;
    private final FolderDetector detector = new FolderDetector();

    public StackDetectionService(SourceReader reader, AdapterRegistry adapters) {
        this.reader = reader;
        this.adapters = adapters;
    }

    public FolderDetection detect(String rootPath) {
        String path = FolderDetector.normalize(rootPath);
        requireDirectory(reader, path);
        return detector.detect(path, adapters.all(), folderAccess(), HINT_LANGUAGES);
    }

    public DetectionResponse detectResponse(String rootPath) {
        return toResponse(detect(rootPath));
    }

    static DetectionResponse toResponse(FolderDetection d) {
        var warnings = new ArrayList<String>();
        d.failedAdapters().forEach(id -> warnings.add("Không chạy được bộ nhận diện '" + id
                + "', có thể bỏ sót dự án loại này. Kiểm tra Node.js và đã build sidecar (npm --prefix analyzer-node run build)."));
        if (d.truncated()) {
            warnings.add("Thư mục quá lớn, chỉ duyệt được một phần. Hãy chọn thư mục cụ thể hơn.");
        }
        return new DetectionResponse(
                DetectionStatus.valueOf(d.status().name()),
                d.roots().stream().map(DetectedRootResponse::from).toList(),
                d.suggestedRoot() == null ? null : DetectedRootResponse.from(d.suggestedRoot()),
                d.legacyHints().stream().map(LegacyHintResponse::from).toList(),
                List.copyOf(warnings));
    }

    /** Thư mục phải tồn tại bên trong {@code SOURCE_ROOT} (ngoài thì {@code SourceReader} báo 400). */
    static void requireDirectory(SourceReader reader, String path) {
        if (!reader.isDirectory(path)) {
            throw new AppException(ErrorCode.FOLDER_NOT_FOUND, "Không tìm thấy thư mục: " + path, Map.of("path", path));
        }
    }

    private FolderAccess folderAccess() {
        return new FolderAccess(
                dir -> quietly(() -> reader.listDirs(dir).stream().map(SourceReader.Entry::name).toList()),
                dir -> quietly(() -> reader.listFiles(dir).stream().map(SourceReader.Entry::name).toList()),
                this::readMarker);
    }

    private Optional<String> readMarker(String path) {
        try {
            if (!reader.exists(path) || reader.isDirectory(path)) {
                return Optional.empty();
            }
            byte[] bytes = reader.readBytes(path);
            return bytes.length > MAX_MARKER_BYTES
                    ? Optional.empty()
                    : Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (UncheckedIOException | AppException e) {
            return Optional.empty();
        }
    }

    /** Thư mục không đọc được (vd thư mục hệ thống) thì coi như rỗng. */
    private static List<String> quietly(Supplier<List<String>> listing) {
        try {
            return listing.get();
        } catch (UncheckedIOException | AppException e) {
            log.debug("Bỏ qua thư mục không đọc được: {}", e.getMessage());
            return List.of();
        }
    }
}
