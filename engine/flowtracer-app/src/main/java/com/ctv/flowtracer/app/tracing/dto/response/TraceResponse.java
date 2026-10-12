package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.ir.trace.Trace;
import java.util.List;

/**
 * Kết quả dò vết luồng (API D1): đủ để vẽ sequence diagram và sinh kịch bản.
 *
 * @param traceId mã tất định từ (điểm bắt đầu, độ sâu, lần quét, liên kết chốt tay, bộ lọc nhiễu); dùng cho xuất diagram
 * @param startKey node_key của thành phần xuất phát
 * @param startName tên kỹ thuật của thành phần xuất phát (vd {@code POST /api/v1/categories})
 * @param startDisplayName mô tả ngắn lấy từ {@code @Operation} / Javadoc; null nếu mã nguồn không có
 * @param maxDepth độ sâu đã dùng để dò
 * @param participants các cột, đã xếp theo vai trò (Client, Controller, Service, Repository, Database)
 * @param steps các bước theo thứ tự thực thi, gồm cả chiều trả về
 * @param blocks khối điều kiện/lặp
 * @param warnings cảnh báo có căn cứ (vd "1 lời gọi chưa xác định được đích (xem bước 7)"); rỗng nếu không có
 * @param uiOnly true nếu luồng chỉ gồm User và Frontend, không gọi máy chủ (giao diện ghi "Không gọi máy chủ")
 */
public record TraceResponse(
        String traceId,
        String startKey,
        String startName,
        String startDisplayName,
        int maxDepth,
        List<TraceParticipantResponse> participants,
        List<TraceStepResponse> steps,
        List<TraceBlockResponse> blocks,
        List<String> warnings,
        boolean uiOnly) {

    public static TraceResponse from(Trace t, String startName, String startDisplayName, int maxDepth) {
        return new TraceResponse(
                t.traceId(),
                t.startKey(),
                startName,
                startDisplayName,
                maxDepth,
                t.participants().stream().map(TraceParticipantResponse::from).toList(),
                t.steps().stream().map(TraceStepResponse::from).toList(),
                t.blocks().stream().map(TraceBlockResponse::from).toList(),
                t.warnings(),
                t.uiOnly());
    }
}
