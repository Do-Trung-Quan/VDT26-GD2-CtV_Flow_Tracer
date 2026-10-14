package com.ctv.flowtracer.app.ai.dto.response;

/**
 * Một mục đích dùng AI trên trang Cấu hình AI.
 *
 * @param purpose mã mục đích
 * @param label tên hiển thị (tiếng Việt)
 * @param description mô tả ngắn việc AI làm
 * @param enabled đang bật hay tắt
 */
public record AiPurposeSettingResponse(String purpose, String label, String description, boolean enabled) {}
