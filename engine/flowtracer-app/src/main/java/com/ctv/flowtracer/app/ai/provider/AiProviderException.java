package com.ctv.flowtracer.app.ai.provider;

import java.time.Duration;

/**
 * Lỗi khi gọi provider AI, đã PHÂN LOẠI để cổng AI biết nên nghỉ bao lâu và giao diện nói gì với người dùng (thay vì chung một câu
 * "AI không phản hồi"). Không chứa khóa API hay nội dung prompt.
 */
public class AiProviderException extends RuntimeException {

    /** Loại lỗi. */
    public enum Kind {
        /** 429 / hết hạn mức yêu cầu hoặc token (phút / ngày). */
        QUOTA,
        /** 503 / 502 / 504: dịch vụ quá tải hoặc tạm ngưng, thường tự hết sau vài giây. */
        OVERLOADED,
        /** 401 / 403: khóa API sai hoặc không có quyền dùng model. */
        AUTH,
        /** 400: yêu cầu bị từ chối (thường do sai tên model). */
        BAD_REQUEST,
        /** Provider trả lời quá lâu. */
        TIMEOUT,
        OTHER
    }

    private final Kind kind;
    private final Duration retryAfter;
    private final boolean daily;
    private final Integer quotaLimit;

    /**
     * @param retryAfter thời gian provider yêu cầu chờ; null nếu không nói
     * @param daily true nếu là hạn mức THEO NGÀY (chờ vài phút không đủ)
     */
    public AiProviderException(Kind kind, String message, Duration retryAfter, boolean daily) {
        this(kind, message, retryAfter, daily, null);
    }

    /** @param quotaLimit số lượt tối đa của gói theo ngày / phút mà nhà cung cấp báo (vd gói miễn phí: 20); null nếu không nói */
    public AiProviderException(Kind kind, String message, Duration retryAfter, boolean daily, Integer quotaLimit) {
        super(message);
        this.kind = kind;
        this.retryAfter = retryAfter;
        this.daily = daily;
        this.quotaLimit = quotaLimit;
    }

    public Integer quotaLimit() {
        return quotaLimit;
    }

    public Kind kind() {
        return kind;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public boolean daily() {
        return daily;
    }
}
