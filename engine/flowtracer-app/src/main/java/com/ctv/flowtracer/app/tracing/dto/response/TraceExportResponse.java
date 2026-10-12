package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.app.tracing.model.ExportFormat;

/**
 * Diagram đã xuất (API D2).
 *
 * @param format định dạng đã xuất
 * @param fileName tên file gợi ý khi lưu, vd {@code POST_api_v1_categories.puml}
 * @param mediaType kiểu nội dung của {@code content}
 * @param content mã PlantUML hoặc nội dung SVG
 */
public record TraceExportResponse(ExportFormat format, String fileName, String mediaType, String content) {}
