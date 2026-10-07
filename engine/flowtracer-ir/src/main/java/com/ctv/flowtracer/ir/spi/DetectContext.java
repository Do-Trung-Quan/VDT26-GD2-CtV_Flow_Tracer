package com.ctv.flowtracer.ir.spi;

import java.util.List;
import java.util.Objects;

/**
 * Ngữ cảnh nhận diện công nghệ của một Source.
 *
 * @param relativePaths danh sách file của Source (đã qua bộ lọc, sắp xếp ổn định)
 * @param loader đọc nội dung file đặc trưng khi cần
 */
public record DetectContext(List<String> relativePaths, SourceContentLoader loader) {

    public DetectContext {
        relativePaths = relativePaths == null ? List.of() : List.copyOf(relativePaths);
        Objects.requireNonNull(loader, "loader");
    }
}
