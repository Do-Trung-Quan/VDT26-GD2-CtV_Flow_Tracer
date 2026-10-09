package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;

/**
 * Một endpoint trên cây API Backend.
 *
 * @param nodeKey định danh ổn định của endpoint
 * @param httpMethod GET/POST/…; {@code ANY} khi {@code @RequestMapping} không khai phương thức
 * @param path đường dẫn đầy đủ (đã cộng context-path)
 * @param summary mô tả ngắn (null nếu code không có {@code @Operation} lẫn Javadoc)
 * @param summarySource nguồn mô tả: {@code OPERATION} hoặc {@code JAVADOC} (null nếu không có mô tả)
 * @param deprecated endpoint được đánh dấu ngừng dùng
 * @param filePath file khai báo, tương đối trong Source
 * @param line dòng bắt đầu của hàm xử lý
 * @param warnings cảnh báo có căn cứ (vd đường dẫn chứa hằng số chưa giải được); rỗng nếu không có
 */
public record EndpointNodeResponse(
        String nodeKey,
        String httpMethod,
        String path,
        String summary,
        String summarySource,
        boolean deprecated,
        String filePath,
        Integer line,
        List<String> warnings) {}
