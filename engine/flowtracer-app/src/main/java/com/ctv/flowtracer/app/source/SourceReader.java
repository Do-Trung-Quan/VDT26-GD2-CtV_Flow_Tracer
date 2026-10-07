package com.ctv.flowtracer.app.source;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Điểm DUY NHẤT được đọc mã nguồn người dùng (CLAUDE.md §3.4). Chỉ đọc. Mọi đường dẫn là tương đối so với
 * {@code flowtracer.source-root} và dùng dấu '/'.
 */
public interface SourceReader {

    /** Một mục trong thư mục. */
    record Entry(String relativePath, String name, boolean directory, long sizeBytes) {}

    /** Liệt kê thư mục con trực tiếp (dùng cho trình duyệt thư mục). */
    List<Entry> listDirs(String relativeDir);

    /**
     * Duyệt đệ quy mọi file dưới {@code relativeRoot}, sắp xếp ổn định theo đường dẫn.
     *
     * @param accept nhận (đường dẫn tương đối so với relativeRoot, kích thước) → có lấy file hay không
     */
    List<Entry> walkFiles(String relativeRoot, BiPredicate<String, Long> accept);

    byte[] readBytes(String relativePath);

    String readString(String relativePath);

    /** Đọc khoảng dòng [fromLine, toLine] (đánh số từ 1). */
    List<String> readLines(String relativePath, int fromLine, int toLine);

    boolean exists(String relativePath);
}
