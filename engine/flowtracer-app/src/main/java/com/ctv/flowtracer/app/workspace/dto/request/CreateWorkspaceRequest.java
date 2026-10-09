package com.ctv.flowtracer.app.workspace.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yêu cầu tạo Workspace (API A2). Vai trò và công nghệ của từng Source do tool tự nhận diện; thư mục có nhiều gốc dự án
 * được tách thành nhiều Source.
 *
 * @param name tên Workspace, không trùng với Workspace khác
 * @param description mô tả ngắn (tùy chọn)
 * @param sources các thư mục mã nguồn (tùy chọn, có thể thêm sau)
 */
public record CreateWorkspaceRequest(
        @NotBlank(message = "Tên Workspace không được để trống")
        @Size(max = 200, message = "Tên Workspace tối đa 200 ký tự")
        String name,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        @Size(max = 20, message = "Tối đa 20 thư mục mã nguồn mỗi lần tạo")
        List<@NotNull(message = "Thư mục mã nguồn không được rỗng") @Valid CreateSourceRequest> sources) {

    public CreateWorkspaceRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
