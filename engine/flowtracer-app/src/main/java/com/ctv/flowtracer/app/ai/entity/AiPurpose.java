package com.ctv.flowtracer.app.ai.entity;

/**
 * Các mục đích ĐƯỢC PHÉP dùng AI (overview §2.7). Thêm mục đích mới phải được người dùng đồng ý và cập nhật §2.7
 * trước. Khớp enum {@code ai_purpose} trong CSDL. {@link #EDGE_RESOLUTION}: AI chỉ phân tích, giải thích ứng viên khi người
 * dùng bấm "Nhờ AI phân tích" — không bao giờ đổi liên kết.
 */
public enum AiPurpose {
    PURPOSE_TEXT(true),
    SCENARIO_POLISH(true),
    MOCK_DATA(true),
    EDGE_RESOLUTION(true),
    FEATURE_SUMMARY(false),
    LEGACY_MATCH(false);

    private final boolean mvp;

    AiPurpose(boolean mvp) {
        this.mvp = mvp;
    }

    /** true nếu thuộc MVP; false nếu thuộc phần mở rộng. */
    public boolean isMvp() {
        return mvp;
    }
}
