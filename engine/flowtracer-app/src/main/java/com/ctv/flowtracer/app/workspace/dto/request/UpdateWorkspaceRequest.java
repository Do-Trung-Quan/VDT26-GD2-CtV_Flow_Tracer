package com.ctv.flowtracer.app.workspace.dto.request;

import com.ctv.flowtracer.app.common.web.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu sửa thông tin Workspace (API A4). Trường {@code null} thì giữ nguyên.
 *
 * @param name tên mới (không trùng Workspace khác)
 * @param description mô tả mới; chuỗi rỗng là xóa mô tả
 */
public record UpdateWorkspaceRequest(
        @Size(max = 200, message = "Tên Workspace tối đa 200 ký tự")
        @Pattern(regexp = ValidationPatterns.NOT_BLANK_TEXT, message = "Tên Workspace không được để trống")
        String name,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description) {}
