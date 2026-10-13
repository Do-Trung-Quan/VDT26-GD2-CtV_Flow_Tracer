package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import java.util.List;

/**
 * Chi tiết một liên kết (API E1) — popover khi bấm vào mũi tên.
 *
 * @param edgeId id của liên kết (trùng {@code edgeRef} của bước trên diagram)
 * @param kind loại liên kết (vd {@code HTTP_REQUEST}, {@code CALLS})
 * @param confidence mức tin cậy tự động (trước khi áp chốt tay)
 * @param reason lý do của mức tin cậy
 * @param from thành phần gọi
 * @param to đích tự động; null nếu chưa xác định
 * @param candidates các ứng viên, mức khớp cao trước; rỗng nếu liên kết chắc chắn
 * @param filePath file của điểm gọi (có thể null)
 * @param line dòng của điểm gọi (có thể null)
 * @param override quyết định chốt tay đang áp cho liên kết này (null nếu chưa có)
 * @param overridable true nếu loại liên kết này chốt tay được
 */
public record EdgeDetailResponse(
        String edgeId,
        EdgeKind kind,
        Confidence confidence,
        String reason,
        EdgeNodeResponse from,
        EdgeNodeResponse to,
        List<EdgeCandidateResponse> candidates,
        String filePath,
        Integer line,
        LinkOverrideResponse override,
        boolean overridable) {}
