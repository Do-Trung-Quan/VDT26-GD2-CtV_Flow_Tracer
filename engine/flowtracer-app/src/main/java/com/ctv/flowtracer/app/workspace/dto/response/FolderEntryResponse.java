package com.ctv.flowtracer.app.workspace.dto.response;

/**
 * Một thư mục con trong trình duyệt thư mục (API A6).
 *
 * @param name tên thư mục
 * @param path đường dẫn tương đối trong {@code SOURCE_ROOT}
 * @param gitRepo thư mục có {@code .git} (là một repo Git)
 */
public record FolderEntryResponse(
        String name,
        String path,
        boolean gitRepo) {}
