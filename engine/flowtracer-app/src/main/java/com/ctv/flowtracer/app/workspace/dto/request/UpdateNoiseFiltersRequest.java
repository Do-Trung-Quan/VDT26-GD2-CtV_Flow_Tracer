package com.ctv.flowtracer.app.workspace.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yêu cầu cập nhật bộ lọc nhiễu thủ công của Workspace (API I5); thay toàn bộ danh sách cũ. Bộ lọc mặc định của adapter
 * không sửa được.
 *
 * @param manual danh sách bộ lọc thủ công (trùng mẫu thì giữ mục đầu tiên)
 */
public record UpdateNoiseFiltersRequest(
        @NotNull(message = "Danh sách bộ lọc là bắt buộc (có thể rỗng)")
        @Size(max = 200, message = "Tối đa 200 bộ lọc thủ công")
        List<@NotNull(message = "Bộ lọc không được rỗng") @Valid NoiseFilterItemRequest> manual) {}
