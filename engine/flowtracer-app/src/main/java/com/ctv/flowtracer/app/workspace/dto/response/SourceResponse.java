package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.ir.SourceRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một thư mục mã nguồn của Workspace.
 *
 * @param id định danh Source
 * @param name tên hiển thị
 * @param role vai trò do tool tự nhận diện
 * @param rootPath đường dẫn tương đối trong {@code SOURCE_ROOT}
 * @param excludeGlobs thư mục / file loại trừ (gồm cả luật tự thêm cho Source con)
 * @param parentSourceId Source chứa thư mục này (Source lồng nhau), nếu có
 * @param detectedStacks công nghệ đã nhận diện
 * @param createdAt thời điểm thêm
 * @param updatedAt thời điểm cập nhật gần nhất
 */
public record SourceResponse(
        UUID id,
        String name,
        SourceRole role,
        String rootPath,
        List<String> excludeGlobs,
        UUID parentSourceId,
        List<DetectedStackResponse> detectedStacks,
        Instant createdAt,
        Instant updatedAt) {

    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getName(),
                source.getRole(),
                source.getRootPath(),
                List.copyOf(source.getExcludeGlobs()),
                source.getParent() == null ? null : source.getParent().getId(),
                source.getDetectedStacks().stream().map(DetectedStackResponse::from).toList(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }
}
