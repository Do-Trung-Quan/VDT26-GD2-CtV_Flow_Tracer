package com.ctv.flowtracer.app.common.web;

import java.util.List;

/** Kết quả phân trang dùng chung. */
public record PageResponse<T>(List<T> items, long total, int page, int limit) {}
