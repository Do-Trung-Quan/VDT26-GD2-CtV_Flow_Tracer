package com.ctv.flowtracer.app.graph.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.graph.dto.response.BackendTreeResponse;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API C1: cây API Backend. (C2 cây Frontend làm ở Giai đoạn 4.) */
@RestController
@RequestMapping("/workspaces/{id}/trees")
public class TreeController {

    private final GraphQueryService graphQueryService;

    public TreeController(GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    /** C1 — {@code Module → Endpoint}; rỗng nếu Workspace chưa quét. */
    @GetMapping("/backend")
    public ApiResponse<BackendTreeResponse> backend(@PathVariable UUID id) {
        return ApiResponse.ok(graphQueryService.backendTree(id));
    }
}
