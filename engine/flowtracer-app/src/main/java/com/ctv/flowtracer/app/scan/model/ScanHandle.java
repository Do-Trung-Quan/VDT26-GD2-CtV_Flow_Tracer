package com.ctv.flowtracer.app.scan.model;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Điều khiển một lần quét đang chạy: yêu cầu hủy và chờ kết thúc. Dùng chung giữa luồng API và luồng quét. */
public final class ScanHandle {

    private final UUID workspaceId;
    private final UUID scanRunId;
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);

    public ScanHandle(UUID workspaceId, UUID scanRunId) {
        this.workspaceId = workspaceId;
        this.scanRunId = scanRunId;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID scanRunId() {
        return scanRunId;
    }

    public void requestCancel() {
        cancelRequested.set(true);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    /** Luồng quét gọi khi đã ghi trạng thái cuối cùng. */
    public void markFinished() {
        finished.countDown();
    }

    /** @return true nếu lần quét đã kết thúc trong thời gian chờ */
    public boolean awaitFinished(Duration timeout) {
        try {
            return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
