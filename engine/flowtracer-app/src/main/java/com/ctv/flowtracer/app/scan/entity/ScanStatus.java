package com.ctv.flowtracer.app.scan.entity;

import java.util.EnumSet;
import java.util.Set;

/** Trạng thái lần quét và các bước chuyển hợp lệ. */
public enum ScanStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED;

    public Set<ScanStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(SUCCEEDED, PARTIAL, FAILED, CANCELLED);
            case SUCCEEDED, PARTIAL, FAILED, CANCELLED -> EnumSet.noneOf(ScanStatus.class);
        };
    }

    public boolean isFinished() {
        return allowedNext().isEmpty();
    }
}
