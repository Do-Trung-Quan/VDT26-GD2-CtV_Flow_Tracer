package com.ctv.flowtracer.app.ai.dto.response;

/**
 * Kết quả kiểm tra kết nối AI (API I3).
 *
 * @param ok kết nối và nhận được câu trả lời
 * @param latencyMs độ trễ (ms); 0 nếu không gọi được
 * @param message thông điệp tiếng Việt, đã loại thông tin bí mật
 */
public record AiTestResponse(boolean ok, int latencyMs, String message) {}
