package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.core.detection.FolderDetection;

/**
 * Thư mục có nhiều mã của công nghệ chưa hỗ trợ (vd PHP hệ thống cũ) — chỉ để báo, tool không tự thêm.
 *
 * @param path đường dẫn tương đối trong {@code SOURCE_ROOT}
 * @param language tên ngôn ngữ, vd "PHP"
 * @param fileCount số file đếm được trong giới hạn duyệt (số thật có thể lớn hơn)
 */
public record LegacyHintResponse(
        String path,
        String language,
        int fileCount) {

    public static LegacyHintResponse from(FolderDetection.LegacyHint hint) {
        return new LegacyHintResponse(hint.path(), hint.language(), hint.fileCount());
    }
}
