package com.ctv.flowtracer.app.source;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Điểm DUY NHẤT được đọc mã nguồn người dùng (CLAUDE.md §3.4). Chỉ đọc. Mọi đường dẫn là tương đối so với
 * {@code flowtracer.source-root} và dùng dấu '/'.
 */
public interface SourceReader {

    /** Một mục trong thư mục. */
    record Entry(String relativePath, String name, boolean directory, long sizeBytes) {}

    /** Liệt kê thư mục con trực tiếp (dùng cho trình duyệt thư mục). */
    List<Entry> listDirs(String relativeDir);

    /** Liệt kê file nằm ngay trong thư mục (không đệ quy, bỏ qua symlink), sắp xếp theo tên. */
    List<Entry> listFiles(String relativeDir);

    /** Đường dẫn tồn tại và là thư mục (không theo symlink). Đường dẫn ra ngoài source-root thì ném lỗi 400. */
    boolean isDirectory(String relativePath);

    /**
     * Duyệt đệ quy mọi file dưới {@code relativeRoot}, sắp xếp ổn định theo đường dẫn.
     *
     * @param accept nhận (đường dẫn tương đối so với relativeRoot, kích thước) → có lấy file hay không
     */
    List<Entry> walkFiles(String relativeRoot, BiPredicate<String, Long> accept);

    /**
     * Như {@link #walkFiles(String, BiPredicate)} nhưng bỏ qua cả nhánh thư mục khi {@code acceptDirectory} trả false
     * (vd {@code node_modules}) — không duyệt vào bên trong. Không theo symlink.
     *
     * @param acceptDirectory nhận đường dẫn thư mục tương đối so với relativeRoot ({@code ""} là chính relativeRoot)
     */
    List<Entry> walkFiles(String relativeRoot, Predicate<String> acceptDirectory, BiPredicate<String, Long> accept);

    byte[] readBytes(String relativePath);

    String readString(String relativePath);

    /** Đọc khoảng dòng [fromLine, toLine] (đánh số từ 1). */
    List<String> readLines(String relativePath, int fromLine, int toLine);

    boolean exists(String relativePath);
}
