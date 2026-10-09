package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.app.workspace.dto.response.FolderBrowseResponse;
import com.ctv.flowtracer.app.workspace.dto.response.FolderEntryResponse;
import com.ctv.flowtracer.core.detection.FolderDetector;
import com.ctv.flowtracer.core.discovery.DefaultExcludes;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Duyệt thư mục trong {@code SOURCE_ROOT} (API A6): chỉ trả thư mục, không trả nội dung file. Đường dẫn ra ngoài
 * {@code SOURCE_ROOT} (kể cả qua symlink) bị {@code SourceReader} chặn với lỗi 400.
 */
@Service
public class FolderBrowseService {

    private final SourceReader reader;

    public FolderBrowseService(SourceReader reader) {
        this.reader = reader;
    }

    public FolderBrowseResponse browse(String path) {
        String dir = FolderDetector.normalize(path);
        StackDetectionService.requireDirectory(reader, dir);
        List<FolderEntryResponse> folders;
        try {
            folders = reader.listDirs(dir).stream()
                    .filter(e -> isBrowsable(e.name()))
                    .map(e -> new FolderEntryResponse(e.name(), e.relativePath(), reader.exists(e.relativePath() + "/.git")))
                    .toList();
        } catch (UncheckedIOException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không đọc được thư mục: " + dir, Map.of("path", dir));
        }
        return new FolderBrowseResponse(dir, parentOf(dir), folders);
    }

    /** Ẩn thư mục build/thư viện/ẩn và thư mục hệ thống của Windows. */
    static boolean isBrowsable(String name) {
        return !DefaultExcludes.isSkippedDir(name) && !name.startsWith("$") && !name.equals("System Volume Information");
    }

    private static String parentOf(String dir) {
        if (dir.isEmpty()) {
            return null;
        }
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }
}
