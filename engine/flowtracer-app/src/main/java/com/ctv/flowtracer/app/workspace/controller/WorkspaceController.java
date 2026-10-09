package com.ctv.flowtracer.app.workspace.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.workspace.dto.request.CreateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.response.WorkspaceResponse;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API A1–A5: danh sách, tạo, xem, sửa, xóa Workspace. */
@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /** A1 — mới cập nhật lên đầu. */
    @GetMapping
    public ApiResponse<List<WorkspaceResponse>> list() {
        return ApiResponse.ok(workspaceService.list());
    }

    /** A2 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return ApiResponse.created(workspaceService.create(request), "Đã tạo Workspace");
    }

    /** A3 */
    @GetMapping("/{id}")
    public ApiResponse<WorkspaceResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(workspaceService.get(id));
    }

    /** A4 */
    @PatchMapping("/{id}")
    public ApiResponse<WorkspaceResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ApiResponse.ok(workspaceService.update(id, request), "Đã lưu thông tin Workspace");
    }

    /** A5 — không bao giờ đụng tới mã nguồn. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        workspaceService.delete(id);
        return ApiResponse.ok(null, "Đã xóa Workspace");
    }
}
