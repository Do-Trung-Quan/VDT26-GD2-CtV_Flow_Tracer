package com.ctv.flowtracer.app.scan.dto.response;

import com.ctv.flowtracer.app.scan.model.FileCounts;

/**
 * Số file của một lần quét (chỉ tính file bộ phân tích xử lý, vd {@code .java}).
 *
 * @param total số file hiện có
 * @param added file mới
 * @param modified file đã sửa (mã băm khác)
 * @param removed file đã xóa
 * @param unchanged file không đổi
 * @param extracted file được đọc và trích dữ kiện lại
 * @param fromCache file dùng lại dữ kiện đã lưu
 * @param errors file không phân tích được
 */
public record FileCountsResponse(
        int total,
        int added,
        int modified,
        int removed,
        int unchanged,
        int extracted,
        int fromCache,
        int errors) {

    public static FileCountsResponse from(FileCounts c) {
        return new FileCountsResponse(
                c.total(), c.added(), c.modified(), c.removed(), c.unchanged(), c.extracted(), c.fromCache(), c.errors());
    }
}
