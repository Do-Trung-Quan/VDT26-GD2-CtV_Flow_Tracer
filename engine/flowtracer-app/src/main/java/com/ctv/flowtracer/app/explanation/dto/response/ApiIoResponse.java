package com.ctv.flowtracer.app.explanation.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Đầu vào, đầu ra và ràng buộc của MỘT API Backend. Thành phần giao diện gọi nhiều API thì mỗi API một mục.
 *
 * @param apiKey node_key của API
 * @param method phương thức HTTP ({@code GET}, {@code POST}…; {@code ANY} khi API nhận mọi phương thức)
 * @param path đường dẫn đầy đủ của API
 * @param displayName tên thân thiện của API (từ {@code @Operation}); null nếu không có
 * @param input đầu vào theo từng phần có dữ liệu — {@code path}, {@code query}, {@code body}; mỗi trường là một câu ngắn
 *     "kiểu — bắt buộc, tối đa 50 ký tự". Rỗng nếu API không nhận gì
 * @param output cấu trúc kết quả trả về (lớp bọc chung như {@code ApiResponse<T>} được giữ, {@code T} mở thành dữ liệu thật);
 *     rỗng nếu API không trả nội dung
 * @param errors các trường hợp lỗi có thể gặp, bằng lời
 * @param constraints ràng buộc "thô" rút từ code, mỗi ràng buộc một câu: quyền, kiểm tra dữ liệu đầu vào, nhánh báo lỗi, cột không được trùng
 */
public record ApiIoResponse(
        String apiKey,
        String method,
        String path,
        String displayName,
        Map<String, Object> input,
        Map<String, Object> output,
        List<String> errors,
        List<String> constraints) {}
