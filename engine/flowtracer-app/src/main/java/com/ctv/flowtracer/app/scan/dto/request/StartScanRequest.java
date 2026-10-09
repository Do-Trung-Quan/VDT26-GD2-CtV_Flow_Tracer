package com.ctv.flowtracer.app.scan.dto.request;

import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu bắt đầu quét (API B1).
 *
 * @param trigger {@code IMPORT}: phân tích lại toàn bộ file; {@code RESCAN}: chỉ phân tích file thêm/sửa (so mã băm),
 *     file không đổi dùng lại dữ kiện đã lưu
 */
public record StartScanRequest(
        @NotNull(message = "Kiểu quét là bắt buộc (IMPORT hoặc RESCAN)")
        ScanTrigger trigger) {}
