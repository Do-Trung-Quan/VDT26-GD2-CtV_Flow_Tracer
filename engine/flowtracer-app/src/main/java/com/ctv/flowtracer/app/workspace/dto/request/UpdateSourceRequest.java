package com.ctv.flowtracer.app.workspace.dto.request;

import com.ctv.flowtracer.app.common.web.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yêu cầu sửa một thư mục mã nguồn (API A8). Trường {@code null} thì giữ nguyên. Thư mục và vai trò không sửa được.
 *
 * @param name tên hiển thị mới
 * @param excludeGlobs danh sách loại trừ mới (thay toàn bộ danh sách cũ); luật loại trừ Source con luôn được giữ
 */
public record UpdateSourceRequest(
        @Size(max = 200, message = "Tên nguồn mã tối đa 200 ký tự")
        @Pattern(regexp = ValidationPatterns.NOT_BLANK_TEXT, message = "Tên nguồn mã không được để trống")
        String name,

        @Size(max = 100, message = "Tối đa 100 luật loại trừ")
        List<
                        @NotBlank(message = "Luật loại trừ không được để trống")
                        @Size(max = 300, message = "Luật loại trừ tối đa 300 ký tự")
                        @Pattern(
                                regexp = ValidationPatterns.RELATIVE_GLOB,
                                message = "Luật loại trừ phải là glob tương đối, không chứa '..'")
                        String>
                excludeGlobs) {}
