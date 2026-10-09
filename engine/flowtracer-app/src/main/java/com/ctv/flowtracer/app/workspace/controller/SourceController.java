package com.ctv.flowtracer.app.workspace.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.workspace.dto.request.CreateSourceRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateSourceRequest;
import com.ctv.flowtracer.app.workspace.dto.response.SourceResponse;
import com.ctv.flowtracer.app.workspace.service.SourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API A7–A9: thêm, sửa, gỡ thư mục mã nguồn của Workspace. */
@RestController
@RequestMapping("/workspaces/{id}/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /** A7 — thư mục có nhiều gốc dự án được tách thành nhiều Source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<SourceResponse>> add(@PathVariable UUID id, @Valid @RequestBody CreateSourceRequest request) {
        return ApiResponse.created(sourceService.add(id, request), "Đã thêm nguồn mã");
    }

    /** A8 */
    @PatchMapping("/{sourceId}")
    public ApiResponse<SourceResponse> update(
            @PathVariable UUID id, @PathVariable UUID sourceId, @Valid @RequestBody UpdateSourceRequest request) {
        return ApiResponse.ok(sourceService.update(id, sourceId, request), "Đã lưu nguồn mã");
    }

    /** A9 — thư mục trên máy không bị đụng tới. */
    @DeleteMapping("/{sourceId}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @PathVariable UUID sourceId) {
        sourceService.delete(id, sourceId);
        return ApiResponse.ok(null, "Đã gỡ nguồn mã");
    }
}
