package com.ctv.flowtracer.core.detection;

import com.ctv.flowtracer.ir.SourceRole;
import java.util.List;

/**
 * Kết quả nhận diện một thư mục người dùng chọn (Nhóm 1 của feature_list).
 *
 * @param status kết luận
 * @param roots các gốc dự án tìm thấy bên trong thư mục (rỗng nếu không có)
 * @param suggestedRoot gốc dự án nên dùng thay thế khi {@code status = INSIDE_PROJECT}
 * @param legacyHints thư mục có nhiều mã của công nghệ chưa hỗ trợ (vd PHP) — chỉ để báo, không tự thêm
 * @param failedAdapters adapter bị lỗi khi nhận diện (vd sidecar không chạy được)
 * @param truncated thư mục quá lớn, chỉ duyệt được một phần
 */
public record FolderDetection(
        Status status,
        List<ProjectRoot> roots,
        ProjectRoot suggestedRoot,
        List<LegacyHint> legacyHints,
        List<String> failedAdapters,
        boolean truncated) {

    public FolderDetection {
        roots = List.copyOf(roots);
        legacyHints = List.copyOf(legacyHints);
        failedAdapters = List.copyOf(failedAdapters);
    }

    public enum Status {
        /** Có ít nhất một gốc dự án bên trong. */
        VALID,
        /** Thư mục nằm bên trong một dự án — nên dùng {@code suggestedRoot}. */
        INSIDE_PROJECT,
        /** Không tìm thấy dự án nào được hỗ trợ. */
        NOT_FOUND
    }

    /**
     * Một gốc dự án.
     *
     * @param path đường dẫn tương đối so với {@code SOURCE_ROOT}
     * @param reason dấu hiệu đã dùng để nhận diện
     */
    public record ProjectRoot(
            String path, String adapterId, SourceRole role, String stackName, String version, double score, String reason) {}

    /**
     * Thư mục có nhiều file của một ngôn ngữ chưa hỗ trợ.
     *
     * @param path đường dẫn tương đối so với {@code SOURCE_ROOT}
     * @param language tên ngôn ngữ, vd "PHP"
     * @param fileCount số file đếm được (trong giới hạn độ sâu duyệt — số thật có thể lớn hơn)
     */
    public record LegacyHint(String path, String language, int fileCount) {}
}
