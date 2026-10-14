package com.ctv.flowtracer.app.ai.model;

import java.util.Map;

/**
 * Kết quả một lần nhờ AI. Chỉ {@link #GENERATED} và {@link #CACHED} có {@code content}; các trạng thái còn lại nghĩa là
 * người gọi phải dùng kết quả tất định và có thể hiện {@code message} cho người dùng.
 *
 * @param status trạng thái
 * @param content nội dung đã kiểm tra; null nếu không có
 * @param message lý do (tiếng Việt) khi không có nội dung AI; null khi có
 * @param provider provider tạo nội dung (cho nhãn "✦ AI")
 * @param model model tạo nội dung
 */
public record AiOutcome(Status status, Map<String, Object> content, String message, String provider, String model) {

    public enum Status {
        /** Vừa gọi AI và đầu ra đạt kiểm tra. */
        GENERATED,
        /** Dùng lại bản đã lưu. */
        CACHED,
        /** Mục đích bị tắt hoặc provider = none. */
        DISABLED,
        /** Provider chưa cấu hình đủ hoặc gọi lỗi. */
        UNAVAILABLE,
        QUOTA_EXCEEDED,
        /** Đầu ra không đạt kiểm tra. */
        REJECTED
    }

    public boolean hasContent() {
        return content != null;
    }

    static AiOutcome none(Status status, String message) {
        return new AiOutcome(status, null, message, null, null);
    }

    public static AiOutcome disabled(String message) {
        return none(Status.DISABLED, message);
    }

    public static AiOutcome unavailable(String message) {
        return none(Status.UNAVAILABLE, message);
    }

    public static AiOutcome quota(String message) {
        return none(Status.QUOTA_EXCEEDED, message);
    }

    public static AiOutcome rejected(String message) {
        return none(Status.REJECTED, message);
    }
}
