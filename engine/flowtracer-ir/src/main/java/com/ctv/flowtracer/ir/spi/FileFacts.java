package com.ctv.flowtracer.ir.spi;

/**
 * Dữ kiện thô trích từ một file ở pha 1. Mỗi adapter định nghĩa kiểu riêng (vd {@code JavaFileFacts}); kiểu
 * đó phải serialize/deserialize được bằng JSON để lưu cache theo mã băm file.
 */
public interface FileFacts {

    String relativePath();

    String contentHash();
}
