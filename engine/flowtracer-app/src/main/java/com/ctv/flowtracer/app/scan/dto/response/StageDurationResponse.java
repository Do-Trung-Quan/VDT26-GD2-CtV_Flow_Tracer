package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.scan.model.StageDuration;

/**
 * Thời gian đo được của một giai đoạn đã chạy.
 *
 * @param stage giai đoạn
 * @param label tên giai đoạn tiếng Việt
 * @param durationMs thời gian (mili giây)
 */
public record StageDurationResponse(
        ScanStage stage,
        String label,
        long durationMs) {

    public static StageDurationResponse from(StageDuration d) {
        return new StageDurationResponse(d.stage(), d.stage().label(), d.durationMs());
    }
}
