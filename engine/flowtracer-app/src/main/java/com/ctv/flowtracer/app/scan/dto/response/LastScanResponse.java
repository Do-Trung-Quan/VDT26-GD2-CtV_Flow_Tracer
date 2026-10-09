package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import java.time.Instant;
import java.util.UUID;

/**
 * Tóm tắt lần quét gần nhất của Workspace (hiển thị trên thẻ Workspace).
 *
 * @param id định danh lần quét
 * @param trigger kiểu quét
 * @param status trạng thái
 * @param startedAt thời điểm bắt đầu
 * @param finishedAt thời điểm kết thúc (null khi đang chạy)
 */
public record LastScanResponse(
        UUID id,
        ScanTrigger trigger,
        ScanStatus status,
        Instant startedAt,
        Instant finishedAt) {

    public static LastScanResponse from(ScanRun run) {
        return new LastScanResponse(run.getId(), run.getTrigger(), run.getStatus(), run.getStartedAt(), run.getFinishedAt());
    }
}
