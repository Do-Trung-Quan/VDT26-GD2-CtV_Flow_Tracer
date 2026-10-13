package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;

/**
 * Một đầu của liên kết, hoặc một ứng viên.
 *
 * @param nodeKey key ổn định của thành phần
 * @param kind loại thành phần
 * @param name tên kỹ thuật (vd {@code POST /api/v1/categories})
 * @param displayName tên thân thiện (có thể null)
 * @param filePath file chứa thành phần (có thể null)
 * @param line dòng bắt đầu (có thể null)
 */
public record EdgeNodeResponse(
        String nodeKey,
        NodeKind kind,
        String name,
        String displayName,
        String filePath,
        Integer line) {

    public static EdgeNodeResponse from(IrNode node) {
        return new EdgeNodeResponse(
                node.key(),
                node.kind(),
                node.name(),
                node.displayName(),
                node.location() == null ? null : node.location().filePath(),
                node.location() == null ? null : node.location().startLine());
    }
}
