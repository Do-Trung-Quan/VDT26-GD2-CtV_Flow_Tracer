package com.ctv.flowtracer.app.explanation.dto.response;

import com.ctv.flowtracer.app.graph.dto.response.NodeRelationResponse;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;

/**
 * Nội dung tab Diễn giải của một thành phần (API F1). Phần tất định; AI chưa tham gia (Giai đoạn 7).
 *
 * @param nodeKey key ổn định của thành phần
 * @param kind loại thành phần
 * @param name tên kỹ thuật
 * @param displayName tên thân thiện (có thể null)
 * @param container true nếu là thành phần chứa (ứng dụng, màn hình, tab, hộp thoại, form): chỉ có {@code purpose}, không có đầu vào / đầu ra
 * @param purpose đoạn mục đích (hoặc mô tả ngắn với thành phần chứa)
 * @param apis đầu vào / đầu ra / ràng buộc theo từng API mà thành phần là hoặc gọi tới; rỗng với thành phần chứa và phần tử chỉ thao tác giao diện
 * @param unresolvedCalls lời gọi HTTP của phần tử giao diện mà chưa xác định được API đích (vd "POST /gateway/api/v1/…"): phần tử VẪN gọi
 *     máy chủ, chỉ chưa biết gọi API nào — hãy chốt tay liên kết trên diagram; rỗng nếu mọi lời gọi đã xác định
 * @param trail chuỗi thành phần chứa, từ cao nhất xuống cha trực tiếp (thành phần giao diện)
 * @param calledFrom các thành phần giao diện gọi tới API này (khi thành phần là API)
 * @param calledFromTotal tổng số thành phần gọi tới (có thể lớn hơn số phần tử trong {@code calledFrom})
 * @param ai trạng thái nội dung AI của Mục đích / Ràng buộc / Lỗi; null với thành phần chứa
 * @param callsTo thành phần mà nó gọi tới: API / hộp thoại / màn hình (phần tử giao diện), bảng dữ liệu / hệ thống khác (API)
 */
public record ExplanationResponse(
        String nodeKey,
        NodeKind kind,
        String name,
        String displayName,
        boolean container,
        PurposeResponse purpose,
        List<ApiIoResponse> apis,
        List<String> unresolvedCalls,
        List<NodeRelationResponse> trail,
        List<NodeRelationResponse> calledFrom,
        int calledFromTotal,
        List<NodeRelationResponse> callsTo,
        AiInfoResponse ai) {}
