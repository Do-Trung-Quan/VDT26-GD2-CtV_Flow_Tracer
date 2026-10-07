package com.ctv.flowtracer.ir;

/**
 * Mức tin cậy của một edge. AI chỉ được tạo {@link #AI_RESOLVED} bằng cách chọn trong danh sách ứng viên
 * do phân tích tĩnh tìm ra.
 */
public enum Confidence {
    /** Tham chiếu rõ ràng và duy nhất trong code. */
    CERTAIN(4),
    /** Suy ra bằng luật heuristic (URL có ký tự đại diện, truy vấn ghép động…). */
    INFERRED(2),
    /** Có nhiều ứng viên, AI đã chọn một. */
    AI_RESOLVED(1),
    /** Không xác định được. */
    UNRESOLVED(0),
    /** (Mở rộng) Đã xác nhận bằng dữ liệu lúc chạy. */
    RUNTIME_VERIFIED(5);

    private final int rank;

    Confidence(int rank) {
        this.rank = rank;
    }

    /** Thứ hạng để so sánh: số lớn hơn nghĩa là đáng tin hơn. */
    public int rank() {
        return rank;
    }
}
