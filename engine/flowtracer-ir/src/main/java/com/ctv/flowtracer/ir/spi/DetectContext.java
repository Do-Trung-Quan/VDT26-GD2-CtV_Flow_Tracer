package com.ctv.flowtracer.ir.spi;

import java.util.List;
import java.util.Objects;

/**
 * Ngữ cảnh nhận diện công nghệ của một thư mục ứng viên gốc dự án.
 *
 * @param relativePaths các file nằm ngay trong thư mục ứng viên (sắp xếp ổn định), dùng dấu '/'
 * @param loader đọc nội dung file theo đường dẫn tương đối so với thư mục ứng viên (vd {@code pom.xml}, {@code api/pom.xml})
 */
public record DetectContext(List<String> relativePaths, SourceContentLoader loader) {

    public DetectContext {
        relativePaths = relativePaths == null ? List.of() : List.copyOf(relativePaths);
        Objects.requireNonNull(loader, "loader");
    }
}
