package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.ScanStats;
import java.util.List;

/**
 * Thống kê một lần quét — mọi số đều đo/đếm thật.
 *
 * @param files tổng số file của mọi Source
 * @param nodes số thành phần của Workspace sau lần quét
 * @param edges số quan hệ của Workspace sau lần quét
 * @param endpoints số endpoint Backend của Workspace sau lần quét
 * @param diagnosticErrors số chẩn đoán mức lỗi
 * @param diagnosticWarnings số chẩn đoán mức cảnh báo
 * @param noChanges quét lại nhưng không có file nào đổi
 * @param configChanges số file cấu hình (vd application.yml) thêm / sửa / xóa; không tính vào số file
 * @param stageDurations thời gian từng giai đoạn đã chạy, theo thứ tự chạy
 * @param diagnostics chẩn đoán của bộ phân tích (lỗi trước, tối đa 100 mục)
 * @param sources kết quả từng Source
 */
public record ScanStatsResponse(
        FileCountsResponse files,
        long nodes,
        long edges,
        long endpoints,
        int diagnosticErrors,
        int diagnosticWarnings,
        boolean noChanges,
        int configChanges,
        List<StageDurationResponse> stageDurations,
        List<ScanDiagnosticResponse> diagnostics,
        List<SourceScanStatsResponse> sources) {

    public static ScanStatsResponse from(ScanStats s) {
        return new ScanStatsResponse(
                FileCountsResponse.from(s.files()),
                s.nodes(),
                s.edges(),
                s.endpoints(),
                s.diagnosticErrors(),
                s.diagnosticWarnings(),
                s.noChanges(),
                s.configChanges(),
                s.stageDurations().stream().map(StageDurationResponse::from).toList(),
                s.diagnostics().stream().map(ScanDiagnosticResponse::from).toList(),
                s.sources().stream().map(SourceScanStatsResponse::from).toList());
    }
}
