package com.ctv.flowtracer.app.graph.model;

import com.ctv.flowtracer.ir.IrEdge;
import java.util.UUID;

/** Một dòng của bảng {@code ir_edges}: IrEdge kèm các id lưu trữ. */
public record IrEdgeRow(UUID id, UUID workspaceId, UUID fromNodeId, UUID toNodeId, UUID fileId, IrEdge edge) {}
