package com.ctv.flowtracer.app.workspace.dto.response;

import java.util.List;

/**
 * Bộ lọc nhiễu của Workspace (API I4, I5).
 *
 * @param defaults mặc định của các adapter — không xóa được, sắp theo mẫu
 * @param manual người dùng tự thêm, theo thứ tự đã lưu
 */
public record NoiseFiltersResponse(
        List<NoiseFilterResponse> defaults,
        List<NoiseFilterResponse> manual) {}
