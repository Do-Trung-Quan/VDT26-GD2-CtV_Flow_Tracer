package com.ctv.flowtracer.app.workspace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Một bộ lọc nhiễu thủ công trong {@link UpdateNoiseFiltersRequest}.
 *
 * @param pattern glob khớp tên đầy đủ của thành phần, vd {@code *.util.*}, {@code org.slf4j.*}
 * @param description lý do ẩn (tùy chọn)
 */
public record NoiseFilterItemRequest(
        @NotBlank(message = "Mẫu bộ lọc không được để trống")
        @Size(max = 300, message = "Mẫu bộ lọc tối đa 300 ký tự")
        String pattern,

        @Size(max = 300, message = "Mô tả bộ lọc tối đa 300 ký tự")
        String description) {}
