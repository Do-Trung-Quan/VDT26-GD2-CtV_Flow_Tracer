package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.scan.model.ScanStats;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lần quét (API B1, B3, B4, B5).
 *
 * @param id định danh lần quét
 * @param workspaceId Workspace được quét
 * @param trigger kiểu quét
 * @param status trạng thái
 * @param cancelRequested đã có yêu cầu hủy nhưng lần quét chưa dừng hẳn
 * @param errorSummary lý do thất bại / ghi chú (null nếu không có)
 * @param startedAt thời điểm bắt đầu
 * @param finishedAt thời điểm kết thúc (null khi đang chạy)
 * @param durationMs tổng thời gian (null khi đang chạy)
 * @param stats thống kê (null khi chưa xong)
 */
public record ScanRunResponse(
        UUID id,
        UUID workspaceId,
        ScanTrigger trigger,
        ScanStatus status,
        boolean cancelRequested,
        String errorSummary,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ScanStatsResponse stats) {

    public static ScanRunResponse from(ScanRun run, ScanStats stats, boolean cancelRequested) {
        return new ScanRunResponse(
                run.getId(),
                run.getWorkspaceId(),
                run.getTrigger(),
                run.getStatus(),
                cancelRequested,
                run.getErrorSummary(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getFinishedAt() == null ? null : Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis(),
                stats == null ? null : ScanStatsResponse.from(stats));
    }
}
