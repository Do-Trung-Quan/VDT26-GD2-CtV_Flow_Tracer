package com.ctv.flowtracer.app.graph.dto.response;

import com.ctv.flowtracer.ir.NodeKind;

/**
 * Một thành phần liên quan tới thành phần đang xem ("Được gọi từ", "Gọi tới", chuỗi thành phần chứa).
 *
 * @param nodeKey key ổn định của thành phần
 * @param kind loại thành phần
 * @param name tên kỹ thuật (vd {@code POST /api/v1/categories}, tên bảng)
 * @param displayName tên thân thiện tiếng Việt (có thể null)
 * @param context thành phần chứa gần nhất (màn hình / hộp thoại) của phần tử giao diện; null nếu không áp dụng
 * @param elementType loại phần tử giao diện ({@code button}, {@code input}…); null nếu không phải phần tử
 * @param fallbackName tên kỹ thuật của phần tử không có nhãn đọc được (như trên cây Frontend); null nếu đã có {@code displayName}
 * @param routePath đường dẫn route của màn hình; null nếu không phải màn hình
 * @param aiLabel tên chuẩn do AI đặt cho phần tử giao diện; null nếu chưa đặt
 */
public record NodeRelationResponse(
        String nodeKey,
        NodeKind kind,
        String name,
        String displayName,
        String context,
        String elementType,
        String fallbackName,
        String routePath,
        String aiLabel) {}
