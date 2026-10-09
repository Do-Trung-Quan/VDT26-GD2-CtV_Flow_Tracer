package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.ScanStage;
import java.util.UUID;

/**
 * Sự kiện tiến độ gửi qua SSE (API B2, tên sự kiện {@code progress}). Sự kiện cuối có tên {@code done} và dữ liệu là
 * {@link ScanRunResponse}.
 *
 * @param scanRunId lần quét
 * @param stage giai đoạn hiện tại
 * @param stageLabel tên giai đoạn tiếng Việt
 * @param stageIndex thứ tự giai đoạn (bắt đầu từ 1)
 * @param stageCount tổng số giai đoạn
 * @param processed số mục đã xử lý trong giai đoạn (file, Source…)
 * @param total tổng số mục của giai đoạn (0 nếu giai đoạn không đếm được)
 * @param message mô tả ngắn
 */
public record ScanProgressResponse(
        UUID scanRunId,
        ScanStage stage,
        String stageLabel,
        int stageIndex,
        int stageCount,
        int processed,
        int total,
        String message) {}
