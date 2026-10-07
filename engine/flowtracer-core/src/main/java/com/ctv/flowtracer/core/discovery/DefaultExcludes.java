package com.ctv.flowtracer.core.discovery;

/** Danh sách thư mục/file luôn bỏ qua khi quét (glob, so với đường dẫn tương đối dùng dấu '/'). */
public final class DefaultExcludes {

    public static final java.util.List<String> GLOBS = java.util.List.of(
            "**/node_modules/**",
            "**/.git/**",
            "**/dist/**",
            "**/target/**",
            "**/build/**",
            "**/.angular/**",
            "**/.idea/**",
            "**/.vscode/**",
            "**/vendor/**",
            "**/.gradle/**",
            "**/*.min.js",
            "**/*.map");

    private DefaultExcludes() {}
}
