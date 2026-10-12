package com.ctv.flowtracer.app.workspace.model;

import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.List;
import java.util.UUID;

/**
 * Thiết lập của Workspace ảnh hưởng tới dò vết — cho package {@code tracing}.
 *
 * @param noiseFilters bộ lọc nhiễu hiệu lực: mặc định của các adapter cộng với bộ lọc thủ công, bỏ trùng, sắp theo mẫu
 * @param defaultMaxDepth độ sâu dò vết mặc định của Workspace; null nếu dùng mặc định của hệ thống
 * @param lastScanRunId lần quét gần nhất (đã có IR); null nếu chưa quét
 */
public record TraceSettings(List<NoiseFilter> noiseFilters, Integer defaultMaxDepth, UUID lastScanRunId) {}
