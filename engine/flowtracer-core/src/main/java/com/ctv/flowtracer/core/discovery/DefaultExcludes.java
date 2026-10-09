package com.ctv.flowtracer.core.discovery;

import java.util.List;

/** Danh sách thư mục/file luôn bỏ qua khi quét (glob, so với đường dẫn tương đối dùng dấu '/'). */
public final class DefaultExcludes {

    /** Tên thư mục build/thư viện luôn bỏ qua ở mọi cấp (thư mục bắt đầu bằng '.' cũng bị bỏ qua khi nhận diện). */
    public static final List<String> SKIPPED_DIR_NAMES = List.of("node_modules", "dist", "target", "build", "vendor");

    public static final List<String> GLOBS = List.of(
            "**/node_modules/**",
            "**/.git/**",
            "**/dist/**",
            "**/target/**",
            "**/build/**",
            "**/.angular/**",
            "**/.idea/**",
            "**/.vscode/**",
            "**/vendor/**",
            "docs/**",
            "**/.gradle/**",
            "**/*.min.js",
            "**/*.map");

    private DefaultExcludes() {}

    /** Thư mục có bị bỏ qua khi duyệt tìm gốc dự án không (theo tên, ở mọi cấp). */
    public static boolean isSkippedDir(String name) {
        return name.startsWith(".") || SKIPPED_DIR_NAMES.contains(name);
    }
}
