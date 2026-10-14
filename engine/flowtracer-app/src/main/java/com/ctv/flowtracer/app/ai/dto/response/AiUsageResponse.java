package com.ctv.flowtracer.app.ai.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê sử dụng AI (API I6). Lượt gọi, token, lỗi, độ trễ lấy từ nhật ký gọi AI (không chứa nội dung prompt).
 *
 * @param from ngày bắt đầu (gồm)
 * @param to ngày kết thúc (gồm)
 * @param calls tổng lượt gọi provider
 * @param inputTokens tổng token vào
 * @param outputTokens tổng token ra
 * @param errors tổng lượt lỗi / bị loại / hết hạn mức
 * @param avgLatencyMs độ trễ trung bình của các lượt thành công (ms)
 * @param tokensToday token đã dùng hôm nay
 * @param dailyTokenLimit hạn mức token mỗi ngày
 * @param byPurpose số liệu theo từng mục đích
 */
public record AiUsageResponse(
        LocalDate from,
        LocalDate to,
        long calls,
        long inputTokens,
        long outputTokens,
        long errors,
        long avgLatencyMs,
        long tokensToday,
        long dailyTokenLimit,
        List<ByPurpose> byPurpose) {

    /**
     * Số liệu của một mục đích.
     *
     * @param purpose mã mục đích
     * @param label tên hiển thị
     * @param calls lượt gọi provider
     * @param tokens token vào + ra
     * @param errors lượt lỗi / bị loại / hết hạn mức
     */
    public record ByPurpose(String purpose, String label, long calls, long tokens, long errors) {}
}
