package com.ctv.flowtracer.app.scan.model;

import com.ctv.flowtracer.ir.Diagnostic;
import java.util.UUID;

/**
 * Một chẩn đoán của bộ phân tích (cảnh báo / lỗi), giữ lại để người dùng xem được căn cứ của số liệu thống kê.
 *
 * @param severity mức độ
 * @param sourceId Source phát sinh
 * @param filePath file liên quan, tương đối trong Source (null nếu không gắn với file)
 * @param line dòng (null nếu không xác định)
 * @param message nội dung
 */
public record ScanDiagnostic(
        Diagnostic.Severity severity,
        UUID sourceId,
        String filePath,
        Integer line,
        String message) {}
