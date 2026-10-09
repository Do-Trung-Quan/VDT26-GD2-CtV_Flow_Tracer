package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.scan.model.ScanHandle;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trạng thái của một lần quét đang chạy, đi qua các bước của pipeline. */
public final class ScanContext {

    private final ScanHandle handle;
    private final ScanTrigger trigger;
    private final List<SourceWork> sources;
    private final Map<ScanStage, Long> stageDurationsMs = new LinkedHashMap<>();

    ScanContext(ScanHandle handle, ScanTrigger trigger, List<SourceWork> sources) {
        this.handle = handle;
        this.trigger = trigger;
        this.sources = List.copyOf(sources);
    }

    UUID workspaceId() {
        return handle.workspaceId();
    }

    UUID scanRunId() {
        return handle.scanRunId();
    }

    ScanTrigger trigger() {
        return trigger;
    }

    List<SourceWork> sources() {
        return sources;
    }

    Map<ScanStage, Long> stageDurationsMs() {
        return stageDurationsMs;
    }

    /** Dừng ngay nếu người dùng đã yêu cầu hủy — gọi giữa các file và giữa các giai đoạn. */
    void checkCancelled() {
        if (handle.isCancelRequested()) {
            throw new ScanCancelledException();
        }
    }

    /** Ném ra để dừng pipeline khi người dùng hủy; IR cũ được giữ nguyên vì chưa tới bước lưu. */
    static final class ScanCancelledException extends RuntimeException {
        ScanCancelledException() {
            super("Lần quét bị hủy", null, false, false);
        }
    }
}
