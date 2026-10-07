package com.ctv.flowtracer.ir.spi;

import java.util.Objects;

/**
 * Một file mã nguồn được truyền vào adapter. Adapter KHÔNG tự đọc file; nội dung đã được ứng dụng đọc sẵn
 * qua {@code SourceReader}.
 *
 * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
 * @param content nội dung văn bản (UTF-8)
 * @param contentHash SHA-256 của nội dung (hex)
 */
public record SourceFile(String relativePath, String content, String contentHash) {

    public SourceFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
