package com.ctv.flowtracer.ir.spi;

import java.util.Objects;

/**
 * Một file thư viện đóng gói sẵn (vd {@code .jar}) nằm trong Source, đã đọc nguyên bytes qua {@code SourceReader}. Adapter chỉ
 * ĐỌC cấu trúc khai báo bên trong (tên lớp, trường, kiểu) — không nạp lớp, không chạy bất cứ mã nào.
 *
 * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
 * @param content nội dung nhị phân của file
 */
public record LibraryArchive(String relativePath, byte[] content) {

    public LibraryArchive {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
    }
}
