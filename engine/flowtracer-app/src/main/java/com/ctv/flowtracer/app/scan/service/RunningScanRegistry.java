package com.ctv.flowtracer.app.scan.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sổ ghi nhớ "Workspace nào đang quét" — chặn quét trùng, chặn xóa Workspace / sửa nguồn mã khi đang quét. Service
 * công khai cho package {@code workspace} (Phụ lục B của project_structure.md).
 *
 * <p>TODO (Giai đoạn 2): {@code ScanService} gọi {@link #tryStart}/{@link #finish}; giữ handle để hủy lần quét.
 */
@Component
public class RunningScanRegistry {

    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public boolean isRunning(UUID workspaceId) {
        return running.contains(workspaceId);
    }

    /** @return {@code false} nếu Workspace đã có lần quét đang chạy */
    public boolean tryStart(UUID workspaceId) {
        return running.add(workspaceId);
    }

    public void finish(UUID workspaceId) {
        running.remove(workspaceId);
    }
}
