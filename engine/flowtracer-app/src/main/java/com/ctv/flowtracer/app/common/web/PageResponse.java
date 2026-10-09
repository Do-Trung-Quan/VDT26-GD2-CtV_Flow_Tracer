package com.ctv.flowtracer.app.common.web;

import java.util.List;

/**
 * Kết quả phân trang dùng chung (nằm trong {@code data} của {@link ApiResponse}).
 *
 * @param items các phần tử của trang hiện tại
 * @param total tổng số phần tử
 * @param page số trang (bắt đầu từ 1)
 * @param limit số phần tử tối đa mỗi trang
 * @param <T> kiểu phần tử
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int limit) {}
