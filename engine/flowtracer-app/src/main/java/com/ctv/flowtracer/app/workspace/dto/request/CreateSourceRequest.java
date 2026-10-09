package com.ctv.flowtracer.app.workspace.dto.request;

import com.ctv.flowtracer.app.common.web.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yêu cầu thêm một thư mục mã nguồn (API A7, và từng phần tử {@code sources} của API A2). Không có vai trò — vai trò do
 * tool tự nhận diện.
 *
 * @param rootPath đường dẫn tương đối trong {@code SOURCE_ROOT} (chuỗi rỗng là chính {@code SOURCE_ROOT})
 * @param name tên hiển thị (tùy chọn; mặc định là tên thư mục). Chỉ áp dụng khi thư mục là đúng một gốc dự án
 * @param excludeGlobs thư mục / file loại trừ, glob tương đối so với gốc Source (vd {@code generated/**})
 */
public record CreateSourceRequest(
        @NotNull(message = "Đường dẫn thư mục là bắt buộc")
        @Size(max = 1000, message = "Đường dẫn tối đa 1000 ký tự")
        @Pattern(
                regexp = ValidationPatterns.RELATIVE_PATH,
                message = "Đường dẫn phải là đường dẫn tương đối trong SOURCE_ROOT, không chứa '..'")
        String rootPath,

        @Size(max = 200, message = "Tên nguồn mã tối đa 200 ký tự")
        String name,

        @Size(max = 100, message = "Tối đa 100 luật loại trừ")
        List<
                        @NotBlank(message = "Luật loại trừ không được để trống")
                        @Size(max = 300, message = "Luật loại trừ tối đa 300 ký tự")
                        @Pattern(
                                regexp = ValidationPatterns.RELATIVE_GLOB,
                                message = "Luật loại trừ phải là glob tương đối, không chứa '..'")
                        String>
                excludeGlobs) {

    public CreateSourceRequest {
        excludeGlobs = excludeGlobs == null ? List.of() : List.copyOf(excludeGlobs);
    }
}
