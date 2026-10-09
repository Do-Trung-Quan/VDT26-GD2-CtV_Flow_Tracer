package com.ctv.flowtracer.app.scan.model;

/**
 * Số file của một lần quét. Chỉ tính file mà bộ phân tích của Source xử lý (vd file {@code .java} với Spring); mọi số
 * đều đếm thật trong lần quét.
 *
 * @param total số file được phân tích hiện có
 * @param added file mới so với lần quét trước
 * @param modified file có mã băm khác lần quét trước
 * @param removed file đã có ở lần quét trước nhưng nay không còn
 * @param unchanged file có mã băm giống lần quét trước
 * @param extracted file được đọc và trích dữ kiện lại ở lần quét này
 * @param fromCache file dùng lại dữ kiện đã lưu (không đọc lại)
 * @param errors file không phân tích được
 */
public record FileCounts(
        int total,
        int added,
        int modified,
        int removed,
        int unchanged,
        int extracted,
        int fromCache,
        int errors) {

    public static FileCounts empty() {
        return new FileCounts(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public FileCounts plus(FileCounts o) {
        return new FileCounts(
                total + o.total,
                added + o.added,
                modified + o.modified,
                removed + o.removed,
                unchanged + o.unchanged,
                extracted + o.extracted,
                fromCache + o.fromCache,
                errors + o.errors);
    }

    /** Có file nào thêm, sửa hoặc xóa không. */
    public boolean hasChanges() {
        return added + modified + removed > 0;
    }
}
