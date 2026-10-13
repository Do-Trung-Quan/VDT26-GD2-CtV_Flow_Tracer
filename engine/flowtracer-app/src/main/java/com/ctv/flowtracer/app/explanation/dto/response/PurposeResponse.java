package com.ctv.flowtracer.app.explanation.dto.response;

/**
 * Đoạn "Mục đích" của một thành phần.
 *
 * @param text nội dung
 * @param source nguồn: {@code OPERATION} (Swagger {@code @Operation}), {@code JAVADOC}, {@code SKELETON} (câu khung tất định);
 *     {@code AI} có từ Giai đoạn 7
 * @param sourceLabel tên nguồn để hiển thị: "Swagger", "Javadoc", "Câu khung"
 */
public record PurposeResponse(
        String text,
        String source,
        String sourceLabel) {}
