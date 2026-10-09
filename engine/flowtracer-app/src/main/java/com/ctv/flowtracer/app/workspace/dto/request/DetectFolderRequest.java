package com.ctv.flowtracer.app.workspace.dto.request;

import com.ctv.flowtracer.app.common.web.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu nhận diện một thư mục trước khi thêm (API A10).
 *
 * @param rootPath đường dẫn tương đối trong {@code SOURCE_ROOT} (chuỗi rỗng là chính {@code SOURCE_ROOT})
 */
public record DetectFolderRequest(
        @NotNull(message = "Đường dẫn thư mục là bắt buộc")
        @Size(max = 1000, message = "Đường dẫn tối đa 1000 ký tự")
        @Pattern(
                regexp = ValidationPatterns.RELATIVE_PATH,
                message = "Đường dẫn phải là đường dẫn tương đối trong SOURCE_ROOT, không chứa '..'")
        String rootPath) {}
