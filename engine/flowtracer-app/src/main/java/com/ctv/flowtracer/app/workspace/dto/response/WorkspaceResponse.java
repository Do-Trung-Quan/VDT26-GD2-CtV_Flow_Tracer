package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.app.scan.dto.response.LastScanResponse;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin Workspace kèm danh sách Source và lần quét gần nhất (API A1–A4).
 *
 * @param id định danh Workspace
 * @param name tên Workspace
 * @param description mô tả (có thể null)
 * @param sourceCount số thư mục mã nguồn
 * @param sources các thư mục mã nguồn, sắp theo đường dẫn
 * @param lastScan lần quét gần nhất (null nếu chưa quét)
 * @param createdAt thời điểm tạo
 * @param updatedAt thời điểm cập nhật gần nhất
 */
public record WorkspaceResponse(
        UUID id,
        String name,
        String description,
        int sourceCount,
        List<SourceResponse> sources,
        LastScanResponse lastScan,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkspaceResponse from(Workspace workspace, List<SourceResponse> sources, LastScanResponse lastScan) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                sources.size(),
                List.copyOf(sources),
                lastScan,
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
