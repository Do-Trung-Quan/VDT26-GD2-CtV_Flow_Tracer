package com.ctv.flowtracer.ir.spi;

import java.util.Optional;

/**
 * Cách duy nhất để adapter lấy nội dung một file khác trong Source (vd {@code pom.xml},
 * {@code proxy.conf.json}). Ứng dụng hiện thực bằng {@code SourceReader} — adapter không tự đọc file.
 */
@FunctionalInterface
public interface SourceContentLoader {

    /** @param relativePath đường dẫn tương đối trong Source; trả về rỗng nếu không có file. */
    Optional<String> load(String relativePath);
}
