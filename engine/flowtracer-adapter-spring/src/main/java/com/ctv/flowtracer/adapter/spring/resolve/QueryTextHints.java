package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.regex.Pattern;

/** Nhận ra một chuỗi trông như câu hoặc mệnh đề truy vấn (cùng bộ từ khóa với {@code StringFragmentExtractor}). */
final class QueryTextHints {

    private static final Pattern KEYWORD = Pattern.compile(
            "(?i)\\b(select|from|join|where|insert\\s+into|update|delete|group\\s+by|order\\s+by|having)\\b");

    private QueryTextHints() {}

    static boolean looksLikeQuery(String text) {
        return KEYWORD.matcher(text).find();
    }
}
