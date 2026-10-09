package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.SourceScanStats;
import java.util.UUID;

/**
 * Kết quả quét của một Source.
 *
 * @param sourceId id Source
 * @param sourceName tên Source
 * @param adapter bộ phân tích đã dùng
 * @param files số file theo loại
 * @param nodes số thành phần đã lưu của Source
 * @param endpoints số endpoint đã lưu của Source
 * @param skippedNodes số thành phần bị bỏ vì trùng với Source khác
 * @param note ghi chú khi Source không được phân tích (null nếu không có)
 */
public record SourceScanStatsResponse(
        UUID sourceId,
        String sourceName,
        String adapter,
        FileCountsResponse files,
        int nodes,
        int endpoints,
        int skippedNodes,
        String note) {

    public static SourceScanStatsResponse from(SourceScanStats s) {
        return new SourceScanStatsResponse(
                s.sourceId(),
                s.sourceName(),
                s.adapter(),
                FileCountsResponse.from(s.files()),
                s.nodes(),
                s.endpoints(),
                s.skippedNodes(),
                s.note());
    }
}
