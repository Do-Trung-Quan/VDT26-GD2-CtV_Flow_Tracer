package com.ctv.flowtracer.app.workspace.dto.response;

import java.util.List;

/**
 * Kết quả nhận diện một thư mục (API A10).
 *
 * @param status kết luận
 * @param roots các gốc dự án tìm thấy (chỉ có khi {@code VALID}), sắp theo đường dẫn
 * @param suggestedRoot gốc dự án nên dùng thay thế (chỉ có khi {@code INSIDE_PROJECT})
 * @param legacyHints thư mục có nhiều mã của công nghệ chưa hỗ trợ (vd PHP hệ thống cũ) — chỉ để báo
 * @param warnings cảnh báo cho người dùng (bộ nhận diện lỗi, thư mục quá lớn…)
 */
public record DetectionResponse(
        DetectionStatus status,
        List<DetectedRootResponse> roots,
        DetectedRootResponse suggestedRoot,
        List<LegacyHintResponse> legacyHints,
        List<String> warnings) {}
