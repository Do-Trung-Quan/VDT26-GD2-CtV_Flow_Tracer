package com.ctv.flowtracer.app.workspace.dto.response;

import java.util.List;

/**
 * Kết quả duyệt một thư mục (API A6).
 *
 * @param path thư mục đang xem, tương đối trong {@code SOURCE_ROOT} ({@code ""} là chính {@code SOURCE_ROOT})
 * @param parentPath thư mục cha; {@code null} khi đang ở {@code SOURCE_ROOT}
 * @param folders các thư mục con, sắp theo tên
 */
public record FolderBrowseResponse(
        String path,
        String parentPath,
        List<FolderEntryResponse> folders) {}
