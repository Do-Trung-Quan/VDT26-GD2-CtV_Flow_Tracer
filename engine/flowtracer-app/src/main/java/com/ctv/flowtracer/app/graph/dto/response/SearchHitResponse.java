package com.ctv.flowtracer.app.graph.dto.response;

import com.ctv.flowtracer.ir.NodeKind;

/**
 * Một kết quả tìm kiếm (API C3).
 *
 * @param nodeKey định danh ổn định của thành phần
 * @param kind loại node trong IR
 * @param name tên kỹ thuật (đường dẫn API, tên class, tên bảng…)
 * @param displayName nhãn tiếng Việt / mô tả ngắn nếu mã nguồn có
 * @param context chú thích ngắn giúp phân biệt kết quả (module của API, route của màn hình, component của phần tử…); có thể null
 * @param filePath file khai báo, tương đối trong Source
 * @param line dòng bắt đầu
 */
public record SearchHitResponse(
        String nodeKey, NodeKind kind, String name, String displayName, String context, String filePath, Integer line) {}
