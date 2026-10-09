package com.ctv.flowtracer.app.workspace.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateNoiseFiltersRequest;
import com.ctv.flowtracer.app.workspace.dto.response.NoiseFiltersResponse;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API I4–I5: bộ lọc nhiễu (mặc định của adapter + thủ công) của Workspace. */
@RestController
@RequestMapping("/workspaces/{id}/noise-filters")
public class NoiseFilterController {

    private final WorkspaceService workspaceService;

    public NoiseFilterController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /** I4 */
    @GetMapping
    public ApiResponse<NoiseFiltersResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(workspaceService.noiseFilters(id));
    }

    /** I5 — thay toàn bộ danh sách thủ công. */
    @PutMapping
    public ApiResponse<NoiseFiltersResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateNoiseFiltersRequest request) {
        return ApiResponse.ok(workspaceService.updateNoiseFilters(id, request), "Đã lưu bộ lọc nhiễu");
    }
}
