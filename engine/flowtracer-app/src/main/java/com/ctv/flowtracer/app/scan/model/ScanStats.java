package com.ctv.flowtracer.app.scan.model;

import java.util.List;

/**
 * Thống kê một lần quét, lưu ở cột {@code scan_runs.stats} (JSONB). Mọi số đều đo hoặc đếm thật trong lần quét; giai
 * đoạn nào không chạy thì không có trong {@code stageDurations}.
 *
 * @param files tổng số file của mọi Source
 * @param nodes số thành phần của Workspace sau lần quét
 * @param edges số quan hệ của Workspace sau lần quét
 * @param endpoints số endpoint Backend của Workspace sau lần quét
 * @param diagnosticErrors số chẩn đoán mức lỗi (file lỗi cú pháp…)
 * @param diagnosticWarnings số chẩn đoán mức cảnh báo (hằng số đường dẫn không giải được…)
 * @param noChanges quét lại nhưng không có file nào thay đổi — không phân tích lại
 * @param configChanges số file cấu hình (vd {@code application.yml}) thêm / sửa / xóa — không tính vào số file; có thay đổi
 *     thì kết quả được ghép lại dù không file mã nguồn nào đổi
 * @param stageDurations thời gian từng giai đoạn đã chạy, theo thứ tự chạy
 * @param diagnostics chẩn đoán của bộ phân tích (lỗi trước, tối đa {@value #MAX_DIAGNOSTICS} mục) — căn cứ của hai số
 *     đếm chẩn đoán
 * @param sources kết quả từng Source
 */
public record ScanStats(
        FileCounts files,
        long nodes,
        long edges,
        long endpoints,
        int diagnosticErrors,
        int diagnosticWarnings,
        boolean noChanges,
        int configChanges,
        List<StageDuration> stageDurations,
        List<ScanDiagnostic> diagnostics,
        List<SourceScanStats> sources) {

    /** Số chẩn đoán tối đa được lưu kèm lần quét. */
    public static final int MAX_DIAGNOSTICS = 100;
}
