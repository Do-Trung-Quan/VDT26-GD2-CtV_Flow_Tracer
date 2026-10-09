package com.ctv.flowtracer.app.common.web;

/** Biểu thức chính quy dùng chung cho Bean Validation ({@code @Pattern}) của các DTO. */
public final class ValidationPatterns {

    /** Có ít nhất một ký tự khác khoảng trắng. */
    public static final String NOT_BLANK_TEXT = "(?s).*\\S.*";

    /**
     * Đường dẫn tương đối trong {@code SOURCE_ROOT}: không bắt đầu bằng '/' hay '\', không có đoạn {@code ..}, không có
     * ký tự ổ đĩa ({@code :}) hay ký tự đại diện. Chuỗi rỗng là chính {@code SOURCE_ROOT}.
     */
    public static final String RELATIVE_PATH = "^(?![/\\\\])(?!.*(?:^|[/\\\\])\\.\\.(?:[/\\\\]|$))[^:*?\"<>|]*$";

    /** Glob tương đối so với gốc Source (vd {@code generated/**}): như {@link #RELATIVE_PATH} nhưng cho phép {@code *}, {@code ?}. */
    public static final String RELATIVE_GLOB = "^(?![/\\\\])(?!.*(?:^|[/\\\\])\\.\\.(?:[/\\\\]|$))[^:\"<>|]+$";

    private ValidationPatterns() {}
}
