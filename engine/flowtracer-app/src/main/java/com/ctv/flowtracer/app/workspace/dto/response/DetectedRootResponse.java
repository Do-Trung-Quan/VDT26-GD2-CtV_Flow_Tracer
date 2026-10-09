package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.core.detection.FolderDetection;
import com.ctv.flowtracer.ir.SourceRole;

/**
 * Một gốc dự án tìm thấy khi nhận diện thư mục.
 *
 * @param path đường dẫn tương đối trong {@code SOURCE_ROOT}
 * @param role vai trò (Backend / Frontend)
 * @param adapter bộ phân tích sẽ dùng, vd {@code spring}, {@code angular}
 * @param stackName tên công nghệ
 * @param version phiên bản đọc được (có thể null)
 * @param evidence dấu hiệu đã dùng để nhận diện, vd "pom.xml có spring-boot 3.3.3"
 */
public record DetectedRootResponse(
        String path,
        SourceRole role,
        String adapter,
        String stackName,
        String version,
        String evidence) {

    public static DetectedRootResponse from(FolderDetection.ProjectRoot root) {
        return new DetectedRootResponse(
                root.path(),
                root.role(),
                root.adapterId(),
                root.stackName(),
                root.version(),
                root.reason());
    }
}
