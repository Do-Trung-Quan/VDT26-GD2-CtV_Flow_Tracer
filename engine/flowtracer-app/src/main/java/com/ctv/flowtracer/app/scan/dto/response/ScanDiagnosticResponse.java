package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.ScanDiagnostic;
import com.ctv.flowtracer.ir.Diagnostic;
import java.util.UUID;

/**
 * Một chẩn đoán của bộ phân tích.
 *
 * @param severity {@code ERROR} / {@code WARNING} / {@code INFO}
 * @param sourceId Source phát sinh
 * @param filePath file liên quan, tương đối trong Source (null nếu không gắn với file)
 * @param line dòng (null nếu không xác định)
 * @param message nội dung
 */
public record ScanDiagnosticResponse(
        Diagnostic.Severity severity,
        UUID sourceId,
        String filePath,
        Integer line,
        String message) {

    public static ScanDiagnosticResponse from(ScanDiagnostic d) {
        return new ScanDiagnosticResponse(d.severity(), d.sourceId(), d.filePath(), d.line(), d.message());
    }
}
