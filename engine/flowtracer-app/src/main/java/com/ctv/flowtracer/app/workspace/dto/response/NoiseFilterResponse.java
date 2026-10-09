package com.ctv.flowtracer.app.workspace.dto.response;

import com.ctv.flowtracer.ir.NoiseFilter;

/**
 * Một bộ lọc nhiễu.
 *
 * @param pattern glob khớp tên đầy đủ của thành phần, vd {@code *.util.*}
 * @param description lý do ẩn (có thể null)
 */
public record NoiseFilterResponse(
        String pattern,
        String description) {

    public static NoiseFilterResponse from(NoiseFilter filter) {
        return new NoiseFilterResponse(filter.pattern(), filter.description());
    }
}
