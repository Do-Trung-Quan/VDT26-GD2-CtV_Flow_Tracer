package com.ctv.flowtracer.app.scan.service;

import com.ctv.flowtracer.app.scan.dto.response.LastScanResponse;
import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.model.ScanStats;
import com.ctv.flowtracer.app.scan.repository.ScanRunRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đọc thông tin lần quét — service công khai cho package {@code workspace} (thẻ Workspace hiển thị lần quét gần nhất).
 * Không phụ thuộc service khác để tránh vòng phụ thuộc với {@code WorkspaceService}.
 */
@Service
public class ScanQueryService {

    private final ScanRunRepository scanRuns;
    private final JsonMapper json;

    public ScanQueryService(ScanRunRepository scanRuns, JsonMapper json) {
        this.scanRuns = scanRuns;
        this.json = json;
    }

    /** Tóm tắt các lần quét theo id (bỏ qua id không tồn tại). */
    @Transactional(readOnly = true)
    public Map<UUID, LastScanResponse> summaries(Collection<UUID> scanRunIds) {
        var result = new LinkedHashMap<UUID, LastScanResponse>();
        scanRuns.findAllById(scanRunIds).forEach(r -> result.put(r.getId(), LastScanResponse.from(r)));
        return result;
    }

    /** Thống kê đã lưu của lần quét; null khi lần quét chưa có thống kê (đang chạy, hoặc dừng trước khi tính). */
    public ScanStats statsOf(ScanRun run) {
        return run.getStats().isEmpty() ? null : json.convertValue(run.getStats(), ScanStats.class);
    }

    /** Thống kê → dạng Map để lưu vào cột JSONB. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(ScanStats stats) {
        return json.convertValue(stats, Map.class);
    }
}
