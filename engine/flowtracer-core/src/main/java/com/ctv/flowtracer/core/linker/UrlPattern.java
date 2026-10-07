package com.ctv.flowtracer.core.linker;

import java.util.List;

/** URL đã chuẩn hóa thành các đoạn literal / ký tự đại diện {*}; chấm điểm độ cụ thể. */
public record UrlPattern(String httpMethod, List<Segment> segments) {

    /** Một đoạn path: literal hoặc ký tự đại diện. */
    public record Segment(String value, boolean wildcard) {}

    public static final String WILDCARD = "{*}";

    /** Chuẩn hóa: bỏ query string, gộp '/', biến {@code {id}} / {@code :id} / phần không biết thành {*}. */
    public static UrlPattern parse(String httpMethod, String rawPath) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    /** Điểm độ cụ thể: đoạn literal được cộng nhiều điểm hơn đoạn đại diện. */
    public int specificity() {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
