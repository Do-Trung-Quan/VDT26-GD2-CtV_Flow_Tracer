package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;

/**
 * Kết quả tìm kiếm nhanh (API C3), nhóm theo loại thành phần; nhóm không có kết quả thì không xuất hiện.
 *
 * @param query từ khóa đã dùng (đã bỏ khoảng trắng thừa)
 * @param total tổng số kết quả khớp trong mọi nhóm
 * @param groups các nhóm, theo thứ tự API, Màn hình, Phần tử, Bảng, Hàm
 */
public record SearchResponse(String query, int total, List<SearchGroupResponse> groups) {}
