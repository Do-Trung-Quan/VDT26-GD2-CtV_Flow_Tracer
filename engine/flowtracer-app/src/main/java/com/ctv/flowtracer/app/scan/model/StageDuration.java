package com.ctv.flowtracer.app.scan.model;

/**
 * Thời gian đo được của một giai đoạn đã chạy.
 *
 * @param stage giai đoạn
 * @param durationMs thời gian (mili giây)
 */
public record StageDuration(
        ScanStage stage,
        long durationMs) {}
