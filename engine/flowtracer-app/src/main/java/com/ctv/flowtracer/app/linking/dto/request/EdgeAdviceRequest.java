package com.ctv.flowtracer.app.linking.dto.request;

/**
 * Yêu cầu "Nhờ AI phân tích" một liên kết (API E2).
 *
 * @param force true = bỏ qua bản đã lưu và hỏi AI lại; bỏ trống / false = dùng bản đã lưu nếu có
 * @param startKey key thành phần xuất phát của luồng đang xem; có thì Engine đưa chuỗi gọi từ đó tới điểm gọi (tên hàm, chuỗi truyền vào)
 *     cho AI làm bằng chứng
 */
public record EdgeAdviceRequest(Boolean force, String startKey) {}
