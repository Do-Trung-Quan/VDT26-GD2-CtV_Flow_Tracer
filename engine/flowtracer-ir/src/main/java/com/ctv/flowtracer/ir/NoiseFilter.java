package com.ctv.flowtracer.ir;

import java.util.Objects;

/**
 * Mẫu thành phần bị ẩn khỏi trace (logger, mapper, *Utils…).
 *
 * @param pattern glob khớp với tên đầy đủ, vd {@code *.util.*}, {@code org.slf4j.*}, {@code *#get*()}
 */
public record NoiseFilter(String pattern, String description) {

    public NoiseFilter {
        Objects.requireNonNull(pattern, "pattern");
    }
}
