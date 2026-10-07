/**
 * Chức năng nhóm 9, phần AI (API I1–I3, I6): CỔNG AI DUY NHẤT — cache, hạn mức, che secret, gọi provider, kiểm tra kết quả, ghi nhật ký. Chỉ package này được dùng Spring AI. Bảng: ai_artifacts, llm_call_logs, app_settings.
 *
 * <p>Cấu trúc chuẩn mỗi chức năng: {@code controller} → {@code service} → {@code repository}, cùng {@code entity} (bảng CSDL) và
 * {@code dto} (dữ liệu vào/ra API). Xem .context/project_structure.md §4.4.
 */
package com.ctv.flowtracer.app.ai;
