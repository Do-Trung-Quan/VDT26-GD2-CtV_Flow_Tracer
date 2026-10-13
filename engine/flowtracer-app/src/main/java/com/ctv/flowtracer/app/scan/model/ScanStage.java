package com.ctv.flowtracer.app.scan.model;

/** Các giai đoạn của một lần quét, theo thứ tự chạy. */
public enum ScanStage {
    DISCOVER("Liệt kê file"),
    DIFF("So sánh thay đổi"),
    EXTRACT("Đọc & trích xuất"),
    RESOLVE("Ghép kết quả"),
    PERSIST("Lưu dữ liệu"),
    LINK("Nối Frontend – Backend"),
    FINALIZE("Hoàn tất");

    private final String label;

    ScanStage(String label) {
        this.label = label;
    }

    /** Tên giai đoạn tiếng Việt cho giao diện. */
    public String label() {
        return label;
    }
}
