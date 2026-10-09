package com.ctv.flowtracer.app.workspace.model;

import com.ctv.flowtracer.ir.SourceRole;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin một Source cần cho lần quét — {@code WorkspaceService} cấp cho package {@code scan} (không lộ entity).
 *
 * @param sourceId id Source
 * @param name tên hiển thị
 * @param role vai trò
 * @param rootPath thư mục gốc, tương đối trong {@code SOURCE_ROOT}
 * @param adapterId bộ phân tích đã nhận diện (null nếu chưa nhận diện được)
 * @param includeGlobs chỉ lấy file khớp (rỗng = lấy tất cả)
 * @param excludeGlobs luật loại trừ thủ công (gồm cả luật tự thêm cho Source con)
 */
public record SourceScanTarget(
        UUID sourceId,
        String name,
        SourceRole role,
        String rootPath,
        String adapterId,
        List<String> includeGlobs,
        List<String> excludeGlobs) {}
