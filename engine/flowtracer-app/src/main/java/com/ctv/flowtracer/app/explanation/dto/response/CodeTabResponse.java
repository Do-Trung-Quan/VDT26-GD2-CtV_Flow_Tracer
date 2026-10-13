package com.ctv.flowtracer.app.explanation.dto.response;

import java.util.List;

/**
 * Một tab của popup "Xem code" (API C5): đoạn mã của một lớp trong luồng.
 *
 * @param title tên tab: "Template", "Component", "Kết nối API", "Controller", "Service", "Repository"…; có nhiều file cùng vai trò thì kèm tên file
 * @param role vai trò của lớp: {@code TEMPLATE}, {@code COMPONENT}, {@code FRONTEND}, {@code HTTP}, {@code CONTROLLER}, {@code SERVICE}, {@code REPOSITORY}, {@code BACKEND}
 * @param filePath đường dẫn file, tương đối trong Source
 * @param sourceName tên Source chứa file
 * @param startLine số dòng của dòng đầu tiên trong {@code code}
 * @param highlightLines các dòng cần tô sáng (số dòng trong file)
 * @param code nội dung đoạn mã (chỉ đọc), các dòng nối bằng {@code \n}
 */
public record CodeTabResponse(
        String title,
        String role,
        String filePath,
        String sourceName,
        int startLine,
        List<Integer> highlightLines,
        String code) {}
