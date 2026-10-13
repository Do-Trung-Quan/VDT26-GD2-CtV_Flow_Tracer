package com.ctv.flowtracer.app.linking.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeDetailResponse;
import com.ctv.flowtracer.app.linking.service.LinkingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API E1: chi tiết một liên kết (popover khi bấm mũi tên). "Nhờ AI phân tích" (E2) làm ở Giai đoạn 7. */
@RestController
@RequestMapping("/workspaces/{id}/edges")
public class EdgeController {

    private final LinkingService linkingService;

    public EdgeController(LinkingService linkingService) {
        this.linkingService = linkingService;
    }

    /** E1 — {@code edgeId} là {@code edgeRef} của bước trên diagram. */
    @GetMapping("/{edgeId}")
    public ApiResponse<EdgeDetailResponse> get(@PathVariable UUID id, @PathVariable UUID edgeId) {
        return ApiResponse.ok(linkingService.edgeDetail(id, edgeId));
    }
}
