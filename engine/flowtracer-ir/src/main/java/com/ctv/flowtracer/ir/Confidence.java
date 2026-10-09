package com.ctv.flowtracer.ir;

/**
 * Mức tin cậy của một edge. AI không bao giờ thay đổi mức tin cậy; liên kết chốt tay lưu riêng ở {@code link_override}
 * và hiển thị là "Chốt tay". Tiêu chí: Nhóm 5 trong feature_list.
 */
public enum Confidence {
    /** Đích xác định duy nhất bằng kiểu dữ liệu hoặc chuỗi hằng khớp đúng một endpoint. */
    CERTAIN(4),
    /** Phải dùng luật / heuristic (URL ghép từ biến, khớp tham số, luật proxy, @Primary…). */
    INFERRED(2),
    /** Không có ứng viên, hoặc nhiều ứng viên có mức khớp gần nhau. */
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
