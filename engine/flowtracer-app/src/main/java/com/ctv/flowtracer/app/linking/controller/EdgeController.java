package com.ctv.flowtracer.app.linking.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeDetailResponse;
import com.ctv.flowtracer.app.linking.service.EdgeAdviceService;
import com.ctv.flowtracer.app.linking.service.LinkingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API E1: chi tiết một liên kết (popover khi bấm mũi tên); E2: "Nhờ AI phân tích" — chỉ giải thích, không đổi liên kết. */
@RestController
@RequestMapping("/workspaces/{id}/edges")
public class EdgeController {

    private final LinkingService linkingService;
    private final EdgeAdviceService adviceService;

    public EdgeController(LinkingService linkingService, EdgeAdviceService adviceService) {
        this.linkingService = linkingService;
        this.adviceService = adviceService;
    }

    /**
     * E1 — {@code edgeId} là {@code edgeRef} của bước trên diagram; {@code start_key} (tùy chọn) là thành phần xuất phát của luồng đang
     * xem, để kèm tín hiệu khớp tên từ chuỗi gọi cho từng ứng viên.
     */
    @GetMapping("/{edgeId}")
    public ApiResponse<EdgeDetailResponse> get(
            @PathVariable UUID id,
            @PathVariable UUID edgeId,
            @org.springframework.web.bind.annotation.RequestParam(name = "start_key", required = false) String startKey) {
        return ApiResponse.ok(linkingService.edgeDetail(id, edgeId, startKey));
    }

    /** E2 — AI giải thích từng ứng viên; {@code 503} khi AI tắt, hết hạn mức hoặc lỗi. Không đổi liên kết. */
    @org.springframework.web.bind.annotation.PostMapping("/{edgeId}/advice")
    public ApiResponse<com.ctv.flowtracer.app.linking.dto.response.EdgeAdviceResponse> advice(
            @PathVariable UUID id,
            @PathVariable UUID edgeId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) com.ctv.flowtracer.app.linking.dto.request.EdgeAdviceRequest request) {
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        return ApiResponse.ok(adviceService.advise(id, edgeId, force, request == null ? null : request.startKey()));
    }
}
