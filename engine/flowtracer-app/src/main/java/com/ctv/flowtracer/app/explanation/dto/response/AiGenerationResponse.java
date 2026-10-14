package com.ctv.flowtracer.app.explanation.dto.response;

/**
 * Kết quả một lần sinh nội dung AI (API F4). Nội dung mới được đọc lại bằng F1 / F2 / F3 (hoặc C2 với {@code ELEMENT_NAMING}).
 *
 * @param purpose mục đích đã chạy
 * @param state {@code GENERATED} (vừa gọi AI) hoặc {@code CACHED} (dùng bản đã lưu)
 * @param provider provider đã viết nội dung
 * @param model model đã viết nội dung
 * @param named {@code ELEMENT_NAMING}: số phần tử đã được đặt tên; null với mục đích khác
 * @param total {@code ELEMENT_NAMING}: tổng số phần tử của thành phần chứa; null với mục đích khác
 * @param message ghi chú (vd một phần của màn hình lớn bị AI từ chối); null nếu không có
 */
public record AiGenerationResponse(String purpose, String state, String provider, String model, Integer named, Integer total, String message) {}
