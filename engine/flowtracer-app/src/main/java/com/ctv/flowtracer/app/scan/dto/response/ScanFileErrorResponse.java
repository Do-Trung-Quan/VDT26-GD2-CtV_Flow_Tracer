package com.ctv.flowtracer.app.scan.dto.response;

import java.util.UUID;

/**
 * Một file không phân tích được (API B6).
 *
 * @param sourceId Source chứa file
 * @param sourceName tên Source
 * @param filePath đường dẫn tương đối trong Source
 * @param fullPath đường dẫn tương đối trong {@code SOURCE_ROOT}
 * @param reason lý do (lỗi cú pháp kèm dòng, sai bảng mã, vượt kích thước…)
 */
public record ScanFileErrorResponse(
        UUID sourceId,
        String sourceName,
        String filePath,
        String fullPath,
        String reason) {}
