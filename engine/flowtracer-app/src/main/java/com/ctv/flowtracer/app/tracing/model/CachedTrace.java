package com.ctv.flowtracer.app.tracing.model;

import com.ctv.flowtracer.ir.trace.Trace;

/**
 * Một kết quả dò vết đã tính, giữ trong bộ nhớ theo {@code trace_id}.
 *
 * @param trace kết quả dò vết
 * @param startName tên kỹ thuật của thành phần xuất phát
 * @param startDisplayName mô tả ngắn của thành phần xuất phát; null nếu không có
 * @param maxDepth độ sâu đã dùng
 */
public record CachedTrace(Trace trace, String startName, String startDisplayName, int maxDepth) {}
