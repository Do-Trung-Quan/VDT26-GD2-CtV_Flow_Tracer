package com.ctv.flowtracer.app.graph.dto.response;

import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;

/**
 * Chi tiết một thành phần (API C4).
 *
 * @param nodeKey key ổn định của thành phần
 * @param kind loại thành phần
 * @param name tên kỹ thuật
 * @param displayName tên thân thiện tiếng Việt (có thể null)
 * @param filePath file khai báo (tương đối trong Source); null nếu không có
 * @param line dòng bắt đầu; null nếu không có
 * @param sourceName tên Source chứa thành phần
 * @param trail chuỗi thành phần chứa nó, từ cao nhất xuống thành phần cha trực tiếp (chỉ thành phần giao diện)
 * @param calledFrom với API: các phần tử / màn hình giao diện mà luồng của chúng tới API này; thành phần khác: rỗng
 * @param calledFromTotal tổng số thành phần gọi tới (có thể lớn hơn số phần tử trả về trong {@code calledFrom})
 * @param callsTo với API: bảng dữ liệu và hệ thống khác nó tới; với phần tử giao diện: API gọi, hộp thoại mở, màn hình chuyển tới
 */
public record NodeDetailResponse(
        String nodeKey,
        NodeKind kind,
        String name,
        String displayName,
        String filePath,
        Integer line,
        String sourceName,
        List<NodeRelationResponse> trail,
        List<NodeRelationResponse> calledFrom,
        int calledFromTotal,
        List<NodeRelationResponse> callsTo) {}
