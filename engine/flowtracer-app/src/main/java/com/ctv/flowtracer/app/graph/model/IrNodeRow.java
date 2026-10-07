package com.ctv.flowtracer.app.graph.model;

import com.ctv.flowtracer.ir.IrNode;
import java.util.UUID;

/** Một dòng của bảng {@code ir_nodes}: IrNode kèm các id lưu trữ. Ghi/đọc bằng JdbcClient, không dùng JPA. */
public record IrNodeRow(UUID id, UUID workspaceId, UUID sourceId, UUID fileId, IrNode node) {}
