package com.ctv.flowtracer.app.scan.pipeline;

/**
 * Một file bộ phân tích sẽ xử lý, đã đọc ở bước DISCOVER.
 *
 * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
 * @param sizeBytes kích thước
 * @param contentHash SHA-256 nội dung (hex)
 * @param content nội dung UTF-8; null nếu không đọc được thành văn bản
 * @param readError lý do không dùng được file (quá lớn, nhị phân, sai bảng mã…); null nếu đọc được
 */
record DiscoveredFile(String relativePath, long sizeBytes, String contentHash, String content, String readError) {}
