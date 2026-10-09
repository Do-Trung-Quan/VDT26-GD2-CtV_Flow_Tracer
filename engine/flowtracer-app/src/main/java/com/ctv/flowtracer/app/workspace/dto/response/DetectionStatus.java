package com.ctv.flowtracer.app.workspace.dto.response;

/** Kết luận nhận diện một thư mục (Nhóm 1 của feature_list). */
public enum DetectionStatus {
    /** Có ít nhất một gốc dự án bên trong — dùng được. */
    VALID,
    /** Thư mục nằm bên trong một dự án — nên dùng gốc dự án được gợi ý. */
    INSIDE_PROJECT,
    /** Không tìm thấy dự án Spring Boot hoặc Angular. */
    NOT_FOUND
}
