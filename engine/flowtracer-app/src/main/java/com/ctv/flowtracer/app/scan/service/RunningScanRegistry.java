package com.ctv.flowtracer.app.scan.service;

import com.ctv.flowtracer.app.scan.model.ScanHandle;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sổ ghi nhớ "Workspace nào đang quét" — mỗi Workspace tối đa một lần quét; chặn xóa Workspace / sửa nguồn mã khi đang
 * quét; giữ handle để hủy. Service công khai cho package {@code workspace} (Phụ lục B của project_structure.md).
 */
@Component
public class RunningScanRegistry {

    private final Map<UUID, ScanHandle> byWorkspace = new ConcurrentHashMap<>();

    public boolean isRunning(UUID workspaceId) {
        return byWorkspace.containsKey(workspaceId);
    }

    /** @return false nếu Workspace đã có lần quét đang chạy (không đăng ký handle mới) */
    public boolean tryRegister(ScanHandle handle) {
        return byWorkspace.putIfAbsent(handle.workspaceId(), handle) == null;
    }

    public Optional<ScanHandle> byScanRun(UUID scanRunId) {
        return byWorkspace.values().stream().filter(h -> h.scanRunId().equals(scanRunId)).findFirst();
    }

    public void unregister(ScanHandle handle) {
        byWorkspace.remove(handle.workspaceId(), handle);
    }
}
