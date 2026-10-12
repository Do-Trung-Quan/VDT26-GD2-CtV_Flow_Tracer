package com.ctv.flowtracer.app.graph.dto.response;

import com.ctv.flowtracer.app.graph.model.SearchCategory;
import java.util.List;

/**
 * Nhóm kết quả tìm kiếm theo loại thành phần.
 *
 * @param category loại nhóm
 * @param label tên nhóm hiển thị (tiếng Việt)
 * @param total tổng số kết quả khớp của nhóm trong lần quét (có thể nhiều hơn số trả về)
 * @param hits các kết quả, khớp tốt nhất trước
 */
public record SearchGroupResponse(SearchCategory category, String label, int total, List<SearchHitResponse> hits) {}
