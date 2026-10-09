package com.ctv.flowtracer.core.detection;

import com.ctv.flowtracer.ir.spi.SourceContentLoader;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Cách lõi nhìn thấy cây thư mục khi nhận diện — ứng dụng hiện thực bằng {@code SourceReader}, lõi không tự đọc file.
 * Mọi đường dẫn là tương đối so với {@code SOURCE_ROOT}, dùng dấu '/', {@code ""} là chính {@code SOURCE_ROOT}.
 * Thư mục không đọc được thì trả danh sách rỗng.
 *
 * @param subdirNames tên các thư mục con trực tiếp
 * @param fileNames tên các file nằm ngay trong thư mục
 * @param content đọc nội dung một file
 */
public record FolderAccess(
        Function<String, List<String>> subdirNames, Function<String, List<String>> fileNames, SourceContentLoader content) {

    public FolderAccess {
        Objects.requireNonNull(subdirNames, "subdirNames");
        Objects.requireNonNull(fileNames, "fileNames");
        Objects.requireNonNull(content, "content");
    }
}
