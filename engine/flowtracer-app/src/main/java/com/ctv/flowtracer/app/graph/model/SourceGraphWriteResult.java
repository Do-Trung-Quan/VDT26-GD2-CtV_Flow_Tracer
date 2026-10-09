package com.ctv.flowtracer.app.graph.model;

import java.util.List;

/**
 * Kết quả ghi IR của một Source.
 *
 * @param nodesWritten số node đã ghi (thêm mới hoặc cập nhật)
 * @param nodesRemoved số node của lần quét trước không còn, đã xóa
 * @param edgesWritten số edge đã ghi
 * @param skippedKeys key bị bỏ vì đang thuộc Source khác trong cùng Workspace
 * @param skippedEdges số edge bị bỏ vì điểm đầu không có trong IR
 */
public record SourceGraphWriteResult(
        int nodesWritten,
        int nodesRemoved,
        int edgesWritten,
        List<String> skippedKeys,
        int skippedEdges) {}
