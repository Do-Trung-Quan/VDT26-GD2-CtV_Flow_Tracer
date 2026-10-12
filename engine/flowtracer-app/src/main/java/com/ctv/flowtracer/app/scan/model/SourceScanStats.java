package com.ctv.flowtracer.app.scan.model;

import java.util.UUID;

/**
 * Kết quả quét của một Source.
 *
 * @param sourceId id Source
 * @param sourceName tên Source
 * @param adapter bộ phân tích đã dùng
 * @param files số file theo loại
 * @param nodes số thành phần đã lưu của Source sau lần quét
 * @param endpoints số endpoint đã lưu của Source sau lần quét
 * @param skippedNodes số thành phần bị bỏ vì trùng key với Source khác trong Workspace
 * @param note ghi chú khi Source không được phân tích (vd bộ phân tích chưa hỗ trợ); null nếu không có
 * @param analysisVersion phiên bản bộ phân tích đã dựng IR của Source ({@code id-v<phiên bản>}); đổi phiên bản thì lần quét
 *     lại kế tiếp phân tích lại toàn bộ dù không file nào đổi; null ở lần quét ghi trước khi có trường này
 */
public record SourceScanStats(
        UUID sourceId,
        String sourceName,
        String adapter,
        FileCounts files,
        int nodes,
        int endpoints,
        int skippedNodes,
        String note,
        String analysisVersion) {}
