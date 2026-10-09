package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.app.workspace.entity.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin Workspace kèm danh sách Source (API A1–A4).
 *
 * <p>TODO (Giai đoạn 2): thêm trạng thái và thời điểm của lần quét gần nhất.
 *
 * @param id định danh Workspace
 * @param name tên Workspace
 * @param description mô tả (có thể null)
 * @param sourceCount số thư mục mã nguồn
 * @param sources các thư mục mã nguồn, sắp theo đường dẫn
 * @param lastScanRunId lần quét gần nhất (null nếu chưa quét)
 * @param createdAt thời điểm tạo
 * @param updatedAt thời điểm cập nhật gần nhất
 */
public record WorkspaceResponse(
        UUID id,
        String name,
        String description,
        int sourceCount,
        List<SourceResponse> sources,
        UUID lastScanRunId,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkspaceResponse from(Workspace workspace, List<SourceResponse> sources) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                sources.size(),
                List.copyOf(sources),
                workspace.getLastScanRunId(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
