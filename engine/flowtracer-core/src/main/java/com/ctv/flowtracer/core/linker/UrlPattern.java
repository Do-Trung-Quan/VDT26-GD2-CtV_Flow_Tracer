package com.ctv.flowtracer.core.linker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Đường dẫn đã chuẩn hóa thành các đoạn: literal, biến ({@code {id}}, {@code :id}, phần không tính được) hoặc đoạn trộn
 * literal với biến ({@code item-{id}}).
 *
 * @param httpMethod phương thức HTTP viết hoa ({@code ANY} khi endpoint nhận mọi phương thức)
 */
public record UrlPattern(String httpMethod, List<Segment> segments) {

    /** Loại một đoạn path. */
    public enum SegmentType {
        LITERAL,
        VARIABLE,
        /** Literal xen biến, vd {@code item-{id}}: so khớp bằng biểu thức chính quy. */
        MIXED
    }

    /** Một đoạn path. */
    public record Segment(String value, SegmentType type) {

        public boolean literal() {
            return type == SegmentType.LITERAL;
        }

        /** Biểu thức chính quy của đoạn (biến khớp mọi chuỗi không chứa '/'). */
        Pattern regex() {
            var out = new StringBuilder();
            int i = 0;
            while (i < value.length()) {
                int open = value.indexOf('{', i);
                int close = open < 0 ? -1 : value.indexOf('}', open);
                if (open < 0 || close < 0) {
                    out.append(Pattern.quote(value.substring(i)));
                    break;
                }
                if (open > i) {
                    out.append(Pattern.quote(value.substring(i, open)));
                }
                out.append("[^/]+");
                i = close + 1;
            }
            return Pattern.compile(out.toString());
        }
    }

    public static final String ANY = "ANY";

    public UrlPattern {
        Objects.requireNonNull(httpMethod, "httpMethod");
        segments = List.copyOf(segments);
    }

    /**
     * Chuẩn hóa: bỏ host của URL tuyệt đối, query string và fragment; gộp '/' liên tiếp; bỏ '/' cuối. Biến {@code {id}},
     * {@code {id:\d+}}, {@code :id}, {@code {?}} và {@code *} thành đoạn biến.
     */
    public static UrlPattern parse(String httpMethod, String rawPath) {
        String method = httpMethod == null || httpMethod.isBlank() ? ANY : httpMethod.strip().toUpperCase(Locale.ROOT);
        String path = rawPath == null ? "" : rawPath.strip();
        path = path.replaceFirst("^(?i)(https?:)?//[^/]*", "");
        path = path.replaceFirst("[?#].*$", "");
        var segments = new ArrayList<Segment>();
        for (String part : path.split("/+")) {
            if (part.isEmpty()) {
                continue;
            }
            segments.add(segment(part));
        }
        return new UrlPattern(method, segments);
    }

    private static Segment segment(String part) {
        if (part.equals("*") || part.equals("**") || part.startsWith(":") && part.length() > 1) {
            return new Segment("{" + part.replace(":", "") + "}", SegmentType.VARIABLE);
        }
        // {id:\d+} của Spring: bỏ phần regex sau dấu ':'
        String cleaned = part.replaceAll("\\{([^}:]*):[^}]*}", "{$1}");
        if (cleaned.matches("\\{[^{}]*}")) {
            return new Segment(cleaned, SegmentType.VARIABLE);
        }
        if (cleaned.contains("{") && cleaned.contains("}")) {
            return new Segment(cleaned, SegmentType.MIXED);
        }
        return new Segment(cleaned, SegmentType.LITERAL);
    }

    /** Đường dẫn dạng chuẩn, vd {@code /api/v1/items/{id}}; rỗng thì {@code /}. */
    public String path() {
        if (segments.isEmpty()) {
            return "/";
        }
        var out = new StringBuilder();
        segments.forEach(s -> out.append('/').append(s.value()));
        return out.toString();
    }

    /** Số đoạn literal (đoạn cố định càng nhiều, đường dẫn càng cụ thể). */
    public int literalCount() {
        return (int) segments.stream().filter(Segment::literal).count();
    }

    public boolean startsWithVariable() {
        return !segments.isEmpty() && segments.get(0).type() == SegmentType.VARIABLE;
    }
}
