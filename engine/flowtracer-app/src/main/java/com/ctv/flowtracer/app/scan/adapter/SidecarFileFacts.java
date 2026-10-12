package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.ir.spi.FileFacts;

/**
 * Dữ kiện pha 1 của một file thuộc bộ phân tích chạy trong sidecar. Sidecar tự trích dữ kiện và cache theo mã băm trong
 * thư mục riêng ({@code sidecar-<adapter>-v<phiên bản>}), nên phía Engine chỉ ghi nhận file nào, mã băm nào đã vào lần
 * phân tích — đủ để sidecar biết danh sách file và để Engine so sánh thay đổi.
 *
 * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
 * @param contentHash SHA-256 của nội dung (hex)
 */
public record SidecarFileFacts(String relativePath, String contentHash) implements FileFacts {}
